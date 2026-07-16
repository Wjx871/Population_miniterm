import request from './request.js'

export const getPolicySuggestions = () => request.get('/assistant/policy/suggestions')
export const queryPolicyAssistant = (question) => request.post('/assistant/policy/query', { question })
