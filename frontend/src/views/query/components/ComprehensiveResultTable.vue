<template>
  <el-table :data="records" v-loading="loading" row-key="personId" border stripe>
    <el-table-column prop="name" label="姓名" width="96" fixed />
    <el-table-column prop="maskedIdentityNo" label="身份证号（脱敏）" width="168" />
    <el-table-column prop="personStatus" label="人员状态" width="108"><template #default="{ row }"><StatusTag :value="row.personStatus" /></template></el-table-column>
    <el-table-column prop="householdNo" label="户籍编号" min-width="128" show-overflow-tooltip />
    <el-table-column label="当前区划" min-width="128" show-overflow-tooltip><template #default="{ row }">{{ row.currentRegionName || row.currentRegionCode || '—' }}</template></el-table-column>
    <el-table-column prop="residenceStatus" label="户籍状态" width="108"><template #default="{ row }"><StatusTag :value="row.residenceStatus" /></template></el-table-column>
    <el-table-column prop="floatingStatus" label="流动登记" width="108"><template #default="{ row }"><StatusTag :value="row.floatingStatus || '暂无'" kind="floating" /></template></el-table-column>
    <el-table-column prop="permitStatus" label="居住证" width="108"><template #default="{ row }"><StatusTag :value="row.permitStatus || '暂无'" kind="residencePermit" /></template></el-table-column>
    <el-table-column prop="permitValidUntil" label="居住证有效期" width="120"><template #default="{ row }">{{ formatDate(row.permitValidUntil) || '—' }}</template></el-table-column>
    <el-table-column prop="lastMigrationDate" label="最后迁移" width="116"><template #default="{ row }">{{ formatDate(row.lastMigrationDate) || '—' }}</template></el-table-column>
    <el-table-column label="操作" width="116" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="$emit('profile', row)">综合档案</el-button></template></el-table-column>
  </el-table>
</template>
<script setup>
import StatusTag from '../../../components/common/StatusTag.vue'
import { formatDate } from '../../../utils/date'
defineProps({ records: { type: Array, default: () => [] }, loading: Boolean })
defineEmits(['profile'])
</script>
