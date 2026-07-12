<template><BaseEChart :option="namedCountOption(rows)" label="当前业务规模图" /></template>
<script setup>import BaseEChart from './BaseEChart.vue'; import { namedCountOption } from '../../../utils/dashboardChart'; defineProps({ rows:{type:Array,default:()=>[]} })</script>
