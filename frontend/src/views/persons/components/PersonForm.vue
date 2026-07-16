<template>
  <el-form ref="formRef" :model="form" :rules="activeRules" label-width="108px">
    <el-form-item v-if="!isEdit" label="登记类型" prop="registrationType">
      <el-select v-model="form.registrationType" style="width:100%" @change="changeRegistrationType">
        <el-option v-for="item in PERSON_REGISTRATION_TYPES" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </el-form-item>

    <el-alert v-if="!isEdit" class="registration-alert" :title="registrationType.label" type="info" :closable="false" show-icon>
      <template #default>{{ registrationType.description }}</template>
    </el-alert>

    <template v-if="isDirectArchive || isEdit">
      <el-form-item v-if="!isEdit" label="身份证影印本">
        <div class="idcard-block">
          <el-button :icon="Camera" type="primary" plain @click="scannerVisible = true">{{ image ? '重新扫描' : '扫描身份证' }}</el-button>
          <el-tag v-if="image" :type="imageTagType" effect="plain">OCR：{{ ocrStatusText }}</el-tag>
          <el-button v-if="image" link type="danger" @click="clearImage">清除</el-button>
          <div v-if="image" class="idcard-meta"><span>影印本：{{ image.fileName }}（{{ formatBytes(image.fileSize) }}）</span><span v-if="image.maskedIdCard">识别号：{{ image.maskedIdCard }}</span></div>
          <div v-else class="idcard-hint">持居民身份证建档必须上传身份证影印本；OCR 仅辅助回填，提交前请人工核对。</div>
        </div>
      </el-form-item>
      <IdCardScannerDialog v-model:visible="scannerVisible" @recognized="onRecognized" @skipped="onSkipped" />

      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" placeholder="请输入姓名" maxlength="50" show-word-limit /></el-form-item>
      <el-form-item label="性别" prop="gender"><el-radio-group v-model="form.gender"><el-radio value="男">男</el-radio><el-radio value="女">女</el-radio></el-radio-group></el-form-item>
      <el-form-item label="身份证号" prop="idCard"><el-input v-model="form.idCard" placeholder="请输入身份证号" :disabled="isEdit" maxlength="18" /></el-form-item>
      <el-form-item label="出生日期" prop="birthDate"><el-date-picker v-model="form.birthDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" :disabled-date="disableFutureDate" style="width:100%" /></el-form-item>
      <el-form-item label="民族" prop="ethnicity"><DictionarySelect v-model="form.ethnicity" type="ETHNICITY" value-mode="label" placeholder="请选择民族" style="width:100%" /></el-form-item>
      <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" placeholder="手机号码（选填）" maxlength="20" /></el-form-item>
      <el-form-item label="现居住地址" prop="currentAddress"><el-input v-model="form.currentAddress" type="textarea" :rows="2" placeholder="请输入详细地址" maxlength="255" show-word-limit /></el-form-item>
    </template>

    <template v-else>
      <el-form-item label="申请人姓名" prop="name"><el-input v-model="form.name" placeholder="请输入待登记人员姓名；新生儿可按出生医学证明填写" maxlength="50" /></el-form-item>
      <el-form-item label="性别" prop="gender"><el-radio-group v-model="form.gender"><el-radio value="男">男</el-radio><el-radio value="女">女</el-radio><el-radio value="待确认">待确认</el-radio></el-radio-group></el-form-item>
      <el-form-item label="出生日期"><el-date-picker v-model="form.birthDate" type="date" placeholder="可选，按证明材料填写" value-format="YYYY-MM-DD" :disabled-date="disableFutureDate" style="width:100%" /></el-form-item>
      <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" placeholder="手机号码（选填）" maxlength="20" /></el-form-item>
      <el-form-item label="拟落户地址"><el-input v-model="form.currentAddress" type="textarea" :rows="2" placeholder="请输入拟落户地或联系地址" maxlength="255" show-word-limit /></el-form-item>

      <el-divider content-position="left">登记申请材料</el-divider>
      <p class="material-tip">材料将上传至登记申请草稿，后续由人工审核。各地具体材料要求以当地公安户政部门最新规定为准。</p>
      <div class="registration-materials">
        <div v-for="doc in registrationType.documents" :key="doc.type" class="material-row">
          <div><b>{{ doc.name }}</b><el-tag v-if="doc.required" size="small" type="danger" effect="plain">必传</el-tag><span v-else class="optional">选填</span></div>
          <div class="material-upload"><el-upload :auto-upload="false" :show-file-list="false" accept="image/jpeg,image/png,application/pdf" :on-change="file => selectDocument(doc.type, file)"><el-button size="small" plain>选择文件</el-button></el-upload><span v-if="documentFiles[doc.type]" class="file-name">{{ documentFiles[doc.type].name }}</span><span v-else class="file-empty">未选择</span></div>
        </div>
      </div>
    </template>
  </el-form>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Camera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '../../../utils/date'
import { validateIdCard, validatePhone, validateBirthDate } from '../../../utils/validators'
import DictionarySelect from '../../../components/business/DictionarySelect.vue'
import IdCardScannerDialog from './IdCardScannerDialog.vue'
import { PERSON_REGISTRATION_TYPES, getRegistrationType } from '../../../constants/personRegistration'

