const { formatDate, formatDateTime } = require('../utils/date')
const { resolveStatus } = require('../utils/status')

const PERMIT_STATUS = { ACTIVE: '有效', EXPIRED: '已过期', CANCELLED: '已注销' }
const FLOATING_STATUS = { ACTIVE: '有效登记', CLOSED: '已关闭', EXPIRED: '已到期' }
const MIGRATION_STATUS = { DRAFT: '草稿', SUBMITTED: '已提交', UNDER_REVIEW: '审批中', APPROVED: '已通过', REJECTED: '已驳回', RETURNED: '已退回', RESUBMITTED: '已重新提交', COMPLETED: '已办结', WITHDRAWN: '已撤回', CANCELLED: '已取消', ACTIVE: '已办结' }
const MIGRATION_TYPE = { MIGRATION_IN: '迁入', MIGRATION_OUT: '迁出', IN: '迁入', OUT: '迁出' }
const RESIDENCE_BASIS = { EMPLOYMENT: '就业', STUDY: '就学', BUSINESS: '经商', FAMILY: '家庭团聚', OTHER: '其他' }

function display(value, fallback = '未登记') { return value === undefined || value === null || value === '' ? fallback : String(value) }
function masked(value, visible = 4) {
  const text = display(value, '未登记')
  if (text === '未登记' || text.includes('*') || text.length <= visible) return text
  return `${'*'.repeat(Math.min(8, text.length - visible))}${text.slice(-visible)}`
}
function totalOf(page) {
  const total = page && page.totalElements
  return typeof total === 'number' && Number.isFinite(total) && total >= 0 ? total : null
}
function pageOf(page, mapper) {
  const total = totalOf(page)
  return {
    records: ((page && page.content) || []).map(mapper), total,
    totalDisplay: total === null ? '数量暂不可用' : String(total),
    number: Number.isInteger(page && page.number) ? page.number : 0,
    last: Boolean(page && page.last)
  }
}
function permit(row) {
  row = row || {}
  const rawStatus = row.status || ''
  return Object.assign({}, row, {
    permitId: row.permitId, permitNoDisplay: masked(row.permitNo),
    personName: display(row.personName), identityNoDisplay: masked(row.maskedIdentityNo || row.identityNo),
    currentAddressDisplay: display(row.currentAddress), regionDisplay: display(row.issueRegionCode || row.currentRegionCode),
    validFromDisplay: formatDate(row.validFrom), validUntilDisplay: formatDate(row.validUntil),
    rawStatus, statusDisplay: PERMIT_STATUS[rawStatus] || (rawStatus ? '未知状态' : '未登记'),
    statusTone: resolveStatus({ status: rawStatus }).type,
    expiring: typeof row.remainingDays === 'number' ? row.remainingDays <= 30 : false,
    remainingDaysDisplay: typeof row.remainingDays === 'number' ? `${row.remainingDays} 天` : ''
  })
}
function floating(row) {
  row = row || {}
  const rawStatus = row.status || ''
  return Object.assign({}, row, {
    registrationNoDisplay: display(row.registrationNo), personName: display(row.personName),
    identityNoDisplay: masked(row.identityNo), phoneDisplay: masked(row.phone),
    currentRegionDisplay: display(row.currentRegionCode), currentAddressDisplay: display(row.currentAddress),
    residenceReasonDisplay: RESIDENCE_BASIS[row.residenceReasonCode] || display(row.residenceReasonCode),
    arrivalDateDisplay: formatDate(row.arrivalDate), plannedLeaveDateDisplay: formatDate(row.plannedLeaveDate),
    eligibleFromDateDisplay: formatDate(row.eligibleFromDate), rawStatus,
    statusDisplay: FLOATING_STATUS[rawStatus] || (rawStatus ? '未知状态' : '未登记'),
    statusTone: resolveStatus({ status: rawStatus }).type
  })
}
function migration(row) {
  row = row || {}
  const rawStatus = row.status || ''
  const rawType = row.migrationType || row.direction || ''
  return Object.assign({}, row, {
    personName: display(row.personName), applicationNoDisplay: display(row.applicationNo),
    migrationTypeDisplay: MIGRATION_TYPE[rawType] || (rawType ? '迁移记录' : '未登记'),
    sourceRegionDisplay: display(row.sourceRegionCode), targetRegionDisplay: display(row.targetRegionCode),
    executeDateDisplay: formatDate(row.executeDate), executedAtDisplay: formatDateTime(row.executedAt),
    rawStatus, statusDisplay: MIGRATION_STATUS[rawStatus] || (rawStatus ? '未知状态' : '未登记'),
    statusTone: resolveStatus({ status: rawStatus }).type
  })
}

module.exports = {
  PERMIT_STATUS, FLOATING_STATUS, MIGRATION_STATUS, MIGRATION_TYPE, RESIDENCE_BASIS,
  permit, permitPage: (page) => pageOf(page, permit), floating, floatingPage: (page) => pageOf(page, floating),
  migration, migrationPage: (page) => pageOf(page, migration), masked
}
