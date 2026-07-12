<template><BaseEChart :option="migrationTrendOption(points)" label="迁入迁出趋势图" /></template>
<script setup>import BaseEChart from './BaseEChart.vue'; import { migrationTrendOption } from '../../../utils/dashboardChart'; defineProps({ points:{type:Array,default:()=>[]} })</script>
