import request from './request.js'

export const getPolicySuggestions = () => request.get('/assistant/policy/suggestions')
export const queryPolicyAssistant = (question) => request.post('/assistant/policy/query', { question })
export const recognizeIdCard = (file) => { const form = new FormData(); form.append('file', file); return request.post('/assistant/policy/ocr/id-card', form) }
