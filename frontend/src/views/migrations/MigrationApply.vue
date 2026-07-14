<template>
  <div class="page-container">
    <div class="page-header"><div><h1>{{ pageTitle }}</h1><p class="subtitle">先保存草稿，再上传材料并提交审批；提交后申请仅可查看或撤回。</p></div></div>
    <el-steps :active="step" finish-status="success" simple><el-step title="业务信息" /><el-step title="申请材料" /><el-step title="提交审批" /></el-steps>
    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" class="migration-form" :model="form" :rules="rules" label-width="136px" @submit.prevent>
        <el-form-item label="申请标题" prop="title"><el-input v-model.trim="form.title" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="办理人员" prop="personId"><PersonSelect v-model="form.personId" :disabled="isReadonly" @select="handlePersonSelect" /></el-form-item>
        <el-form-item label="迁移类型" prop="migrationType"><el-select v-model="form.migrationType" :disabled="isReadonly" style="width: 100%"><el-option v-for="item in MIGRATION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <template v-if="isIn">
          <el-form-item label="迁出地行政区划"><el-input v-model.trim="form.fromRegionCode" :disabled="isReadonly" maxlength="20" /></el-form-item>
          <el-form-item label="迁出地地址" prop="fromAddress"><el-input v-model.trim="form.fromAddress" :disabled="isReadonly" maxlength="255" /></el-form-item>
          <el-form-item label="迁入地行政区划" prop="toRegionCode"><RegionCascader v-model="form.toRegionCode" :disabled="isReadonly" /></el-form-item>
          <el-form-item label="目标家庭户" prop="toHouseholdId"><HouseholdSelect v-model="form.toHouseholdId" :region-code="form.toRegionCode" status="ACTIVE" :disabled="isReadonly || !form.toRegionCode" /></el-form-item>
          <el-form-item label="迁入日期" prop="inDate"><el-date-picker v-model="form.inDate" type="date" value-format="YYYY-MM-DD" :disabled="isReadonly" /></el-form-item>
        </template>
        <el-alert v-if="!isIn && selectedProfile" class="eligibility-alert" :type="hasActiveResidence ? 'success' : 'error'" :closable="false" show-icon>
          <template #title>{{ hasActiveResidence ? '当前有效户籍' : '该人员没有当前有效户籍，无法办理迁出' }}</template>
          <template v-if="hasActiveResidence" #default>家庭户：{{ selectedProfile.currentHousehold?.householdNo || '-' }}；户籍编号：{{ selectedProfile.currentResidence?.residenceId || '-' }}；当前地址：{{ selectedProfile.currentResidence?.registeredAddress || '-' }}；状态：{{ selectedProfile.currentResidence?.status || '-' }}</template>
        </el-alert>
        <template v-else>
          <el-form-item label="迁往地行政区划" prop="toRegionCode"><el-input v-model.trim="form.toRegionCode" :disabled="isReadonly" maxlength="20" /></el-form-item>
          <el-form-item label="迁往地地址" prop="toAddress"><el-input v-model.trim="form.toAddress" :disabled="isReadonly" maxlength="255" /></el-form-item>
          <el-form-item label="迁出日期" prop="outDate"><el-date-picker v-model="form.outDate" type="date" value-format="YYYY-MM-DD" :disabled="isReadonly" /></el-form-item>
          <el-form-item v-if="requiresNewHead" label="指定新户主" prop="newHeadPersonId" required><MemberCandidateSelect v-model="form.newHeadPersonId" :candidates="memberCandidates" :disabled="isReadonly" /><div class="form-tip">户主迁出且仍有其他成员时，必须指定本户其他有效成员作为新户主。</div></el-form-item>
        </template>
        <el-form-item label="迁移原因" prop="reason"><el-input v-model.trim="form.reason" type="textarea" :rows="3" :disabled="isReadonly" /></el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="form.remark" type="textarea" :rows="2" :disabled="isReadonly" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="isIn" label="迁移批次号" prop="transferBatchNo" :required="requiresTransferBatch"><el-input v-model.trim="form.transferBatchNo" :disabled="isReadonly" maxlength="40" /></el-form-item>
        <el-form-item><el-button v-if="!isReadonly" type="primary" :loading="saving" :disabled="!isIn && form.personId && !hasActiveResidence" @click="saveDraft">保存草稿</el-button><el-button @click="router.back()">返回</el-button></el-form-item>
      </el-form>
    </el-card>
    <template v-if="applicationId">
      <MaterialUploader v-if="!isReadonly && canUpload" :application-id="applicationId" :material-options="materialOptions" :material-rule-text="materialRuleText" @uploaded="refreshDetail" />
      <el-card shadow="never"><template #header>已上传材料</template><MaterialList :materials="detail?.materials || []" :can-delete="!isReadonly && canDelete" @changed="refreshDetail" /></el-card>
      <ApplicationActionBar :application="detail?.application" :loading="submitting" @submit="submit" @withdraw="withdraw" @cancel="cancelDraft" />
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PersonSelect from '../../components/business/PersonSelect.vue'
import HouseholdSelect from '../../components/business/HouseholdSelect.vue'
import MemberCandidateSelect from '../../components/business/MemberCandidateSelect.vue'
import MaterialUploader from '../../components/business/MaterialUploader.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import ApplicationActionBar from '../../components/business/ApplicationActionBar.vue'
import RegionCascader from '../../components/business/RegionCascader.vue'
import { createMigrationInApplication, createMigrationOutApplication, getMigrationApplicationDetail, updateMigrationInApplication, updateMigrationOutApplication } from '../../api/migrations'
import { cancelDraftApplication, submitApplication, withdrawApplication } from '../../api/applications'
import { MIGRATION_TYPE_OPTIONS } from '../../constants/migration'
import { getMigrationMaterialOptions, getMigrationMaterialRuleText } from '../../constants/material'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'
import { getPersonById } from '../../api/persons'
import { normalizePerson } from '../../adapters/person'
import { getComprehensivePersonProfile } from '../../api/query'
import { normalizeComprehensiveProfile } from '../../adapters/comprehensiveQuery'

