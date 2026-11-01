const display = require('../utils/demographic-display')

function memberCount(value) {
  const available = typeof value === 'number' && Number.isFinite(value) && Number.isInteger(value) && value >= 0
  return {
    activeMemberCount: available ? value : null,
    activeMemberCountAvailable: available,
    activeMemberCountDisplay: available ? `${value} 人` : '成员数暂不可用'
  }
}

function member(row) {
  row = row || {}
  const rawStatus = row.status
  const rawRelationship = row.relationship
  const rawGender = row.gender
  return Object.assign({}, row, {
    personName: display.displayValue(row.personName),
    rawRelationship,
    relationshipDisplay: display.displayEnum(rawRelationship, display.RELATIONSHIP, '关系未登记'),
    householdHead: String(rawRelationship || '').toUpperCase() === 'HEAD',
    rawGender,
    genderAvailable: rawGender !== undefined && rawGender !== null && rawGender !== '',
    genderDisplay: display.displayEnum(rawGender, display.GENDER, '未登记'),
    idCardDisplay: display.displayValue(row.idCard),
    phoneDisplay: display.displayValue(row.phone),
    rawStatus,
    statusDisplay: display.displayEnum(rawStatus, display.MEMBER_STATUS, '状态未登记'),
    statusTone: display.statusTone(rawStatus)
  })
}

function normalize(row) {
  row = row || {}
  const rawStatus = row.status
  const rawHouseholdType = row.householdType
  return Object.assign({}, row, memberCount(row.activeMemberCount), {
    householdNo: display.displayValue(row.householdNo),
    headPersonName: display.displayValue(row.headPersonName),
    addressDisplay: display.displayValue(row.address),
    regionCodeDisplay: display.displayValue(row.regionCode),
    establishDateDisplay: display.displayDate(row.establishDate),
    rawHouseholdType,
    householdTypeDisplay: display.displayEnum(rawHouseholdType, display.HOUSEHOLD_TYPE, '类型未登记'),
    rawStatus,
    statusDisplay: display.displayEnum(rawStatus, display.HOUSEHOLD_STATUS, '状态未登记'),
    statusTone: display.statusTone(rawStatus),
    members: (Array.isArray(row.members) ? row.members : []).map(member)
  })
}

function pageTotal(page) {
  const value = page && page.totalElements
  const available = typeof value === 'number' && Number.isFinite(value) && value >= 0
  return { total: available ? value : null, totalAvailable: available, totalDisplay: available ? String(value) : '数量暂不可用' }
}

function normalizePage(page) {
  return Object.assign({
    records: ((page && page.content) || []).map(normalize),
    number: Number.isInteger(page && page.number) ? page.number : 0,
    last: Boolean(page && page.last)
  }, pageTotal(page))
}

module.exports = { member, memberCount, normalize, normalizePage, pageTotal }
