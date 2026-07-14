const { formatDateTime } = require('../utils/date')
const STATUS = { DRAFT: '草稿', SUBMITTED: '已提交', UNDER_REVIEW: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回', COMPLETED: '已完成', CANCELLED: '已取消', PENDING: '待审批' }
const BUSINESS = { GENERAL_SERVICE: '通用服务', PERSON_CANCELLATION: '人员注销', HOUSEHOLD_CANCELLATION: '家庭户注销', MIGRATION_IN: '迁入', MIGRATION_OUT: '迁出', FLOATING_REGISTRATION: '流动人口登记', RESIDENCE_PERMIT_FIRST_ISSUE: '居住证首次申领', RESIDENCE_PERMIT_ENDORSEMENT: '居住证签注', RESIDENCE_PERMIT_CANCELLATION: '居住证注销', KEY_POPULATION_REGISTER: '重点人口建档', KEY_POPULATION_RELEASE: '重点人口解除', SENSITIVE_DATA_EXPORT: '敏感数据导出' }
const VERIFY = { PENDING: '待核验', VERIFIED: '已核验', REJECTED: '核验不通过', APPROVED: '已核验' }
function normalize(row) { row = row || {}; return Object.assign({}, row, { applicationNo: row.applicationNo || '—', title: row.title || '未命名申请', businessTypeDisplay: BUSINESS[row.businessType] || row.businessType || '—', statusDisplay: STATUS[row.status] || row.status || '—', createdAtDisplay: formatDateTime(row.createdAt), submittedAtDisplay: formatDateTime(row.submittedAt), completedAtDisplay: formatDateTime(row.completedAt) }) }
function approval(row) { row = row || {}; return Object.assign({}, row, { title: row.title || row.applicationNo || '未命名申请', businessTypeDisplay: BUSINESS[row.businessType] || row.businessType || '—', statusDisplay: STATUS[row.status] || row.status || '—', submittedAtDisplay: formatDateTime(row.submittedAt) }) }
function material(row) { row = row || {}; return Object.assign({}, row, { materialName: row.materialName || row.originalFilename || '未命名材料', materialTypeDisplay: row.materialType || '—', verifyStatusDisplay: VERIFY[row.verifyStatus] || row.verifyStatus || '—', requiredDisplay: row.requiredFlag ? '必交' : '选交', viewable: Boolean(row.materialId) }) }
function log(row) { row = row || {}; return Object.assign({}, row, { actionDisplay: STATUS[row.toStatus] || row.action || '状态变更', timeDisplay: formatDateTime(row.operationTime), commentDisplay: row.comment || '无意见' }) }
function professionalFields(raw) {
  const source = raw && (raw.professional || raw)
  if (!source || typeof source !== 'object') return []
  const labels = { businessStatus: '业务执行状态', operationType: '操作类型', personName: '涉及人员', householdNo: '涉及家庭户', eventDate: '业务日期', reason: '申请原因', migrationType: '迁移类型', sourceRegionCode: '来源区域', targetRegionCode: '目标区域', populationType: '重点人口类型', attentionLevel: '关注级别', version: '专业记录版本' }
  return Object.keys(labels).filter((key) => source[key] !== null && source[key] !== undefined && source[key] !== '').map((key) => ({ key, label: labels[key], value: STATUS[source[key]] || String(source[key]) }))
}
function normalizePage(page) { return { records: (page && page.content || []).map(normalize), total: Number(page && page.totalElements || 0), number: Number(page && page.number || 0), last: Boolean(page && page.last) } }
module.exports = { STATUS, BUSINESS, VERIFY, normalize, normalizePage, approval, material, log, professionalFields }
