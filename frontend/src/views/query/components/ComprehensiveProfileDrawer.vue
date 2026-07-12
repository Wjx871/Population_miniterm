<template>
  <DetailDrawer :visible="visible" title="人口综合档案" width="760px" @update:visible="$emit('update:visible', $event)">
    <div v-if="loading" class="drawer-loading"><el-skeleton :rows="8" animated /></div>
    <template v-else-if="profile">
      <section class="profile-section"><h3>人口基本信息</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="姓名">{{ profile.person.name || '—' }}</el-descriptions-item><el-descriptions-item label="人员状态">{{ profile.person.personStatus || '—' }}</el-descriptions-item><el-descriptions-item label="身份证号">{{ profile.person.maskedIdentityNo || '—' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ profile.person.maskedPhone || '—' }}</el-descriptions-item></el-descriptions></section>
      <section v-if="profile.currentHousehold" class="profile-section"><h3>当前家庭户</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="户号">{{ profile.currentHousehold.householdNo || '—' }}</el-descriptions-item><el-descriptions-item label="户主">{{ profile.currentHousehold.headPersonName || '—' }}</el-descriptions-item><el-descriptions-item label="关系">{{ profile.currentHousehold.relationship || '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ profile.currentHousehold.status || '—' }}</el-descriptions-item></el-descriptions></section>
      <section v-if="profile.currentResidence" class="profile-section"><h3>当前户籍登记</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="区划">{{ profile.currentResidence.regionCode || '—' }}</el-descriptions-item><el-descriptions-item label="登记类型">{{ profile.currentResidence.registerTypeCode || '—' }}</el-descriptions-item><el-descriptions-item label="登记地址" :span="2">{{ profile.currentResidence.registeredAddress || '—' }}</el-descriptions-item></el-descriptions></section>
      <section v-if="profile.activeFloating" class="profile-section"><h3>流动人口登记</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="登记编号">{{ profile.activeFloating.registrationNo || '—' }}</el-descriptions-item><el-descriptions-item label="到达日期">{{ profile.activeFloating.arrivalDate || '—' }}</el-descriptions-item></el-descriptions></section>
      <section v-if="profile.currentPermit" class="profile-section"><h3>当前居住证</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="证件编号">{{ profile.currentPermit.maskedPermitNo || '—' }}</el-descriptions-item><el-descriptions-item label="有效期至">{{ profile.currentPermit.validUntil || '—' }}</el-descriptions-item></el-descriptions></section>
      <section class="profile-section"><h3>迁入迁出历史</h3><MigrationHistoryTable :records="profile.migrationHistory" /></section>
    </template>
  </DetailDrawer>
</template>
<script setup>
import DetailDrawer from '../../../components/common/DetailDrawer.vue'
import MigrationHistoryTable from './MigrationHistoryTable.vue'
defineProps({ visible:Boolean, loading:Boolean, profile:{type:Object,default:null} })
defineEmits(['update:visible'])
</script>
<style scoped>.profile-section{margin-bottom:20px}.profile-section h3{font-size:15px;margin-bottom:10px}.drawer-loading{padding:16px}</style>
