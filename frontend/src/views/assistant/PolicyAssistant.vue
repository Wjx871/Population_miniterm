<template>
  <div class="assistant-page">
    <section class="assistant-hero">
      <div class="hero-orb orb-one"></div><div class="hero-orb orb-two"></div><div class="hero-grid"></div>
      <div class="hero-copy"><span class="hero-kicker"><i></i>政务智能办理助手</span><h1>政策问答、材料核验<br /><em>一次办清</em></h1><p>围绕人口户籍业务提供有依据的办理指引，并引导您完成下一步操作。</p><div class="hero-tags"><span>RAG 政策检索</span><span>OCR 材料辅助</span><span>轻量 Agent 引导</span></div></div>
      <div class="hero-mascot"><RobotMascot global-tracking /><span class="hero-word">SERVICE</span><div class="hero-hint">眼睛会跟随鼠标移动</div></div>
    </section>
    <el-card class="intro" shadow="never">
      <div><h1>政策办理知识助手</h1><p>为您提供系统内置的业务办理指引和相关功能入口。</p></div>
      <el-alert title="请勿输入身份证号、手机号等敏感信息。回答依据系统内置业务指南，正式办理要求请以主管部门最新规定为准。" type="warning" :closable="false" show-icon />
    </el-card>
    <el-card shadow="never"><template #header>推荐问题</template><el-space wrap><el-button v-for="item in suggestions" :key="item" plain @click="ask(item)">{{ item }}</el-button></el-space></el-card>
    <el-card shadow="never" class="checklist-card">
      <template #header>办理材料核验建议</template>
      <p class="checklist-intro">输入拟办理事项后，助手会结合内置指南生成待办清单。身份证 OCR 请在“人口信息管理 → 新增人员”中完成；助手不会自动提交或修改任何业务数据。</p>
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
import { getPolicySuggestions, queryPolicyAssistant, generateMaterialChecklist } from '../../api/policyAssistant.js'
import RobotMascot from '../../components/assistant/RobotMascot.vue'

const router = useRouter(); const suggestions = ref([]); const messages = ref([]); const question = ref(''); const loading = ref(false); const lastFailed = ref('')
const checklistQuestion = ref(''); const checklistLoading = ref(false); const workflow = ref(null)
const ACTION_WHITELIST = new Set(['/migrations', '/residence-permits', '/approvals', '/households', '/applications'])
onMounted(async () => { try { suggestions.value = await getPolicySuggestions() } catch { suggestions.value = [] } })
async function ask(value) { const text = String(value || '').trim(); if (!text || loading.value || text.length > 500) return; loading.value = true; lastFailed.value = ''; messages.value.push({ role: 'user', content: text }); question.value = ''; try { const response = await queryPolicyAssistant(text); messages.value.push({ role: 'assistant', content: response.answer, response }); if (messages.value.length > 6) messages.value.splice(0, messages.value.length - 6) } catch { lastFailed.value = text; ElMessage.error('咨询请求失败，请检查网络后重试') } finally { loading.value = false } }
function go(path) { if (!ACTION_WHITELIST.has(path)) return; const target = router.resolve(path); if (target.matched.length && target.meta && target.meta.requiresAuth !== false) router.push(path) }
async function generateChecklist() { const text = checklistQuestion.value.trim(); if (!text || checklistLoading.value) return; checklistLoading.value = true; try { workflow.value = await generateMaterialChecklist(text, false) } catch { ElMessage.error('无法生成核验建议，请稍后重试') } finally { checklistLoading.value = false } }
</script>

