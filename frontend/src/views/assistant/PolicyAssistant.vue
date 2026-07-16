<template>
  <div class="assistant-page">
    <el-card class="intro" shadow="never">
      <div><h1>政策办理知识助手</h1><p>为您提供系统内置的业务办理指引和相关功能入口。</p></div>
      <el-alert title="请勿输入身份证号、手机号等敏感信息。回答依据系统内置业务指南，正式办理要求请以主管部门最新规定为准。" type="warning" :closable="false" show-icon />
    </el-card>
    <el-card shadow="never"><template #header>推荐问题</template><el-space wrap><el-button v-for="item in suggestions" :key="item" plain @click="ask(item)">{{ item }}</el-button></el-space></el-card>
    <el-card shadow="never" class="ocr-card">
      <template #header>身份证材料识别</template>
      <el-alert title="仅用于辅助核验，请上传演示或已获授权的身份证正面图片；识别结果不会自动写入人口数据库。" type="info" :closable="false" show-icon />
      <div class="ocr-upload"><el-upload :auto-upload="false" :show-file-list="false" accept="image/jpeg,image/png" :on-change="selectOcrFile"><el-button :loading="ocrLoading">上传并识别身份证正面</el-button></el-upload></div>
      <el-descriptions v-if="ocrResult" :column="2" border class="ocr-result"><el-descriptions-item label="证件类型">{{ ocrResult.documentType }}</el-descriptions-item><el-descriptions-item label="置信度">{{ Math.round(ocrResult.confidence * 100) }}%</el-descriptions-item><el-descriptions-item label="姓名">{{ ocrResult.name || '未识别' }}</el-descriptions-item><el-descriptions-item label="身份证号">{{ ocrResult.idCard || '未识别' }}</el-descriptions-item><el-descriptions-item label="出生日期">{{ ocrResult.birthDate || '未识别' }}</el-descriptions-item><el-descriptions-item label="性别">{{ ocrResult.gender || '未识别' }}</el-descriptions-item><el-descriptions-item label="户籍地址" :span="2">{{ ocrResult.address || '未识别' }}</el-descriptions-item></el-descriptions>
      <p v-if="ocrResult" class="ocr-notice">{{ ocrResult.notice }}</p>
    </el-card>
    <el-card shadow="never" class="checklist-card">
      <template #header>办理材料核验建议</template>
      <p class="checklist-intro">输入拟办理事项后，助手会结合内置指南与当前识别状态生成待办清单，不会自动提交或修改任何业务数据。</p>
      <div class="checklist-form"><el-input v-model="checklistQuestion" maxlength="500" placeholder="例如：我要办理户籍迁入" @keydown.enter.prevent="generateChecklist" /><el-button type="primary" :loading="checklistLoading" :disabled="!checklistQuestion.trim()" @click="generateChecklist">生成核验建议</el-button></div>
      <template v-if="workflow"><el-descriptions :column="2" border class="workflow-summary"><el-descriptions-item label="识别事项">{{ workflow.businessType }}</el-descriptions-item><el-descriptions-item label="下一步">{{ workflow.nextStep }}</el-descriptions-item></el-descriptions><el-table :data="workflow.checklist" border size="small" class="checklist-table"><el-table-column prop="name" label="核验项"/><el-table-column prop="status" label="当前状态" width="180"/><el-table-column prop="guidance" label="办理建议" min-width="320"/></el-table><el-button v-if="workflow.actionPath" link type="primary" @click="go(workflow.actionPath)">进入相关业务功能 →</el-button></template>
    </el-card>
    <el-card class="conversation" shadow="never">
      <template #header>咨询记录</template>
      <el-empty v-if="messages.length === 0" description="请输入您的业务办理问题" />
      <div v-for="(message,index) in messages" :key="index" class="message" :class="message.role">
        <div class="message-label">{{ message.role === 'user' ? '您的问题' : '助手答复' }}</div>
        <div class="message-text">{{ message.content }}</div>
        <template v-if="message.response">
          <section v-if="message.response.citations?.length"><h3>参考来源</h3><ol><li v-for="citation in message.response.citations" :key="citation.index"><b>[{{ citation.index }}] {{ citation.title }}</b> · {{ citation.section }}<div>{{ citation.summary }}</div></li></ol></section>
          <section v-if="message.response.suggestedActions?.length"><h3>相关功能</h3><el-button v-for="action in message.response.suggestedActions" :key="action.path" link type="primary" @click="go(action.path)">{{ action.label }} →</el-button></section>
        </template>
      </div>
      <div class="composer"><el-input v-model="question" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入业务办理问题（Enter 发送，Shift+Enter 换行）" @keydown.enter.exact.prevent="ask(question)" @keydown.enter.shift.exact.stop /><div class="actions"><el-button v-if="lastFailed" @click="ask(lastFailed)">重试</el-button><el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="ask(question)">发送</el-button></div></div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPolicySuggestions, queryPolicyAssistant, recognizeIdCard, generateMaterialChecklist } from '../../api/policyAssistant.js'

