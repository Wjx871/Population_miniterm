import request from './request'

export function getSystemHealth() {
  return request({ url: '/system/health', method: 'get' })
}

export function getPersonsStatistics() {
  return request({ url: '/persons/statistics', method: 'get' })
}

export function getMigrationsInStatistics() {
  return request({ url: '/migrations/in/statistics', method: 'get' })
}

export function getCertificatesExpireSoon() {
  return request({ url: '/certificates/expire-soon', method: 'get' })
}
