const UNKNOWN_ENUM = /^[A-Z][A-Z0-9_]*$/

const GENDER = Object.freeze({ M: '男', MALE: '男', F: '女', FEMALE: '女', UNKNOWN: '未登记' })
const PERSON_TYPE = Object.freeze({ RESIDENT: '常住人口', REGISTERED: '户籍人口', FLOATING: '流动人口', KEY: '重点人口' })
const PERSON_STATUS = Object.freeze({ NORMAL: '正常', ACTIVE: '正常', CANCELLED: '已注销', DECEASED: '已死亡', MOVED_OUT: '已迁出' })
const HOUSEHOLD_TYPE = Object.freeze({ FAMILY: '家庭户', COLLECTIVE: '集体户', AGRICULTURAL: '农业家庭户', NON_AGRICULTURAL: '非农业家庭户' })
const HOUSEHOLD_STATUS = Object.freeze({ ACTIVE: '正常', NORMAL: '正常', PENDING_CANCELLATION: '待注销', CANCELLED: '已注销', DISABLED: '已停用', ARCHIVED: '已归档' })
const RELATIONSHIP = Object.freeze({ HEAD: '户主', SPOUSE: '配偶', CHILD: '子女', PARENT: '父母', MEMBER: '其他成员', OTHER: '其他成员' })
const MEMBER_STATUS = Object.freeze({ ACTIVE: '正常', NORMAL: '正常', LEFT: '已离户', INACTIVE: '已离户', CANCELLED: '已注销' })
const MARITAL_STATUS = Object.freeze({ SINGLE: '未婚', MARRIED: '已婚', DIVORCED: '离异', WIDOWED: '丧偶' })
const POLITICAL_STATUS = Object.freeze({ CPC_MEMBER: '中共党员', CYL_MEMBER: '共青团员', MASSES: '群众' })
const RESIDENCE_STATUS = Object.freeze({ ACTIVE: '正常居住', NORMAL: '正常居住', MOVED_OUT: '已迁出', CANCELLED: '已注销' })

function normalized(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function displayValue(value, fallback = '未登记') {
  if (value === undefined || value === null) return fallback
  const text = String(value).trim()
  return text ? text : fallback
}

function displayEnum(value, mapping, fallback) {
  const text = normalized(value)
  if (!text) return fallback
  const code = text.toUpperCase()
  if (mapping[code]) return mapping[code]
  return UNKNOWN_ENUM.test(code) ? fallback : text
}

function statusTone(value) {
  const code = normalized(value).toUpperCase()
  if (['NORMAL', 'ACTIVE'].includes(code)) return 'success'
  if (code === 'PENDING_CANCELLATION') return 'warning'
  if (['DECEASED'].includes(code)) return 'danger'
  return 'neutral'
}

function displayDate(value) {
  if (value === undefined || value === null || value === '') return '未登记'
  return String(value).slice(0, 10) || '未登记'
}

module.exports = {
  GENDER,
  HOUSEHOLD_STATUS,
  HOUSEHOLD_TYPE,
  MARITAL_STATUS,
  MEMBER_STATUS,
  PERSON_STATUS,
  PERSON_TYPE,
  POLITICAL_STATUS,
  RELATIONSHIP,
  RESIDENCE_STATUS,
  displayDate,
  displayEnum,
  displayValue,
  statusTone
}
