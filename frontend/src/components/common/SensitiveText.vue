<template>
  <span class="sensitive-text">
    <span class="sensitive-value">{{ displayText }}</span>
    <el-button
      v-if="canReveal"
      link
      type="primary"
      size="small"
      class="reveal-btn"
      @click="revealed = !revealed"
    >
      {{ revealed ? '隐藏' : '查看' }}
    </el-button>
  </span>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useUserStore } from '../../stores/user'
import { checkPermission } from '../../utils/permission'
import { PERMISSIONS } from '../../constants/permissions'
import { maskIdCard, maskPhone, maskText } from '../../utils/mask'

const props = defineProps({
  value: {
    type: [String, Number],
    default: '',
  },
  kind: {
    type: String,
    default: 'text',
    validator: (v) => ['idCard', 'phone', 'text'].includes(v),
  },
  /** 列表场景必须 false；详情可按权限临时查看 */
  revealable: {
    type: Boolean,
    default: false,
  },
  permission: {
    type: String,
    default: PERMISSIONS.PERSON_SENSITIVE_VIEW,
  },
  copyable: {
    type: Boolean,
    default: false,
  },
})

const userStore = useUserStore()
const revealed = ref(false)

const hasPermission = computed(() =>
  checkPermission(userStore.permissions, props.permission)
)

const canReveal = computed(() => props.revealable && hasPermission.value)

const maskedText = computed(() => {
  if (props.kind === 'idCard') return maskIdCard(props.value)
  if (props.kind === 'phone') return maskPhone(props.value)
  return maskText(props.value)
})

const displayText = computed(() => {
  if (canReveal.value && revealed.value) {
    const text = props.value === null || props.value === undefined || props.value === ''
      ? '-'
      : String(props.value)
    return text
  }
  return maskedText.value
})

watch(
  () => props.value,
  () => {
    revealed.value = false
  }
)

onBeforeUnmount(() => {
  revealed.value = false
})
</script>

<style scoped>
.sensitive-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.sensitive-value {
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}

.reveal-btn {
  flex-shrink: 0;
  padding: 0 2px;
}
</style>
