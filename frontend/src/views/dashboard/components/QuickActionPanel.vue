<template><el-card shadow="never" class="panel"><template #header><span>常用入口</span></template><div class="actions"><el-button v-for="item in actions" :key="item.to" plain @click="$router.push(item.to)"><el-icon><component :is="item.icon"/></el-icon>{{ item.label }}</el-button></div></el-card></template>
<script setup>defineProps({actions:{type:Array,default:()=>[]}})</script>
<style scoped>.panel{border-radius:var(--radius-large)}.actions{display:flex;gap:10px;flex-wrap:wrap}</style>
