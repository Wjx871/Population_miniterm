<template>
  <el-dialog 
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="title" 
    :width="width"
    destroy-on-close
  >
    <div v-loading="loading">
      <slot></slot>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="$emit('update:visible', false)" size="large">取消</el-button>
        <el-button type="primary" :loading="loading" @click="$emit('confirm')" size="large">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '提示'
  },
  width: {
    type: String,
    default: '600px'
  },
  loading: {
    type: Boolean,
    default: false
  }
});

defineEmits(['update:visible', 'confirm']);
</script>
