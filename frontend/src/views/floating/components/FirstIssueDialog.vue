<template>
  <el-dialog
    :model-value="modelValue"
    title="新增居住证"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="handleClosed"
  >
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        居住证必须基于一条有效流动登记（status = ACTIVE 且已达到资格日期）。系统不允许脱离流动登记直接发放居住证。
      </template>
    </el-alert>

    <el-form label-width="100px">
      <el-form-item label="选择流动登记" required>
        <FloatingSelect v-model="selectedFloatingId" :status="'ACTIVE'" :available-for-first-issue="true" @select="onFloatingSelect" />
      </el-form-item>
      <el-form-item v-if="floatingInfo" label="持证人预览">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="登记编号">{{ floatingInfo.registrationNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ floatingInfo.personName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前区划">{{ floatingInfo.currentRegionCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到达日期">{{ floatingInfo.arrivalDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="资格日期">{{ floatingInfo.eligibleFromDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前地址" :span="2">{{ floatingInfo.currentAddress || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-form-item>
      <el-alert v-if="floatingInfo && !eligible" type="warning" :closable="false" show-icon style="margin-top: 4px">
        <template #title>当前登记未达到申领资格日期（{{ floatingInfo.eligibleFromDate || '待定' }}），进入下一步也可继续，但后端将阻断签发。</template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="handleClosed(false)">取消</el-button>
      <el-button type="primary" :disabled="!selectedFloatingId" @click="handleConfirm">下一步：填写申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FloatingSelect from '../../../components/business/FloatingSelect.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const selectedFloatingId = ref(null)
const floatingInfo = ref(null)

const eligible = computed(() => {
  if (!floatingInfo.value?.eligibleFromDate) return true
  const today = new Date().toISOString().slice(0, 10)
  return today >= String(floatingInfo.value.eligibleFromDate)
})

function onFloatingSelect(item) {
  floatingInfo.value = item || null
}

function handleConfirm() {
  if (!selectedFloatingId.value) return
  emit('confirm', {
    floatingId: selectedFloatingId.value,
    floatingInfo: floatingInfo.value
  })
}

function handleClosed(val) {
  emit('update:modelValue', val)
  if (!val) {
    selectedFloatingId.value = null
    floatingInfo.value = null
  }
}

watch(() => props.modelValue, (val) => {
  if (val) {
    selectedFloatingId.value = null
    floatingInfo.value = null
  }
})
</script>
