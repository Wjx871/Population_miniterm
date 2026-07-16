import request from './request.js'

export const getPolicySuggestions = () => request.get('/assistant/policy/suggestions')
export const queryPolicyAssistant = (question, history = []) => request.post('/assistant/policy/query', { question, history })
export const generateMaterialChecklist = (question, idCardRecognized) => request.post('/assistant/policy/check-materials', { question, idCardRecognized })
