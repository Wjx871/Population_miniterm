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
      <el-skeleton v-if="relationLoading" :rows="3" animated />
      <el-alert v-else-if="relationError" type="error" :closable="false" show-icon title="关联信息加载失败"><el-button link type="primary" @click="loadRelation">重试</el-button></el-alert>
      <el-descriptions v-else-if="relation?.currentResidence || relation?.currentHousehold" :column="2" border>
        <el-descriptions-item label="有效户籍">{{ relation.currentResidence ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="户籍编号">{{ relation.currentResidence?.residenceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="家庭户编号">{{ relation.currentHousehold?.householdNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户主姓名">{{ relation.currentHousehold?.headPersonName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="与户主关系">{{ relation.currentHousehold?.relationship || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户籍状态">{{ relation.currentResidence?.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="家庭地址" :span="2">{{ relation.currentHousehold?.address || relation.currentResidence?.registeredAddress || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无有效户籍或家庭关系" />
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
import { getComprehensivePersonProfile } from '../../../api/query'
import { normalizeComprehensiveProfile } from '../../../adapters/comprehensiveQuery'

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
const relation = ref(null)
const relationLoading = ref(false)
const relationError = ref(false)

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
    await loadRelation()
  } catch (error) {
    console.error(error)
    person.value = null
  } finally {
    loading.value = false
  }
}

async function loadRelation() {
  if (!props.personId) return
  relationLoading.value = true
  relationError.value = false
  try { relation.value = normalizeComprehensiveProfile(await getComprehensivePersonProfile(props.personId)) }
  catch { relation.value = null; relationError.value = true }
  finally { relationLoading.value = false }
}

function handleClose() {
  person.value = null
  relation.value = null
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
