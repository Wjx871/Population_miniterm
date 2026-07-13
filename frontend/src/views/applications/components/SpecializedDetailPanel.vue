<template><el-card v-if="detail" shadow="never"><template #header>专业业务信息</template><el-descriptions :column="2" border>
  <el-descriptions-item v-for="item in items" :key="item.label" :label="item.label">{{ item.value }}</el-descriptions-item>
</el-descriptions></el-card></template>
<script setup>
import { computed } from 'vue'
const props=defineProps({businessType:{type:String,default:''},detail:{type:Object,default:null}})
const text=(v)=>v===null||v===undefined||v===''?'-':String(v)
const items=computed(()=>{const d=props.detail||{};if(props.businessType.includes('CANCELLATION')&&!props.businessType.startsWith('RESIDENCE')){const x=d.cancellation||{};return[{label:'注销编号',value:text(x.cancellationNo)},{label:'对象类型',value:text(x.cancelObjectType)},{label:'注销原因',value:text(x.cancelReasonCode)},{label:'事件日期',value:text(x.eventDate)},{label:'业务状态',value:text(x.businessStatus)},{label:'执行限制',value:text(d.executionRestriction)}]}
if(props.businessType.startsWith('KEY_POPULATION')){const x=d.application||{};return[{label:'操作类型',value:text(x.operationType)},{label:'人员 ID',value:text(x.personId)},{label:'重点类型',value:text(x.populationType)},{label:'关注等级',value:text(x.attentionLevel)},{label:'业务状态',value:text(x.businessStatus)},{label:'业务日期',value:text(x.eventDate)}]}
const x=d.professional||{};return[{label:'导出模块',value:text(x.exportModule)},{label:'导出范围',value:text(x.exportScope)},{label:'申请字段',value:text(x.requestedFields)},{label:'预计行数',value:text(x.expectedRowLimit)},{label:'业务状态',value:text(x.businessStatus)},{label:'导出理由',value:text(x.reason)}]})
</script>
