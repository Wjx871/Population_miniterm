import { normalizePageResult } from '../utils/page.js'

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim()
}

function nullable(value) {
  return value === undefined ? null : value
}

export function normalizeComprehensivePerson(raw = {}) {
  return {
    personId: nullable(raw.personId),
    name: text(raw.name),
    gender: text(raw.gender),
    birthDate: nullable(raw.birthDate),
    maskedIdentityNo: text(raw.maskedIdentityNo),
    maskedPhone: text(raw.maskedPhone),
    personStatus: text(raw.personStatus),
    householdId: nullable(raw.householdId),
    householdNo: text(raw.householdNo),
    headPersonName: text(raw.headPersonName),
    relationship: text(raw.relationship),
    householdHead: nullable(raw.householdHead),
    currentRegionCode: text(raw.currentRegionCode),
    currentRegionName: nullable(raw.currentRegionName),
    currentAddress: nullable(raw.currentAddress),
    residenceStatus: text(raw.residenceStatus),
    floatingId: nullable(raw.floatingId),
    floatingStatus: text(raw.floatingStatus),
    arrivalDate: nullable(raw.arrivalDate),
    permitId: nullable(raw.permitId),
    maskedPermitNo: text(raw.maskedPermitNo),
    permitStatus: text(raw.permitStatus),
    permitValidUntil: nullable(raw.permitValidUntil),
    lastMigrationDirection: text(raw.lastMigrationDirection),
    lastMigrationDate: nullable(raw.lastMigrationDate),
  }
}

export function normalizeComprehensivePage(data) {
  const page = normalizePageResult(data)
  return { ...page, records: page.records.map(normalizeComprehensivePerson) }
}

function normalizeObject(value) {
  return value && typeof value === 'object' ? value : null
}

export function normalizeComprehensiveProfile(raw = {}) {
  const history = Array.isArray(raw?.migrationHistory) ? raw.migrationHistory : []
  return {
    person: normalizeComprehensivePerson(raw?.person || {}),
    currentHousehold: normalizeObject(raw?.currentHousehold),
    currentResidence: normalizeObject(raw?.currentResidence),
    activeFloating: normalizeObject(raw?.activeFloating),
    currentPermit: normalizeObject(raw?.currentPermit),
    migrationHistory: history,
  }
}
