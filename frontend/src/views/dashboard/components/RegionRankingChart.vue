<template><BaseEChart :option="regionRankingOption(rows)" label="行政区划户籍人口排行图" /></template>
<script setup>import BaseEChart from './BaseEChart.vue'; import { regionRankingOption } from '../../../utils/dashboardChart'; defineProps({ rows:{type:Array,default:()=>[]} })</script>
