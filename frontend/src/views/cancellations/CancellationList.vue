<template>
  <div class="page">
    <div class="header"><div><h1>{{ person ? '人员注销申请' : '家庭户销户申请' }}</h1><p>审批通过后仍需授权经办人显式执行。</p></div><el-button v-if="canCreate" type="primary" @click="visible=true">新建申请</el-button></div>
    <el-card shadow="never"><el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="applicationNo" label="申请编号" min-width="190"/><el-table-column prop="title" label="标题"/><el-table-column prop="applicantName" label="申请人"/>
      <el-table-column prop="status" label="状态"><template #default="{row}"><StatusTag :status="row.status"/></template></el-table-column>
      <el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="$router.push(`/cancellations/applications/${row.applicationId}`)">详情</el-button></template></el-table-column>
    </el-table><AppPagination v-model:current="page" v-model:size="size" :total="total" @change="load"/></el-card>
    <FormDialog v-model:visible="visible" :title="person?'新建人员注销申请':'新建家庭户销户申请'" :loading="saving" @confirm="save"><el-form :model="form" label-width="120px">
      <el-form-item :label="person?'人员ID':'家庭户ID'" required><el-input-number v-model="form.objectId" :min="1"/></el-form-item>
      <el-form-item label="注销原因" required><el-select v-model="form.cancelReasonCode"><el-option v-for="r in reasons" :key="r" :label="r" :value="r"/></el-select></el-form-item>
      <el-form-item label="事件日期" required><el-date-picker v-model="form.eventDate" value-format="YYYY-MM-DD"/></el-form-item>
      <el-form-item v-if="person" label="新户主ID"><el-input-number v-model="form.newHeadPersonId" :min="1"/><div class="tip">注销对象为户主且仍有其他成员时必填</div></el-form-item>
      <el-form-item label="标题" required><el-input v-model="form.title"/></el-form-item><el-form-item label="申请原因" required><el-input v-model="form.reason" type="textarea"/></el-form-item><el-form-item label="详细说明"><el-input v-model="form.cancelReasonDetail"/></el-form-item>
    </el-form></FormDialog>
  </div>
</template>
<script setup>
import{computed,onMounted,reactive,ref,watch}from'vue';import{useRoute,useRouter}from'vue-router';import{ElMessage}from'element-plus';import{listApplications}from'../../api/applications';import{createPersonCancellation,createHouseholdCancellation}from'../../api/cancellations';import{useUserStore}from'../../stores/user';import StatusTag from'../../components/common/StatusTag.vue';import AppPagination from'../../components/common/AppPagination.vue';import FormDialog from'../../components/common/FormDialog.vue';
const route=useRoute(),router=useRouter(),user=useUserStore(),person=computed(()=>route.meta.type==='person'),visible=ref(false),saving=ref(false),loading=ref(false),rows=ref([]),page=ref(1),size=ref(10),total=ref(0);const form=reactive({objectId:null,cancelReasonCode:'DEATH',eventDate:'',newHeadPersonId:null,title:'',reason:'',cancelReasonDetail:''});const reasons=computed(()=>person.value?['DEATH','DECLARED_DEAD','SETTLED_ABROAD','DUPLICATE_REGISTRATION','OTHER_APPROVED']:['NO_ACTIVE_MEMBERS','HOUSEHOLD_MERGED','ADDRESS_INVALIDATED','OTHER_APPROVED']);const canCreate=computed(()=>user.hasPermission(person.value?'cancellation:person:create':'cancellation:household:create'));
async function load(){loading.value=true;try{const r=await listApplications({businessType:person.value?'PERSON_CANCELLATION':'HOUSEHOLD_CANCELLATION',page:page.value-1,size:size.value});rows.value=r.content||[];total.value=r.totalElements||0}finally{loading.value=false}}
async function save(){if(!form.objectId||!form.eventDate||!form.title||!form.reason){ElMessage.warning('请填写必填项');return}saving.value=true;try{const data={cancelReasonCode:form.cancelReasonCode,eventDate:form.eventDate,title:form.title,reason:form.reason,cancelReasonDetail:form.cancelReasonDetail};if(person.value){data.personId=form.objectId;data.newHeadPersonId=form.newHeadPersonId}else data.householdId=form.objectId;const r=await(person.value?createPersonCancellation(data):createHouseholdCancellation(data));visible.value=false;router.push(`/cancellations/applications/${r.applicationId}`)}finally{saving.value=false}}
watch(person,()=>{form.cancelReasonCode=person.value?'DEATH':'NO_ACTIVE_MEMBERS';load()});onMounted(load);
</script><style scoped>.page{display:flex;flex-direction:column;gap:16px}.header{display:flex;justify-content:space-between}.header h1{margin:0}.header p,.tip{color:#64748b}</style>
