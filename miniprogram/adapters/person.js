const { formatDate } = require('../utils/date')
const STATUS = { NORMAL: '正常', ACTIVE: '正常', CANCELLED: '已注销', DECEASED: '已死亡', MOVED_OUT: '已迁出', '正常': '正常' }
function normalize(row) {
  row = row || {}
  return Object.assign({}, row, {
    personId: row.personId,
    name: row.name || '—', genderDisplay: row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : row.gender || '—',
    birthDateDisplay: formatDate(row.birthDate), ethnicityDisplay: row.ethnicity || '—',
    idCardDisplay: row.idCard || '—', phoneDisplay: row.phone || '—', addressDisplay: row.currentAddress || '—',
    statusDisplay: STATUS[row.currentStatusCode] || STATUS[row.status] || row.status || '—'
  })
}
function normalizePage(page) { return { records: (page && page.content || []).map(normalize), total: Number(page && page.totalElements || 0), number: Number(page && page.number || 0), last: Boolean(page && page.last) } }
module.exports = { STATUS, normalize, normalizePage }