const router = useRouter(); const suggestions = ref([]); const messages = ref([]); const question = ref(''); const loading = ref(false); const lastFailed = ref('')
const ocrLoading = ref(false); const ocrResult = ref(null)
const checklistQuestion = ref(''); const checklistLoading = ref(false); const workflow = ref(null)
const ACTION_WHITELIST = new Set(['/migrations', '/residence-permits', '/approvals', '/households', '/applications'])
onMounted(async () => { try { suggestions.value = await getPolicySuggestions() } catch { suggestions.value = [] } })
async function ask(value) { const text = String(value || '').trim(); if (!text || loading.value || text.length > 500) return; loading.value = true; lastFailed.value = ''; messages.value.push({ role: 'user', content: text }); question.value = ''; try { const response = await queryPolicyAssistant(text); messages.value.push({ role: 'assistant', content: response.answer, response }); if (messages.value.length > 6) messages.value.splice(0, messages.value.length - 6) } catch { lastFailed.value = text; ElMessage.error('咨询请求失败，请检查网络后重试') } finally { loading.value = false } }
function go(path) { if (!ACTION_WHITELIST.has(path)) return; const target = router.resolve(path); if (target.matched.length && target.meta && target.meta.requiresAuth !== false) router.push(path) }
async function selectOcrFile(uploadFile) { const file = uploadFile.raw; if (!file) return; if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) { ElMessage.warning('仅支持 5MB 以内的 JPG、JPEG 或 PNG 图片'); return } ocrLoading.value = true; ocrResult.value = null; try { ocrResult.value = await recognizeIdCard(file) } catch { ElMessage.error('材料识别失败，请确认 OCR 服务已启动后重试') } finally { ocrLoading.value = false } }
async function generateChecklist() { const text = checklistQuestion.value.trim(); if (!text || checklistLoading.value) return; checklistLoading.value = true; try { workflow.value = await generateMaterialChecklist(text, Boolean(ocrResult.value?.requiresConfirmation)) } catch { ElMessage.error('无法生成核验建议，请稍后重试') } finally { checklistLoading.value = false } }
</script>

<style scoped>
.assistant-page{max-width:1100px;margin:0 auto;display:flex;flex-direction:column;gap:16px}.intro{background:linear-gradient(120deg,#edf5ff,#fff)}.intro h1{margin:0 0 8px;color:#173f73}.intro p{margin:0 0 16px;color:#5f6f82}.ocr-upload{margin-top:14px}.ocr-result{margin-top:14px}.ocr-notice{color:#8a5a18;font-size:13px}.checklist-intro{margin-top:0;color:#5f6f82}.checklist-form{display:flex;gap:10px}.workflow-summary,.checklist-table{margin:16px 0}.conversation{min-height:430px}.message{margin:16px 0;padding:14px 16px;border-radius:8px;max-width:92%}.message.user{margin-left:auto;background:#eaf3ff}.message.assistant{background:#f7f9fc;border:1px solid #e6edf5}.message-label{font-weight:600;color:#285e9d;margin-bottom:8px}.message-text{white-space:pre-wrap;line-height:1.7;color:#273849}.message section{border-top:1px solid #e6edf5;margin-top:14px;padding-top:10px}.message h3{font-size:14px;margin:0 0 8px;color:#34495e}.message li{margin:8px 0;line-height:1.55}.composer{margin-top:20px;border-top:1px solid #edf0f5;padding-top:16px}.actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px}
</style>
