<template><BaseEChart :option="namedCountOption(rows, 'pie')" label="居住证状态分布图" /></template>
<script setup>import BaseEChart from './BaseEChart.vue'; import { namedCountOption } from '../../../utils/dashboardChart'; defineProps({ rows:{type:Array,default:()=>[]} })</script>
