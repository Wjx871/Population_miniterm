<template>
  <div class="action-bar">
    <el-button v-if="application?.status === 'DRAFT' && canContinueSpecialized && specializedEditRoute" type="primary" @click="$emit('continue-specialized')">继续编辑并提交</el-button>
    <el-button v-else-if="application?.status === 'DRAFT' && canSubmit && canSubmitDirect" type="primary" :loading="loading" @click="$emit('submit')">提交申请</el-button>
    <el-button v-if="application?.status === 'DRAFT' && canCancel" type="danger" plain :loading="loading" @click="$emit('cancel')">取消草稿</el-button>
    <el-button v-if="canWithdraw" type="warning" plain :loading="loading" @click="$emit('withdraw')">撤回申请</el-button>
    <span v-if="application?.status === 'APPROVED'" class="approved-tip">审批已通过，等待具备执行权限的管理员处理。</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../../stores/user'
import { PERMISSIONS } from '../../constants/permissions'

const props = defineProps({
  application: { type: Object, default: null },
  loading: Boolean,
  /** 专业业务草稿的编辑路由 */
  specializedEditRoute: { type: Object, default: null },
  /** 是否允许继续编辑专业草稿（须同时检查 DRAFT + application:edit + 对应专业权限） */
  canContinueSpecialized: { type: Boolean, default: false }
})

defineEmits(['submit', 'withdraw', 'cancel', 'continue-specialized'])

const userStore = useUserStore()
const canSubmit = computed(() => userStore.hasPermission(PERMISSIONS.APPLICATION_SUBMIT))
const canSubmitDirect = computed(() => !props.specializedEditRoute)
const canCancel = computed(() => userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT))
const canWithdraw = computed(() => ['SUBMITTED', 'UNDER_REVIEW'].includes(props.application?.status) && userStore.hasPermission(PERMISSIONS.APPLICATION_WITHDRAW))
</script>

<style scoped>
.action-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.approved-tip { color: var(--el-color-warning); font-size: 14px; }
</style>
