import { normalizePageResult } from '../utils/page.js'

const text = (value) => value === null || value === undefined ? '' : String(value).trim()

function pageOf(data, normalize) {
  const page = normalizePageResult(data)
  return { ...page, records: page.records.map(normalize) }
}

export function normalizeHouseholdQueryPage(data) {
  return pageOf(data, (raw = {}) => ({
    householdId: raw.householdId ?? null,
    householdNo: text(raw.householdNo),
    headPersonName: text(raw.headPersonName),
    address: text(raw.address),
    regionCode: text(raw.regionCode),
    householdType: text(raw.householdType),
    status: text(raw.status),
    memberCount: Number.isFinite(Number(raw.memberCount)) ? Number(raw.memberCount) : null,
    containsKeyPopulation: raw.containsKeyPopulation ?? null,
  }))
}

export function normalizeMigrationQueryPage(data) {
  return pageOf(data, (raw = {}) => ({
    direction: text(raw.direction), migrationId: raw.migrationId ?? null, personId: raw.personId ?? null,
    personName: text(raw.personName), migrationType: text(raw.migrationType),
    sourceRegionCode: text(raw.sourceRegionCode), targetRegionCode: text(raw.targetRegionCode),
    status: text(raw.status), executeDate: raw.executeDate ?? null, executedAt: raw.executedAt ?? null,
    applicationNo: text(raw.applicationNo),
  }))
}

export function normalizeLogPage(data) {
  return pageOf(data, (raw = {}) => ({
    logId: raw.logId ?? null, username: text(raw.username), operationType: text(raw.operationType),
    moduleName: text(raw.moduleName), requestPath: text(raw.requestPath), requestMethod: text(raw.requestMethod),
    operationTime: raw.operationTime ?? null, ipAddress: text(raw.ipAddress),
    operationResult: text(raw.operationResult), errorMessage: text(raw.errorMessage), detail: text(raw.detail),
  }))
}