const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
const isIn = computed(() => route.meta.type === 'in')
const pageTitle = computed(() => isIn.value ? '迁入申请' : '迁出申请')
const applicationId = ref(route.query.applicationId || null); const detail = ref(null); const saving = ref(false); const submitting = ref(false); const formRef = ref(); const memberCandidates = ref([])
const selectedProfile = ref(null)
const form = reactive({ personId: null, migrationType: 'OUTSIDE_CITY', fromRegionCode: '', fromAddress: '', toRegionCode: '', toHouseholdId: null, inDate: '', toAddress: '', outDate: '', reason: '', title: '', remark: '', transferBatchNo: '', newHeadPersonId: null, version: null })
const isReadonly = computed(() => applicationId.value && detail.value?.application?.status !== 'DRAFT')
const step = computed(() => !applicationId.value ? 0 : isReadonly.value ? 2 : 1)
const requiresNewHead = computed(() => !isIn.value && detail.value?.household?.headPersonId === form.personId && Number(detail.value?.household?.activeMemberCount) > 1)
const candidateIds = computed(() => (detail.value?.activeMemberPersonIds || []).filter((id) => Number(id) !== Number(form.personId)))
const requiresTransferBatch = computed(() => isIn.value && form.migrationType === 'IN_CITY_CROSS_DISTRICT')
const hasActiveResidence = computed(() => Boolean(selectedProfile.value?.currentResidence && selectedProfile.value.currentResidence.status === 'ACTIVE'))
const materialOptions = computed(() => getMigrationMaterialOptions(isIn.value ? 'in' : 'out', form.migrationType))
const materialRuleText = computed(() => getMigrationMaterialRuleText(isIn.value ? 'in' : 'out', form.migrationType))
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD)); const canDelete = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))
const rules = computed(() => ({ personId: [{ required: true, message: '请选择办理人员', trigger: 'change' }], migrationType: [{ required: true, message: '请选择迁移类型', trigger: 'change' }], title: [{ required: true, message: '请输入申请标题', trigger: 'blur' }], reason: [{ required: true, message: '请输入迁移原因', trigger: 'blur' }], toRegionCode: [{ required: true, message: '请输入行政区划代码', trigger: 'blur' }], ...(requiresTransferBatch.value ? { transferBatchNo: [{ required: true, message: '同市跨区迁入必须填写迁移批次号', trigger: 'blur' }] } : {}), ...(isIn.value ? { fromAddress: [{ required: true, message: '请输入迁出地地址', trigger: 'blur' }], toHouseholdId: [{ required: true, message: '请选择目标家庭户', trigger: 'change' }], inDate: [{ required: true, message: '请选择迁入日期', trigger: 'change' }] } : { toAddress: [{ required: true, message: '请输入迁往地地址', trigger: 'blur' }], outDate: [{ required: true, message: '请选择迁出日期', trigger: 'change' }], ...(requiresNewHead.value ? { newHeadPersonId: [{ required: true, message: '请指定新户主', trigger: 'change' }] } : {}) }) }))

