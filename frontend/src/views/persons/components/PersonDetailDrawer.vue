<template>
  <DetailDrawer
    :model-value="modelValue"
    :title="drawerTitle"
    :loading="loading"
    :width="780"
    @update:model-value="emit('update:modelValue', $event)"
    @close="handleClose"
  >
    <template v-if="person">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ person.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ person.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">
          <SensitiveText :value="person.idCard" kind="idCard" :revealable="true" />
        </el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ formatDate(person.birthDate) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="民族">{{ person.ethnicity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">
          <SensitiveText :value="person.phone" kind="phone" :revealable="true" />
        </el-descriptions-item>
        <el-descriptions-item label="档案状态">
          <StatusTag :value="person.status" />
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(person.createdAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(person.updatedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="现居住地址" :span="2">
          {{ person.currentAddress || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">关联信息</el-divider>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="暂无可用关联信息"
        description="当前户籍与家庭关系需后端关联接口支持后接入，本阶段不展示伪造数据。"
      />
    </template>
    <el-empty v-else-if="!loading" description="暂无人口详情" />
  </DetailDrawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import DetailDrawer from '../../../components/common/DetailDrawer.vue'
import SensitiveText from '../../../components/common/SensitiveText.vue'
import StatusTag from '../../../components/common/StatusTag.vue'
import { getPersonById } from '../../../api/persons'
import { normalizePerson } from '../../../adapters/person'
import { formatDate, formatDateTime } from '../../../utils/date'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  personId: {
    type: [Number, String],
    default: null,
  },
})

const emit = defineEmits(['update:modelValue', 'close'])

const loading = ref(false)
const person = ref(null)

const drawerTitle = computed(() => {
  if (person.value?.name) return `人口详情 · ${person.value.name}`
  return '人口详情'
})

async function loadDetail() {
  if (!props.personId) {
    person.value = null
    return
  }
  loading.value = true
  try {
    const res = await getPersonById(props.personId)
    person.value = normalizePerson(res)
  } catch (error) {
    console.error(error)
    person.value = null
  } finally {
    loading.value = false
  }
}

function handleClose() {
  person.value = null
  emit('close')
}

watch(
  () => [props.modelValue, props.personId],
  ([visible]) => {
    if (visible) {
      loadDetail()
    } else {
      person.value = null
    }
  }
)
</script>
