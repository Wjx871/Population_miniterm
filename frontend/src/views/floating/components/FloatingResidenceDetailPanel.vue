<template>
  <div>
    <!-- 流动登记专业详情 -->
    <el-card v-if="mode === 'floating' && detail" shadow="never" class="professional-card">
      <template #header><div class="card-header"><span>流动登记专业信息</span><StatusTag v-if="detail.status" :value="detail.status" kind="floating" /></div></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="人员">{{ detail.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号"><SensitiveText :value="detail.identityNo" kind="idCard" /></el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ formatPhone(detail.phone || detail.applicantPhone) }}</el-descriptions-item>
        <el-descriptions-item label="来源区划">{{ detail.sourceRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源地址" :span="2">{{ detail.sourceAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前区划">{{ detail.currentRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前地址">{{ detail.currentAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="居住事由">{{ RESIDENCE_REASON[detail.residenceReasonCode] || detail.residenceReasonCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="居住证明类型">{{ RESIDENCE_PROOF_TYPE[detail.residenceProofType] || detail.residenceProofType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="到达日期">{{ detail.arrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划离开日期">{{ detail.plannedLeaveDate || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 居住证专业详情 -->
    <el-card v-if="mode === 'permit' && detail" shadow="never" class="professional-card">
      <template #header><div class="card-header"><span>居住证专业信息</span><el-tag size="small">{{ PERMIT_APPLY_TYPE[applyType] || applyType }}</el-tag></div></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请类型">{{ PERMIT_APPLY_TYPE[applyType] || applyType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人员">{{ merged.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号"><SensitiveText :value="merged.identityNo" kind="idCard" /></el-descriptions-item>
        <el-descriptions-item label="当前区域">{{ merged.currentRegionCode || merged.issueRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前地址" :span="2">{{ merged.currentAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="applyType === 'FIRST_ISSUE'" label="居住依据">{{ RESIDENCE_BASIS[merged.residenceBasisCode] || merged.residenceBasisCode || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="applyType === 'FIRST_ISSUE'" label="关联登记编号">{{ merged.registrationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="(applyType === 'ENDORSEMENT' || applyType === 'CANCELLATION') && merged.permitNo" label="当前证件编号"><SensitiveText :value="merged.permitNo" kind="text" /></el-descriptions-item>
        <el-descriptions-item v-if="merged.requestedValidFrom || merged.requestedValidUntil" label="申请有效期">
          {{ merged.requestedValidFrom || '-' }} ~ {{ merged.requestedValidUntil || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '../../../components/common/StatusTag.vue'
import SensitiveText from '../../../components/common/SensitiveText.vue'
import { RESIDENCE_REASON, RESIDENCE_PROOF_TYPE, PERMIT_APPLY_TYPE, RESIDENCE_BASIS } from '../../../constants/floatingResidence'
import { maskPhone } from '../../../utils/mask'
import { mergeSubjectAndProfessional } from '../../../utils/professionalApplication'

const props = defineProps({
  mode: { type: String, required: true, validator: (v) => ['floating', 'permit'].includes(v) },
  detail: { type: Object, default: null },
  subject: { type: Object, default: null }
})

const merged = computed(() => mergeSubjectAndProfessional(props.subject, props.detail))

const applyType = computed(() => {
  return merged.value?.applyType || props.detail?.applyType
})

function formatPhone(phone) {
  if (!phone) return '-'
  return maskPhone(phone)
}
</script>

<style scoped>
.professional-card{margin-top:16px}
.card-header{display:flex;align-items:center;justify-content:space-between;gap:12px}
</style>
