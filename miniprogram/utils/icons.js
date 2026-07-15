const { createIconDataUri } = require('./icon-assets')

const DEFAULT_SIZE = 32

const ALLOWED_SIZES = Object.freeze([24, 28, 32, 40, 48, 52, 56, 80, 96])

const TONE_COLORS = Object.freeze({
  default: 'currentColor',
  primary: '#1677FF',
  deep: '#123B7A',
  success: '#22A06B',
  warning: '#F59E0B',
  danger: '#E5484D',
  muted: '#98A2B3'
})

const SEMANTIC_ICON_NAMES = [
  'brand',
  'back',
  'close',
  'search',
  'chevron-right',
  'chevron-down',
  'chevron-up',
  'eye',
  'eye-off',
  'refresh',
  'check',
  'check-circle',
  'warning-circle',
  'error-circle',
  'info-circle',
  'checkbox',
  'checkbox-checked'
]

const BUSINESS_ICON_NAMES = [
  'account',
  'lock',
  'shield-check',
  'logout',
  'user',
  'role',
  'department',
  'permission',
  'health',
  'dashboard',
  'population',
  'population-count',
  'person',
  'gender',
  'phone',
  'calendar',
  'id-card',
  'household',
  'household-count',
  'householder',
  'family',
  'relationship',
  'location',
  'region',
  'archive',
  'application',
  'approval',
  'pending-approval',
  'approve',
  'reject',
  'comment',
  'history',
  'material',
  'view-file',
  'migration-in',
  'migration-out',
  'residence-permit',
  'statistics',
  'database',
  'server',
  'security',
  'profile',
  'network-error',
  'unauthorized',
  'forbidden',
  'not-found',
  'conflict',
  'server-error',
  'empty'
]

const ICON_SOURCES = Object.freeze(
  SEMANTIC_ICON_NAMES.concat(BUSINESS_ICON_NAMES).reduce((sources, name) => {
    sources[name] = 'tabler-github'
    return sources
  }, {})
)

const ICON_RESOURCE_NAMES = Object.freeze({
  brand: 'database',
  account: 'user',
  lock: 'lock',
  eye: 'eye',
  'eye-off': 'eye-off',
  'chevron-right': 'chevron-right',
  'chevron-down': 'chevron-down',
  population: 'users',
  person: 'user',
  household: 'home',
  checkbox: 'square',
  'checkbox-checked': 'square-check',
  'error-circle': 'alert-circle',
  'shield-check': 'shield-check',
  user: 'user-shield',
  department: 'building',
  health: 'activity',
  dashboard: 'layout-dashboard',
  database: 'database',
  'population-count': 'users',
  'household-count': 'home',
  'pending-approval': 'clipboard-check',
  'migration-in': 'login-2',
  'migration-out': 'logout-2',
  'residence-permit': 'id',
  location: 'map-pin',
  archive: 'archive',
  application: 'file-description',
  approval: 'clipboard-check',
  profile: 'user-circle',
  refresh: 'refresh'
})

function normalizeSize(size) {
  const parsed = Number(size)
  return ALLOWED_SIZES.includes(parsed) ? parsed : DEFAULT_SIZE
}

function resolveColor({ color, tone, disabled }) {
  if (disabled) return TONE_COLORS.muted
  if (typeof color === 'string' && color.trim()) return color.trim()
  return TONE_COLORS[tone] || TONE_COLORS.default
}

function resolveIcon(options = {}) {
  const name = typeof options.name === 'string' ? options.name.trim() : ''
  const source = ICON_SOURCES[name] || ''
  const resourceName = ICON_RESOURCE_NAMES[name] || ''
  const src = createIconDataUri(resourceName)

  return {
    name,
    source,
    resourceName,
    src,
    known: Boolean(source),
    renderable: Boolean(source && src),
    size: normalizeSize(options.size),
    color: resolveColor(options),
    background: Boolean(options.background),
    disabled: Boolean(options.disabled)
  }
}

module.exports = {
  ALLOWED_SIZES,
  DEFAULT_SIZE,
  ICON_RESOURCE_NAMES,
  ICON_SOURCES,
  TONE_COLORS,
  normalizeSize,
  resolveIcon
}