<style scoped>
.assistant-page{max-width:1100px;margin:0 auto;display:flex;flex-direction:column;gap:16px}.assistant-hero{position:relative;isolation:isolate;min-height:286px;overflow:hidden;border-radius:20px;background:linear-gradient(125deg,#dce9e8,#b9c6c9 54%,#879ca2);box-shadow:0 14px 32px rgba(18,53,70,.16)}.assistant-hero::after{content:'';position:absolute;z-index:-1;inset:0;background:linear-gradient(90deg,rgba(255,255,255,.42),transparent 43%,rgba(12,37,47,.12));pointer-events:none}.hero-grid{position:absolute;inset:0;z-index:-1;opacity:.17;background-image:linear-gradient(rgba(255,255,255,.8) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.8) 1px,transparent 1px);background-size:28px 28px;mask-image:linear-gradient(90deg,#000,transparent 70%)}.hero-orb{position:absolute;z-index:-1;border-radius:50%;filter:blur(2px);animation:orb 7s ease-in-out infinite}.orb-one{width:270px;height:270px;right:-92px;top:-120px;background:rgba(49,239,198,.34)}.orb-two{width:180px;height:180px;left:28%;bottom:-145px;background:rgba(255,255,255,.46);animation-delay:-3s}.hero-copy{position:relative;z-index:2;width:54%;padding:38px 0 30px 42px}.hero-kicker{display:inline-flex;align-items:center;gap:7px;color:#15485c;font-weight:700;font-size:13px;letter-spacing:.08em}.hero-kicker i{width:8px;height:8px;border-radius:50%;background:#16d8b1;box-shadow:0 0 9px #16d8b1;animation:pulse-dot 1.6s infinite}.hero-copy h1{margin:12px 0 10px;color:#102f3c;font-size:32px;line-height:1.22;letter-spacing:.02em}.hero-copy h1 em{font-style:normal;color:#00a987;text-shadow:0 2px 12px rgba(0,207,168,.25)}.hero-copy p{max-width:475px;margin:0;color:#365866;line-height:1.7;font-size:14px}.hero-tags{display:flex;flex-wrap:wrap;gap:8px;margin-top:21px}.hero-tags span{border:1px solid rgba(255,255,255,.72);border-radius:99px;background:rgba(255,255,255,.32);backdrop-filter:blur(5px);padding:5px 10px;color:#254e5d;font-size:12px;transition:transform .2s}.hero-tags span:hover{transform:translateY(-3px);background:rgba(255,255,255,.7)}.hero-mascot{position:absolute;right:8%;bottom:-6px;width:260px;height:270px;display:grid;place-items:end center}.hero-mascot :deep(.robot-mascot){z-index:2;scale:1.05}.hero-word{position:absolute;bottom:14px;left:50%;z-index:0;color:rgba(32,65,76,.12);font-weight:900;font-size:77px;letter-spacing:-.07em;transform:translateX(-50%);white-space:nowrap}.hero-hint{position:absolute;right:0;top:26px;border-radius:99px;background:rgba(255,255,255,.65);padding:6px 10px;color:#365560;font-size:11px;opacity:0;transform:translateY(8px);transition:.25s}.hero-mascot:hover .hero-hint{opacity:1;transform:translateY(0)}.intro{background:linear-gradient(120deg,#edf5ff,#fff)}.intro h1{margin:0 0 8px;color:#173f73}.intro p{margin:0 0 16px;color:#5f6f82}.checklist-intro{margin-top:0;color:#5f6f82}.checklist-form{display:flex;gap:10px}.workflow-summary,.checklist-table{margin:16px 0}.conversation{min-height:430px}.message{margin:16px 0;padding:14px 16px;border-radius:8px;max-width:92%;animation:message-in .32s ease both}.message.user{margin-left:auto;background:#eaf3ff}.message.assistant{background:#f7f9fc;border:1px solid #e6edf5}.message-label{font-weight:600;color:#285e9d;margin-bottom:8px}.message-text{white-space:pre-wrap;line-height:1.7;color:#273849}.message section{border-top:1px solid #e6edf5;margin-top:14px;padding-top:10px}.message h3{font-size:14px;margin:0 0 8px;color:#34495e}.message li{margin:8px 0;line-height:1.55}.composer{margin-top:20px;border-top:1px solid #edf0f5;padding-top:16px}.actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px}@keyframes orb{50%{transform:translateY(14px) scale(1.08)}}@keyframes pulse-dot{50%{opacity:.45;scale:.7}}@keyframes message-in{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}@media(max-width:820px){.hero-copy{width:68%;padding-left:26px}.hero-mascot{right:-56px;scale:.78}.hero-copy h1{font-size:27px}}@media(max-width:580px){.assistant-hero{min-height:360px}.hero-copy{width:auto;padding:28px 24px}.hero-mascot{right:50%;translate:50%;bottom:-42px;scale:.67}.hero-tags{padding-right:0}.hero-copy p{max-width:330px}.checklist-form{flex-direction:column}}
</style>
