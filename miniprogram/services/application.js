const { request, downloadFile } = require('./request')
function list(params) { return request({ url: '/api/applications', data: params }) }
function detail(id) { return request({ url: `/api/applications/${id}` }) }
function logs(id) { return request({ url: `/api/applications/${id}/approval-logs` }) }
function materials(id) { return request({ url: `/api/applications/${id}/materials` }) }
function materialFile(id) { return downloadFile({ url: `/api/materials/${id}/download` }) }
const PROFESSIONAL = {
  MIGRATION_IN: ['/api/migrations/applications/', 'migration:view'], MIGRATION_OUT: ['/api/migrations/applications/', 'migration:view'],
  PERSON_CANCELLATION: ['/api/cancellations/applications/', 'cancellation:view'], HOUSEHOLD_CANCELLATION: ['/api/cancellations/applications/', 'cancellation:view'],
  FLOATING_REGISTRATION: ['/api/floating-registrations/applications/', 'floating:view'],
  RESIDENCE_PERMIT_FIRST_ISSUE: ['/api/residence-permits/applications/', 'residence-permit:view'],
  RESIDENCE_PERMIT_ENDORSEMENT: ['/api/residence-permits/applications/', 'residence-permit:view'],
  RESIDENCE_PERMIT_CANCELLATION: ['/api/residence-permits/applications/', 'residence-permit:view'],
  KEY_POPULATION_REGISTER: ['/api/key-populations/register-applications/', 'key-population:view'],
  KEY_POPULATION_RELEASE: ['/api/key-populations/release-applications/', 'key-population:view'],
  SENSITIVE_DATA_EXPORT: ['/api/exports/applications/', 'approval:view']
}
function professional(id, businessType, permissions) {
  const config = PROFESSIONAL[businessType]
  if (!config || !permissions.includes(config[1])) return Promise.resolve(null)
  return request({ url: `${config[0]}${id}` })
}
module.exports = { list, detail, logs, materials, materialFile, professional, PROFESSIONAL }
