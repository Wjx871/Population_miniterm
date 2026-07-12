<template><div ref="root" class="chart-root" role="img" :aria-label="label" /></template>
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { initEChart } from '../../../charts/echarts'

const props = defineProps({ option: { type: Object, required: true }, label: { type: String, default: '统计图表' } })
const root = ref(null)
let chart = null
let observer = null
const render = () => chart?.setOption(props.option, true)
onMounted(() => { chart = initEChart(root.value); render(); observer = new ResizeObserver(() => chart?.resize()); observer.observe(root.value) })
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose(); chart = null })
</script>
<style scoped>.chart-root{width:100%;height:100%;min-height:220px}</style>
