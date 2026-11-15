<script setup>
import { BRAND } from '../constants/brand.js'

defineProps({ size: { type: Number, default: 40 } })
</script>

<template>
  <img class="brand-mark" src="/brand/yixun-mark.svg" :alt="`${BRAND.name} ${BRAND.latin} 标志`" :width="size" :height="size" />
</template>

<style scoped>
.brand-mark { display: inline-block; flex-shrink: 0; vertical-align: middle; }
</style>
