const { formatDateTime } = require('../utils/date')
const { resolveStatus } = require('../utils/status')
const STATUS = { DRAFT: '草稿', SUBMITTED: '已提交', UNDER_REVIEW: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回', COMPLETED: '已完成', CANCELLED: '已取消', PENDING: '待审批' }
const BUSINESS = { GENERAL_SERVICE: '通用服务', PERSON_CANCELLATION: '人员注销', HOUSEHOLD_CANCELLATION: '家庭户注销', MIGRATION_IN: '迁入', MIGRATION_OUT: '迁出', FLOATING_REGISTRATION: '流动人口登记', RESIDENCE_PERMIT_FIRST_ISSUE: '居住证首次申领', RESIDENCE_PERMIT_ENDORSEMENT: '居住证签注', RESIDENCE_PERMIT_CANCELLATION: '居住证注销', KEY_POPULATION_REGISTER: '重点人口建档', KEY_POPULATION_RELEASE: '重点人口解除', SENSITIVE_DATA_EXPORT: '敏感数据导出' }
const VERIFY = { PENDING: '待核验', VERIFIED: '已核验', REJECTED: '核验不通过', APPROVED: '已核验' }
const ACTIONS = { CREATE: '创建申请', SUBMIT: '提交申请', APPROVE: '审批通过', REJECT: '审批驳回', WITHDRAW: '撤回申请', CANCEL: '取消申请', MATERIAL_VERIFY: '材料核验通过', MATERIAL_REJECT: '材料核验不通过', EXECUTE: '业务办理完成' }

function guidance(status) {
  const messages = {
    DRAFT: '申请尚未提交，暂未进入审批', SUBMITTED: '材料已提交，正在等待审核，无需重复提交',
    UNDER_REVIEW: '材料已提交，正在等待审核，无需重复提交', PENDING: '材料已提交，正在等待审核，无需重复提交',
    APPROVED: '审批已通过，等待业务办理', REJECTED: '申请未通过，请查看审批意见',
    COMPLETED: '业务已办理完成', WITHDRAWN: '申请已撤回，如仍需办理请重新发起申请', CANCELLED: '申请已取消'
  }
  return messages[status] || '请查看办理进度了解当前情况'
}

function normalize(row) {
  row = row || {}
  const rawStatus = row.status || ''
  const statusDisplay = STATUS[rawStatus] || (rawStatus ? '未知状态' : '—')
  const isApproved = rawStatus === 'APPROVED' || rawStatus === 'COMPLETED'
  const isExecuted = rawStatus === 'COMPLETED'
  return Object.assign({}, row, {
    applicationNo: row.applicationNo || '—', title: row.title || '未命名申请',
    businessTypeDisplay: BUSINESS[row.businessType] || (row.businessType ? '其他业务' : '—'),
    rawStatus, statusDisplay, statusTone: resolveStatus({ status: rawStatus }).type,
    nextStepDisplay: guidance(rawStatus), createdAtDisplay: formatDateTime(row.createdAt),
    submittedAtDisplay: formatDateTime(row.submittedAt), submittedTimeDisplay: formatDateTime(row.submittedAt || row.createdAt),
    completedAtDisplay: formatDateTime(row.completedAt), executionRawStatus: rawStatus,
    executionDisplay: isExecuted ? '业务已办理完成' : isApproved ? '等待业务办理' : '尚未进入办理环节',
    isApproved, isExecuted
  })
}
function approval(row) {
  row = row || {}
  const rawStatus = row.status || ''
  let waitingDisplay = ''
  const submitted = row.submittedAt && new Date(row.submittedAt)
  if (rawStatus === 'PENDING' && submitted && !Number.isNaN(submitted.getTime())) {
    const days = Math.max(0, Math.floor((Date.now() - submitted.getTime()) / 86400000))
    waitingDisplay = days > 0 ? `已等待 ${days} 天` : '今日提交'
  }
  return Object.assign({}, row, {
    title: row.title || row.applicationNo || '未命名申请', rawStatus,
    businessTypeDisplay: BUSINESS[row.businessType] || (row.businessType ? '其他业务' : '—'),
    statusDisplay: STATUS[rawStatus] || (rawStatus ? '未知状态' : '—'),
    statusTone: resolveStatus({ status: rawStatus }).type,
    submittedAtDisplay: formatDateTime(row.submittedAt), waitingDisplay,
    actionDisplay: rawStatus === 'PENDING' ? '进入审批' : '查看结果'
  })
}
function approvalDetail(row) {
  const detail = approval(row)
  return Object.assign(detail, {
    decidedAtDisplay: formatDateTime(detail.decidedAt),
    decisionCommentDisplay: detail.decisionComment || '无补充意见'
  })
}
function material(row) { row = row || {}; return Object.assign({}, row, { materialName: row.materialName || row.originalFilename || '未命名材料', verifyStatusDisplay: VERIFY[row.verifyStatus] || (row.verifyStatus ? '状态待确认' : '未核验'), requiredDisplay: row.requiredFlag ? '必交材料' : '补充材料', viewable: Boolean(row.materialId) }) }
function log(row) { row = row || {}; return Object.assign({}, row, { actionDisplay: ACTIONS[row.action] || STATUS[row.toStatus] || '办理进度更新', timeDisplay: formatDateTime(row.operationTime), commentDisplay: row.comment || '无补充说明' }) }
function professionalFields(raw) {
  const source = raw && (raw.professional || raw)
  if (!source || typeof source !== 'object') return []
  const labels = { businessStatus: '业务办理状态', operationType: '办理类型', personName: '涉及人员', householdNo: '涉及家庭户', eventDate: '业务日期', reason: '申请原因', migrationType: '迁移类型', sourceRegionCode: '来源区域', targetRegionCode: '目标区域', populationType: '人员类型', attentionLevel: '关注级别' }
  return Object.keys(labels).filter((key) => source[key] !== null && source[key] !== undefined && source[key] !== '').map((key) => {
    const value = String(source[key])
    return { key, label: labels[key], value: STATUS[value] || BUSINESS[value] || (/^[A-Z][A-Z0-9_]*$/.test(value) ? '相关信息已登记' : value) }
  })
}
function progress(application) {
  const app = application || normalize(null)
  const raw = app.rawStatus
  const reviewed = ['APPROVED', 'REJECTED', 'COMPLETED'].includes(raw)
  return [
    { key: 'submitted', title: '提交申请', state: app.submittedAt ? 'done' : raw === 'DRAFT' ? 'current' : 'done', time: app.submittedAtDisplay, result: app.submittedAt ? '申请已提交' : '等待提交' },
    { key: 'review', title: '受理审核', state: ['SUBMITTED', 'UNDER_REVIEW', 'PENDING'].includes(raw) ? 'current' : reviewed ? 'done' : 'waiting', time: '—', result: reviewed ? '审核已完成' : ['SUBMITTED', 'UNDER_REVIEW', 'PENDING'].includes(raw) ? '正在审核' : '等待受理' },
    { key: 'decision', title: '审批决定', state: reviewed ? 'done' : 'waiting', time: reviewed ? app.completedAtDisplay : '—', result: reviewed ? app.statusDisplay : '等待审批' },
    { key: 'execution', title: '业务办理', state: app.isExecuted ? 'done' : app.isApproved ? 'current' : 'waiting', time: app.isExecuted ? app.completedAtDisplay : '—', result: app.executionDisplay }
  ]
}
function approvalResult(logs) {
  const decisions = (logs || []).filter((item) => item.action === 'APPROVE' || item.action === 'REJECT')
  const latest = decisions[decisions.length - 1]
  return latest ? { available: true, result: latest.actionDisplay, comment: latest.commentDisplay, time: latest.timeDisplay } : { available: false }
}
function normalizePage(page) { return { records: (page && page.content || []).map(normalize), total: Number(page && page.totalElements || 0), number: Number(page && page.number || 0), last: Boolean(page && page.last) } }
module.exports = { STATUS, BUSINESS, VERIFY, ACTIONS, normalize, normalizePage, approval, approvalDetail, material, log, professionalFields, progress, approvalResult }
