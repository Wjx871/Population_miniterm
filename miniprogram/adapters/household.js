const { formatDate } = require('../utils/date')
const STATUS = { ACTIVE: '有效', PENDING_CANCELLATION: '待注销', ARCHIVED: '已归档', CANCELLED: '已注销', '正常': '有效', '有效': '有效' }
function member(row) { row = row || {}; return Object.assign({}, row, { personName: row.personName || '—', relationshipDisplay: row.relationship || '—', idCardDisplay: row.idCard || '—', phoneDisplay: row.phone || '—', statusDisplay: STATUS[row.status] || row.status || '—' }) }
function normalize(row) {
  row = row || {}
  return Object.assign({}, row, { householdNo: row.householdNo || '—', headPersonName: row.headPersonName || '—', addressDisplay: row.address || '—', establishDateDisplay: formatDate(row.establishDate), statusDisplay: STATUS[row.status] || row.status || '—', members: (row.members || []).map(member) })
}
function normalizePage(page) { return { records: (page && page.content || []).map(normalize), total: Number(page && page.totalElements || 0), number: Number(page && page.number || 0), last: Boolean(page && page.last) } }
module.exports = { STATUS, normalize, normalizePage, member }
