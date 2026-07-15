<template>
  <div class="action-bar">
    <el-button v-if="application?.status === 'DRAFT' && canContinueSpecialized && specializedEditRoute" type="primary" @click="$emit('continue-specialized')">继续编辑并提交</el-button>
    <el-button v-else-if="(application?.status === 'DRAFT' || application?.status === 'RETURNED') && allowSubmit && canSubmitDirect" type="primary" :loading="loading" @click="$emit('submit')">{{ application?.status === 'RETURNED' ? '重新提交申请' : '提交申请' }}</el-button>
    <el-button v-if="application?.status === 'DRAFT' && canCancel" type="danger" plain :loading="loading" @click="$emit('cancel')">取消草稿</el-button>
    <el-button v-if="canWithdraw" type="warning" plain :loading="loading" @click="$emit('withdraw')">{{ application?.status === 'RETURNED' ? '放弃并撤回' : '撤回申请' }}</el-button>
    <el-button v-if="canReturn" type="warning" plain :loading="loading" @click="$emit('return-application')">退回申请</el-button>
    <span v-if="application?.status === 'APPROVED'" class="approved-tip">审批已通过，等待具备执行权限的管理员处理。</span>
    <span v-if="application?.status === 'RETURNED'" class="returned-tip">申请已被退回，等待申请人补充后重新提交或直接撤回。</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../../stores/user'
import { PERMISSIONS } from '../../constants/permissions'

const props = defineProps({
  application: { type: Object, default: null },
  loading: Boolean,
  /** 是否允许提交：由页面根据 Handler.getSubmitPermissions 汇总后传入 */
  canSubmit: { type: Boolean, default: undefined },
  /** 专业业务草稿的编辑路由 */
  specializedEditRoute: { type: Object, default: null },
  /** 是否允许继续编辑专业草稿（须同时检查 DRAFT + application:edit + 对应专业权限） */
  canContinueSpecialized: { type: Boolean, default: false }
})

defineEmits(['submit', 'withdraw', 'cancel', 'continue-specialized', 'return-application'])

const userStore = useUserStore()
// 优先使用页面传入的专业权限组合；未传时回退 application:submit（兼容旧调用）
const allowSubmit = computed(() => {
  if (typeof props.canSubmit === 'boolean') return props.canSubmit
  return userStore.hasPermission(PERMISSIONS.APPLICATION_SUBMIT)
})
const canSubmitDirect = computed(() => !props.specializedEditRoute)
const canCancel = computed(() => userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT))
// SUBMITTED / UNDER_REVIEW 撤回为审批阶段的撤回；RETURNED 状态由申请人选择放弃。
const canWithdraw = computed(() =>
  ['SUBMITTED', 'UNDER_REVIEW', 'RETURNED'].includes(props.application?.status)
  && userStore.hasPermission(PERMISSIONS.APPLICATION_WITHDRAW)
)
// 执行人 / 复核岗可对已批准且待执行的申请发起退回；审批人不持有该权限。
const canReturn = computed(() =>
  props.application?.status === 'APPROVED'
  && userStore.hasPermission(PERMISSIONS.APPLICATION_RETURN)
)
</script>

<style scoped>
.action-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.approved-tip { color: var(--el-color-warning); font-size: 14px; }
.returned-tip { color: var(--el-color-warning); font-size: 14px; }
</style>
