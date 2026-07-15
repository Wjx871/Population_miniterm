const display = require('../utils/demographic-display')

function firstValue(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function normalize(row, summary) {
  row = row || {}
  summary = summary || {}
  const rawGender = firstValue(summary.gender, row.gender)
  const rawStatus = firstValue(summary.personStatus, row.currentStatusCode, row.status)
  const rawPersonType = firstValue(summary.personType, row.personType, row.type)
  const idCard = firstValue(summary.maskedIdentityNo, row.idCard)
  const phone = firstValue(summary.maskedPhone, row.phone)
  const address = firstValue(summary.currentAddress, row.currentAddress)

  return Object.assign({}, row, {
    personId: firstValue(summary.personId, row.personId),
    name: display.displayValue(firstValue(summary.name, row.name)),
    rawGender,
    genderDisplay: display.displayEnum(rawGender, display.GENDER, '未登记'),
    birthDateDisplay: display.displayDate(firstValue(summary.birthDate, row.birthDate)),
    ethnicityDisplay: display.displayValue(row.ethnicity),
    idCardDisplay: display.displayValue(idCard),
    phoneDisplay: display.displayValue(phone),
    rawAddress: address,
    addressDisplay: display.displayValue(address),
    rawPersonType,
    personTypeAvailable: rawPersonType !== undefined && rawPersonType !== null && rawPersonType !== '',
    personTypeDisplay: display.displayEnum(rawPersonType, display.PERSON_TYPE, '类型未登记'),
    rawStatus,
    statusDisplay: display.displayEnum(rawStatus, display.PERSON_STATUS, '状态未登记'),
    statusTone: display.statusTone(rawStatus)
  })
}

function normalizeHousehold(row) {
  if (!row || typeof row !== 'object') return null
  const rawStatus = row.status
  return Object.assign({}, row, {
    householdNoDisplay: display.displayValue(row.householdNo),
    headPersonNameDisplay: display.displayValue(row.headPersonName),
    relationshipDisplay: display.displayEnum(row.relationship, display.RELATIONSHIP, '关系未登记'),
    addressDisplay: display.displayValue(row.address),
    regionCodeDisplay: display.displayValue(row.regionCode),
    rawStatus,
    statusDisplay: display.displayEnum(rawStatus, display.HOUSEHOLD_STATUS, '状态未登记'),
    statusTone: display.statusTone(rawStatus)
  })
}

function normalizeResidence(row, fallbackAddress) {
  if (!row && !fallbackAddress) return null
  row = row || {}
  const rawStatus = row.status
  return Object.assign({}, row, {
    addressDisplay: display.displayValue(row.registeredAddress || fallbackAddress),
    regionCodeDisplay: display.displayValue(row.regionCode),
    registerDateDisplay: display.displayDate(row.registerDate || row.startDate),
    rawStatus,
    statusDisplay: display.displayEnum(rawStatus, display.RESIDENCE_STATUS, '状态未登记'),
    statusTone: display.statusTone(rawStatus)
  })
}

function pageTotal(page) {
  const value = page && page.totalElements
  const available = typeof value === 'number' && Number.isFinite(value) && value >= 0
  return { total: available ? value : null, totalAvailable: available, totalDisplay: available ? String(value) : '数量暂不可用' }
}

function normalizePage(page) {
  const total = pageTotal(page)
  return Object.assign({
    records: ((page && page.content) || []).map((row) => normalize(row)),
    number: Number.isInteger(page && page.number) ? page.number : 0,
    last: Boolean(page && page.last)
  }, total)
}

module.exports = { normalize, normalizeHousehold, normalizePage, normalizeResidence, pageTotal }
