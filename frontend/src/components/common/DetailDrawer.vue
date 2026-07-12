<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    :size="drawerSize"
    :destroy-on-close="destroyOnClose"
    direction="rtl"
    class="detail-drawer"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('close')"
  >
    <div v-loading="loading" class="detail-drawer__body">
      <slot />
    </div>
    <template v-if="$slots.footer" #footer>
      <div class="detail-drawer__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '详情',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  width: {
    type: [String, Number],
    default: 760,
  },
  destroyOnClose: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:modelValue', 'close'])

const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)

const drawerSize = computed(() => {
  if (viewportWidth.value < 768) return '100%'
  if (viewportWidth.value < 1024) return '90vw'
  if (typeof props.width === 'number') return `${props.width}px`
  return props.width
})

const onResize = () => {
  viewportWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.detail-drawer__body {
  min-height: 120px;
}

.detail-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
