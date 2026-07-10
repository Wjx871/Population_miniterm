<template>
  <div v-loading="loading" class="page">
    <div class="header"><div><h1>迁移申请详情</h1><p>{{ detail?.application?.applicationNo }}</p></div><div>
      <el-button v-if="isDraft" type="primary" @click="submit">提交审批</el-button>
      <el-button v-if="detail?.executable && canExecute" type="danger" @click="execute">执行业务</el-button>
    </div></div>
    <el-alert v-if="detail && !detail.executable" :title="detail.executionRestriction" type="info" :closable="false" />
    <el-card v-if="detail" shadow="never"><el-descriptions :column="2" border>
      <el-descriptions-item label="业务类型">{{ detail.application.businessType }}</el-descriptions-item>
      <el-descriptions-item label="状态"><StatusTag :status="detail.application.status" /></el-descriptions-item>
      <el-descriptions-item label="人员ID">{{ migration.personId }}</el-descriptions-item><el-descriptions-item label="迁移类型">{{ migration.migrationType }}</el-descriptions-item>
      <el-descriptions-item label="日期">{{ migration.inDate || migration.outDate }}</el-descriptions-item><el-descriptions-item label="迁移批次">{{ migration.transferBatchNo || '—' }}</el-descriptions-item>
      <el-descriptions-item label="地址" :span="2">{{ migration.fromAddress || migration.toAddress }}</el-descriptions-item><el-descriptions-item label="原因" :span="2">{{ migration.reason }}</el-descriptions-item>
    </el-descriptions></el-card>
    <el-card v-if="detail" shadow="never"><template #header><div class="card-title"><span>申请材料</span><div v-if="isDraft">
      <el-select v-model="materialType" style="width:210px;margin-right:8px"><el-option v-for="type in materialTypes" :key="type" :label="type" :value="type" /></el-select>
      <el-upload style="display:inline-block" :show-file-list="false" :http-request="upload"><el-button type="primary" plain>上传材料</el-button></el-upload>
    </div></div></template>
      <el-table :data="materials" border><el-table-column prop="materialType" label="材料类型"/><el-table-column prop="materialName" label="名称"/><el-table-column prop="verifyStatus" label="核验状态"/></el-table>
      <p class="tip">迁入需要身份证明及户口簿/地址证明；跨区还需迁移证明。迁出需要身份证明及户口簿。</p>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'; import { useRoute } from 'vue-router'; import { ElMessage, ElMessageBox } from 'element-plus';
import { getMigrationDetail, executeMigrationIn, executeMigrationOut } from '../../api/migrations'; import { listMaterials, submitApplication, uploadMaterial } from '../../api/applications'; import { useUserStore } from '../../stores/user'; import StatusTag from '../../components/common/StatusTag.vue';
const route=useRoute(),user=useUserStore(),detail=ref(),materials=ref([]),loading=ref(false),materialType=ref('IDENTITY_PROOF');
const materialTypes=['IDENTITY_PROOF','HOUSEHOLD_BOOK','ADDRESS_PROOF','RELATIONSHIP_PROOF','HOUSEHOLD_CONSENT','MIGRATION_PROOF','OTHER_SUPPORTING_DOCUMENT'];
const migration=computed(()=>detail.value?.migrationIn||detail.value?.migrationOut||{}),isDraft=computed(()=>detail.value?.application?.status==='DRAFT'),canExecute=computed(()=>user.hasPermission('migration:execute'));
async function load(){loading.value=true;try{detail.value=await getMigrationDetail(route.params.id);materials.value=await listMaterials(route.params.id)}finally{loading.value=false}}
async function submit(){await ElMessageBox.confirm('确认提交审批？提交后专业详情只读。','提交确认');await submitApplication(route.params.id);ElMessage.success('已提交审批');load()}
async function execute(){await ElMessageBox.confirm('将实际变更当前户籍、成员关系和归档，确认执行？','执行确认',{type:'warning'});const call=detail.value.application.businessType==='MIGRATION_IN'?executeMigrationIn:executeMigrationOut;await call(route.params.id,migration.value.version);ElMessage.success('迁移业务执行完成');load()}
async function upload(option){const data=new FormData();data.append('file',option.file);data.append('materialType',materialType.value);data.append('materialName',option.file.name);data.append('requiredFlag','true');await uploadMaterial(route.params.id,data,option.onProgress);ElMessage.success('材料已上传');load()}
onMounted(load);
</script>
<style scoped>.page{display:flex;flex-direction:column;gap:16px}.header,.card-title{display:flex;justify-content:space-between;align-items:center}.header h1{margin:0}.header p,.tip{color:#64748b}</style>
