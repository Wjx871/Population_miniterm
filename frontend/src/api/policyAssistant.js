import request from './request.js'

export const getPolicySuggestions = () => request.get('/assistant/policy/suggestions')
// 本地生成可能慢于普通 CRUD；仅问答延长等待，不修改全局接口超时。
export const queryPolicyAssistant = (question, history = []) => request.post('/assistant/policy/query', { question, history }, { timeout: 75000 })
export const generateMaterialChecklist = (question, idCardRecognized) => request.post('/assistant/policy/check-materials', { question, idCardRecognized })