function payload() { return isIn.value ? { personId: form.personId, migrationType: form.migrationType, fromRegionCode: form.fromRegionCode || null, fromAddress: form.fromAddress, toRegionCode: form.toRegionCode, toHouseholdId: form.toHouseholdId, inDate: form.inDate, reason: form.reason, title: form.title, remark: form.remark || null, transferBatchNo: form.transferBatchNo || null, version: form.version } : { personId: form.personId, migrationType: form.migrationType, toRegionCode: form.toRegionCode, toAddress: form.toAddress, outDate: form.outDate, reason: form.reason, newHeadPersonId: form.newHeadPersonId || null, title: form.title, remark: form.remark || null, version: form.version } }
async function handlePersonSelect(person) { selectedProfile.value = null; if (isIn.value || !person?.id) return; try { selectedProfile.value = normalizeComprehensiveProfile(await getComprehensivePersonProfile(person.id)) } catch (error) { ElMessage.error(getApiErrorMessage(error, '户籍资格校验失败')) } }
function fill(record) { if (!record) return; Object.assign(form, { personId: record.personId, migrationType: record.migrationType, fromRegionCode: record.fromRegionCode || '', fromAddress: record.fromAddress || '', toRegionCode: record.toRegionCode || '', toHouseholdId: record.toHouseholdId || null, inDate: record.inDate || '', toAddress: record.toAddress || '', outDate: record.outDate || '', reason: record.reason || '', transferBatchNo: record.transferBatchNo || '', newHeadPersonId: record.newHeadPersonId || null, version: record.version }) }
async function loadMemberCandidates() { memberCandidates.value = []; if (!requiresNewHead.value) return; const candidates = await Promise.all(candidateIds.value.map(async (personId) => { try { const person = normalizePerson(await getPersonById(personId)); return person.id ? { personId: person.id, name: person.name, idCard: person.idCard } : null } catch { return null } })); memberCandidates.value = candidates.filter(Boolean) }
async function refreshDetail() { if (!applicationId.value) return; try { detail.value = await getMigrationApplicationDetail(applicationId.value); fill(isIn.value ? detail.value.migrationIn : detail.value.migrationOut); form.title = detail.value.application?.title || form.title; form.remark = detail.value.application?.remark || form.remark; selectedProfile.value = !isIn.value && form.personId ? normalizeComprehensiveProfile(await getComprehensivePersonProfile(form.personId)) : null; await loadMemberCandidates() } catch (error) { ElMessage.error(getApiErrorMessage(error, '加载申请详情失败')) } }
function validateSpecialRules() { if (!isIn.value && !hasActiveResidence.value) { ElMessage.error('该人员没有当前有效户籍，无法办理迁出'); return false } if (requiresTransferBatch.value && !form.transferBatchNo?.trim()) { ElMessage.error('同市跨区迁入必须填写迁移批次号'); return false } if (requiresNewHead.value && !form.newHeadPersonId) { ElMessage.error('户主迁出且仍有其他成员时必须指定新户主'); return false } if (requiresNewHead.value && !candidateIds.value.some((id) => Number(id) === Number(form.newHeadPersonId))) { ElMessage.error('新户主必须是本户其他有效成员'); return false } return true }
async function saveDraft() { const valid = await formRef.value.validate().catch(() => false); if (!valid || (applicationId.value && !validateSpecialRules())) return; saving.value = true; try { const result = applicationId.value ? await (isIn.value ? updateMigrationInApplication(applicationId.value, payload()) : updateMigrationOutApplication(applicationId.value, payload())) : await (isIn.value ? createMigrationInApplication(payload()) : createMigrationOutApplication(payload())); applicationId.value = result.applicationId || applicationId.value; ElMessage.success('草稿已保存'); await router.replace({ query: { applicationId: applicationId.value } }); await refreshDetail() } catch (error) { if (isApiConflict(error)) await refreshDetail(); ElMessage.error(getApiErrorMessage(error, '草稿保存失败')) } finally { saving.value = false } }
async function submit() { const valid = await formRef.value.validate().catch(() => false); if (!valid || !validateSpecialRules()) return; await ElMessageBox.confirm('提交后将不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' }); submitting.value = true; try { await submitApplication(applicationId.value); ElMessage.success('申请已提交，等待审批'); await refreshDetail() } catch (error) { if (isApiConflict(error)) await refreshDetail(); ElMessage.error(getApiErrorMessage(error, '提交失败')) } finally { submitting.value = false } }
async function withdraw() { await ElMessageBox.confirm('确认撤回该申请吗？', '撤回申请', { type: 'warning' }); try { await withdrawApplication(applicationId.value); ElMessage.success('申请已撤回'); await refreshDetail() } catch (error) { if (isApiConflict(error)) await refreshDetail(); ElMessage.error(getApiErrorMessage(error, '撤回失败')) } }
async function cancelDraft() { await ElMessageBox.confirm('确认取消该草稿吗？取消后不能恢复。', '取消草稿', { type: 'warning' }); try { await cancelDraftApplication(applicationId.value); ElMessage.success('草稿已取消'); await router.replace(isIn.value ? '/migrations/in' : '/migrations/out') } catch (error) { if (isApiConflict(error)) await refreshDetail(); ElMessage.error(getApiErrorMessage(error, '取消草稿失败')) } }
onMounted(refreshDetail)
</script>

<style scoped>.page-container{display:flex;flex-direction:column;gap:16px}.page-header h1{margin:0 0 8px}.subtitle,.form-tip{color:var(--el-text-color-secondary);font-size:13px}.form-card{max-width:860px}.migration-form :deep(.el-form-item__label){white-space:nowrap}.eligibility-alert{margin:0 0 18px}</style>
