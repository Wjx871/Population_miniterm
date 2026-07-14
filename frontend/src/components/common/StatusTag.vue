<template>
  <el-tag :type="tagType" size="small">
    {{ label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue';
import { APPLICATION_STATUS, APPROVAL_STATUS } from '../../constants/application';
import { MATERIAL_VERIFY_STATUS } from '../../constants/material';
import { FLOATING_STATUS, RESIDENCE_PERMIT_STATUS } from '../../constants/floatingResidence';

const props = defineProps({
  value: {
    type: String,
    required: true
  },
  kind: {
    type: String,
    default: 'application',
    validator: (value) => ['application', 'migration', 'approval', 'material', 'floating', 'residencePermit'].includes(value),
  },
});

const EXTRA_STATUS_LABEL = Object.freeze({
  PENDING_CANCELLATION: '待注销',
  ARCHIVED: '已归档',
  RELEASED: '已解除',
  FAILED: '失败',
  EXPIRED: '已过期',
  PROCESSING: '处理中',
  ENABLED: '启用',
  DISABLED: '停用',
})

const tagType = computed(() => {
  const v = props.value || '';
  if (['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'PENDING', 'PROCESSING', 'PENDING_CANCELLATION'].includes(v)) return 'primary';
  if (['APPROVED', 'VERIFIED', 'COMPLETED', 'ACTIVE', 'ENABLED'].includes(v)) return 'success';
  if (['REJECTED', 'FAILED'].includes(v)) return 'danger';
  if (['WITHDRAWN', 'CANCELLED', 'LEFT', 'DISABLED', 'RELEASED', 'ARCHIVED'].includes(v)) return 'info';
  if (['EXPIRED'].includes(v)) return 'warning';
  if (v.includes('正常') || v.includes('启用') || v.includes('有效') || v.includes('迁入')) {
    return 'success';
  }
  if (v.includes('禁用') || v.includes('无效') || v.includes('三级') || v.includes('解除')) {
    return 'info';
  }
  if (v.includes('死亡') || v.includes('迁出') || v.includes('过期') || v.includes('一级') || v.includes('注销') || v.includes('失败')) {
    return 'danger';
  }
  if (v.includes('二级') || v.includes('即将到期')) {
    return 'warning';
  }
  return 'primary';
});

const label = computed(() => {
  // 旧 kind 优先，保持流动/居住证等既有中文映射
  const kindLabel = (props.kind === 'approval' ? APPROVAL_STATUS[props.value] : null)
    || (props.kind === 'material' ? MATERIAL_VERIFY_STATUS[props.value] : null)
    || (props.kind === 'floating' ? FLOATING_STATUS[props.value] : null)
    || (props.kind === 'residencePermit' ? RESIDENCE_PERMIT_STATUS[props.value] : null)
    || APPLICATION_STATUS[props.value]
  if (kindLabel) return kindLabel
  if (props.value === 'ACTIVE') return '有效'
  if (EXTRA_STATUS_LABEL[props.value]) return EXTRA_STATUS_LABEL[props.value]
  return props.value || '-'
});
</script>
