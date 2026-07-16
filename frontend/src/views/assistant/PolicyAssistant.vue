<template>
  <div class="assistant-page">
    <el-card class="intro" shadow="never">
      <div><h1>政策办理知识助手</h1><p>为您提供系统内置的业务办理指引和相关功能入口。</p></div>
      <el-alert title="请勿输入身份证号、手机号等敏感信息。回答依据系统内置业务指南，正式办理要求请以主管部门最新规定为准。" type="warning" :closable="false" show-icon />
    </el-card>
    <el-card shadow="never"><template #header>推荐问题</template><el-space wrap><el-button v-for="item in suggestions" :key="item" plain @click="ask(item)">{{ item }}</el-button></el-space></el-card>
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
import { getPolicySuggestions, queryPolicyAssistant } from '../../api/policyAssistant.js'

const router = useRouter(); const suggestions = ref([]); const messages = ref([]); const question = ref(''); const loading = ref(false); const lastFailed = ref('')
const ACTION_WHITELIST = new Set(['/migrations', '/residence-permits', '/approvals', '/households', '/applications'])
onMounted(async () => { try { suggestions.value = await getPolicySuggestions() } catch { suggestions.value = [] } })
async function ask(value) { const text = String(value || '').trim(); if (!text || loading.value || text.length > 500) return; loading.value = true; lastFailed.value = ''; messages.value.push({ role: 'user', content: text }); question.value = ''; try { const response = await queryPolicyAssistant(text); messages.value.push({ role: 'assistant', content: response.answer, response }); if (messages.value.length > 6) messages.value.splice(0, messages.value.length - 6) } catch { lastFailed.value = text; ElMessage.error('咨询请求失败，请检查网络后重试') } finally { loading.value = false } }
function go(path) { if (!ACTION_WHITELIST.has(path)) return; const target = router.resolve(path); if (target.matched.length && target.meta && target.meta.requiresAuth !== false) router.push(path) }
</script>

<style scoped>
.assistant-page{max-width:1100px;margin:0 auto;display:flex;flex-direction:column;gap:16px}.intro{background:linear-gradient(120deg,#edf5ff,#fff)}.intro h1{margin:0 0 8px;color:#173f73}.intro p{margin:0 0 16px;color:#5f6f82}.conversation{min-height:430px}.message{margin:16px 0;padding:14px 16px;border-radius:8px;max-width:92%}.message.user{margin-left:auto;background:#eaf3ff}.message.assistant{background:#f7f9fc;border:1px solid #e7edf5}.message-label{font-weight:600;color:#285e9d;margin-bottom:8px}.message-text{white-space:pre-wrap;line-height:1.7;color:#273849}.message section{border-top:1px solid #e6edf5;margin-top:14px;padding-top:10px}.message h3{font-size:14px;margin:0 0 8px;color:#34495e}.message li{margin:8px 0;line-height:1.55}.composer{margin-top:20px;border-top:1px solid #edf0f5;padding-top:16px}.actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px}
</style>
