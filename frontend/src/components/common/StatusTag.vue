<template>
  <el-tag :type="tagType" size="small">
    {{ value }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  value: {
    type: String,
    required: true
  }
});

const tagType = computed(() => {
  const v = props.value || '';
  if (v.includes('正常') || v.includes('启用') || v.includes('有效') || v.includes('迁入')) {
    return 'success';
  }
  if (v.includes('禁用') || v.includes('无效') || v.includes('三级')) {
    return 'info';
  }
  if (v.includes('死亡') || v.includes('迁出') || v.includes('过期') || v.includes('一级') || v.includes('注销')) {
    return 'danger';
  }
  if (v.includes('二级') || v.includes('即将到期')) {
    return 'warning';
  }
  return 'primary';
});
</script>
