export function getMigrationRecord(detail) {
  return detail?.migrationIn || detail?.migrationOut || null
}

export function isMigrationIn(detail) {
  return Boolean(detail?.migrationIn)
}