const props = defineProps({ modelValue: { type: Object, default: () => ({}) }, isEdit: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const formRef = ref(null); const image = ref(null); const scannerVisible = ref(false); const documentFiles = reactive({})
const form = reactive({ registrationType: 'ID_CARD_ARCHIVE', name: '', gender: '男', idCard: '', birthDate: '', ethnicity: '汉族', phone: '', currentAddress: '', idCardImageId: null })
const registrationType = computed(() => getRegistrationType(form.registrationType)); const isDirectArchive = computed(() => registrationType.value.directCreate)
const baseRules = { registrationType: [{ required: true, message: '请选择登记类型', trigger: 'change' }], name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], gender: [{ required: true, message: '请选择性别', trigger: 'change' }], phone: [{ validator: validatePhone, trigger: 'blur' }] }
const activeRules = computed(() => isDirectArchive.value || props.isEdit ? { ...baseRules, idCard: [{ required: true, message: '请输入身份证号', trigger: 'blur' }, { validator: validateIdCard, trigger: 'blur' }], birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }, { validator: validateBirthDate, trigger: 'change' }] } : baseRules)
const imageTagType = computed(() => image.value?.ocrStatus === 'SUCCESS' ? 'success' : image.value?.ocrStatus === 'FAILED' ? 'warning' : 'info')
const ocrStatusText = computed(() => ({ SUCCESS: '识别成功', FAILED: '识别失败', SKIPPED: '已跳过' })[image.value?.ocrStatus] || '未识别')

function changeRegistrationType() { Object.keys(documentFiles).forEach(key => delete documentFiles[key]); form.idCardImageId = null; image.value = null; formRef.value?.clearValidate() }
function selectDocument(type, file) { if (file?.raw) documentFiles[type] = { name: file.name, raw: file.raw } }
function disableFutureDate(date) { const today = new Date(); today.setHours(23, 59, 59, 999); return date.getTime() > today.getTime() }
function syncFromModel(value) { Object.assign(form, { registrationType: value?.registrationType || 'ID_CARD_ARCHIVE', name: value?.name ?? '', gender: value?.gender || '男', idCard: value?.idCard ?? '', birthDate: value?.birthDate ? formatDate(value.birthDate) : '', ethnicity: value?.ethnicity ?? '汉族', phone: value?.phone ?? '', currentAddress: value?.currentAddress ?? '', idCardImageId: value?.idCardImageId ?? null }); if (props.isEdit) image.value = value?.idCardImage || null }
function onRecognized(result) { if (!result?.imageId) return ElMessage.warning('上传成功但未返回影印本标识'); image.value = { imageId: result.imageId, fileName: result.originalFilename, fileSize: result.fileSize, ocrStatus: result.ocrStatus, maskedIdCard: result.ocrIdcardMasked }; form.idCardImageId = result.imageId; applyOcrToForm(result); scannerVisible.value = false }
function onSkipped(result) { if (result?.imageId) onRecognized({ ...result, ocrStatus: 'SKIPPED', ocrIdcardMasked: null }); else scannerVisible.value = false }
function applyOcrToForm(result) { if (result.ocrStatus !== 'SUCCESS') return; if (!form.idCard && result.ocrIdcardFull) form.idCard = result.ocrIdcardFull; if (!form.name && result.ocrName) form.name = result.ocrName; if (result.ocrGender === 'M') form.gender = '男'; else if (result.ocrGender === 'F') form.gender = '女'; if (!form.birthDate && result.ocrBirthDate) form.birthDate = String(result.ocrBirthDate).substring(0, 10); if (!form.ethnicity && result.ocrEthnicity) form.ethnicity = result.ocrEthnicity; if (!form.currentAddress && result.ocrAddress) form.currentAddress = result.ocrAddress }
function clearImage() { image.value = null; form.idCardImageId = null }
function formatBytes(value) { if (!value) return '0 B'; return `${(Number(value) / 1024 / 1024).toFixed(2)} MB` }
watch(() => props.modelValue, value => syncFromModel(value || {}), { immediate: true, deep: true }); watch(form, () => emit('update:modelValue', { ...form }), { deep: true })
async function validate() { try { await formRef.value?.validate() } catch { return false } if (!isDirectArchive.value && !props.isEdit) { const missing = registrationType.value.documents.filter(doc => doc.required && !documentFiles[doc.type]); if (missing.length) { ElMessage.error(`请上传必传材料：${missing.map(doc => doc.name).join('、')}`); return false } } return true }
function clearValidate() { formRef.value?.clearValidate() }
function getRegistrationApplication() { return { type: registrationType.value, name: form.name, gender: form.gender, birthDate: form.birthDate, phone: form.phone, currentAddress: form.currentAddress, documents: registrationType.value.documents.filter(doc => documentFiles[doc.type]).map(doc => ({ ...doc, file: documentFiles[doc.type].raw })) } }
defineExpose({ validate, clearValidate, getForm: () => ({ ...form }), getRegistrationApplication })
</script>

<style scoped>
.registration-alert{margin-bottom:18px}.idcard-block{display:flex;flex-wrap:wrap;align-items:center;gap:8px;width:100%}.idcard-meta{display:flex;flex-wrap:wrap;gap:10px;width:100%;color:var(--el-text-color-regular);font-size:13px}.idcard-hint,.material-tip{margin:0;color:var(--el-text-color-secondary);font-size:13px;line-height:1.6}.registration-materials{border:1px solid var(--el-border-color-lighter);border-radius:8px;overflow:hidden}.material-row{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:12px 14px;border-bottom:1px solid var(--el-border-color-lighter)}.material-row:last-child{border-bottom:0}.material-row b{margin-right:7px;font-size:13px}.optional,.file-empty{color:var(--el-text-color-secondary);font-size:12px}.material-upload{display:flex;align-items:center;gap:8px}.file-name{max-width:180px;overflow:hidden;color:#3972c6;font-size:12px;text-overflow:ellipsis;white-space:nowrap}@media(max-width:640px){.material-row{align-items:flex-start;flex-direction:column;gap:8px}}
</style>
