const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const {
  ALLOWED_SIZES,
  DEFAULT_SIZE,
  ICON_RESOURCE_NAMES,
  ICON_SOURCES,
  resolveIcon
} = require('../utils/icons')
const { TABLER_ASSETS } = require('../utils/icon-assets')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function loadComponent(relativePath) {
  let definition
  global.Component = (value) => { definition = value }
  const target = require.resolve(relativePath)
  delete require.cache[target]
  require(target)
  return definition
}

test('app-icon registers the reviewed semantic names and their intended sources', () => {
  for (const name of ['account', 'lock', 'eye', 'chevron-right', 'population', 'household']) {
    assert.equal(ICON_SOURCES[name], 'tabler-github')
    assert.ok(ICON_RESOURCE_NAMES[name])
  }
})

test('app-icon resolves the six reviewed GitHub resources locally', () => {
  for (const name of ['account', 'lock', 'eye', 'chevron-right', 'population', 'household']) {
    const icon = resolveIcon({ name })
    assert.equal(icon.renderable, true)
    assert.match(icon.src, /^data:image\/svg\+xml;charset=UTF-8,/)
    assert.doesNotMatch(icon.src, /https?:\/\//)
  }
})

test('app-icon resources come from the reviewed Tabler subset only', () => {
  assert.deepEqual(Object.keys(TABLER_ASSETS), [
    'user',
    'lock',
    'eye',
    'eye-off',
    'chevron-right',
    'users',
    'home',
    'database',
    'user-shield',
    'clipboard-check',
    'login-2',
    'logout-2',
    'id',
    'file-description',
    'user-circle',
    'square-check',
    'square',
    'alert-circle',
    'shield-check',
    'building',
    'activity',
    'refresh',
    'layout-dashboard',
    'chevron-down',
    'map-pin'
  ])
  for (const asset of Object.values(TABLER_ASSETS)) {
    assert.match(asset.sha, /^[0-9a-f]{40}$/)
    assert.match(asset.body, /<(path|circle|line|polyline)/)
  }

  assert.deepEqual(
    [...new Set(Object.values(ICON_RESOURCE_NAMES))].sort(),
    Object.keys(TABLER_ASSETS).sort()
  )
})

test('app-icon safely ignores an unknown semantic name', () => {
  assert.deepEqual(resolveIcon({ name: 'not-a-real-icon' }), {
    name: 'not-a-real-icon',
    source: '',
    resourceName: '',
    src: '',
    known: false,
    renderable: false,
    size: DEFAULT_SIZE,
    color: 'currentColor',
    background: false,
    disabled: false
  })
})

test('app-icon uses the approved default and custom size slots', () => {
  assert.equal(resolveIcon({ name: 'eye' }).size, 32)
  for (const size of ALLOWED_SIZES) {
    assert.equal(resolveIcon({ name: 'eye', size }).size, size)
  }
})

test('app-icon falls back safely for an arbitrary size', () => {
  assert.equal(resolveIcon({ name: 'eye', size: 35 }).size, DEFAULT_SIZE)
  assert.equal(resolveIcon({ name: 'eye', size: 'large' }).size, DEFAULT_SIZE)
})

test('app-icon resolves semantic tones and custom colors', () => {
  assert.equal(resolveIcon({ tone: 'primary' }).color, '#1677FF')
  assert.equal(resolveIcon({ tone: 'success' }).color, '#22A06B')
  assert.equal(resolveIcon({ tone: 'warning' }).color, '#F59E0B')
  assert.equal(resolveIcon({ tone: 'danger' }).color, '#E5484D')
  assert.equal(resolveIcon({ color: '#334155', tone: 'primary' }).color, '#334155')
})

test('app-icon disabled state overrides color and preserves background mode', () => {
  const icon = resolveIcon({ tone: 'danger', disabled: true, background: true })
  assert.equal(icon.color, '#98A2B3')
  assert.equal(icon.disabled, true)
  assert.equal(icon.background, true)
})

test('app-icon hides unavailable and unknown resources instead of showing a box', () => {
  const wxml = source('../components/app-icon/index.wxml')
  assert.match(wxml, /wx:if="\{\{renderable\}\}"/)
  assert.doesNotMatch(wxml, /□|�/u)
})

test('app-icon pages never need raw code points or remote font addresses', () => {
  const sources = [
    source('../utils/icons.js'),
    source('../utils/icon-assets.js'),
    source('../components/app-icon/index.js'),
    source('../components/app-icon/index.wxml'),
    source('../components/app-icon/index.wxss')
  ].join('\n')
  const networkSources = sources.replace(/http:\/\/www\.w3\.org\/2000\/svg/g, '')

  assert.doesNotMatch(networkSources, /iconfont\.cn|at\.alicdn\.com|https?:\/\//i)
  assert.doesNotMatch(sources, /\\e[0-9a-f]{3,}|&#x?[0-9a-f]+;/i)
  assert.doesNotMatch(sources, /setInterval|setTimeout/)
})

test('app-icon is registered once as a global component', () => {
  const app = JSON.parse(source('../app.json'))
  assert.equal(app.usingComponents['app-icon'], '/components/app-icon/index')
})

test('login and dashboard visual icon semantics all resolve to reviewed resources', () => {
  const names = [
    'brand',
    'account',
    'lock',
    'eye',
    'eye-off',
    'checkbox',
    'checkbox-checked',
    'error-circle',
    'shield-check',
    'user',
    'department',
    'health',
    'refresh',
    'population-count',
    'household-count',
    'pending-approval',
    'migration-in',
    'migration-out',
    'residence-permit',
    'population',
    'household',
    'application',
    'approval',
    'profile',
    'dashboard'
  ]

  for (const name of names) assert.equal(resolveIcon({ name }).renderable, true, name)
})

test('app-icon exposes the required public properties', () => {
  const definition = loadComponent('../components/app-icon/index')
  assert.deepEqual(Object.keys(definition.properties), [
    'name',
    'size',
    'color',
    'tone',
    'background',
    'disabled',
    'customClass'
  ])
})

test('app-icon observer applies a reviewed local resource without timers', () => {
  const definition = loadComponent('../components/app-icon/index')
  let data
  definition.observers['name,size,color,tone,background,disabled'].call(
    { setData: (value) => { data = value } },
    'population',
    48,
    '',
    'primary',
    true,
    false
  )

  assert.equal(data.renderable, true)
  assert.match(data.iconSrc, /^data:image\/svg\+xml;charset=UTF-8,/)
  assert.equal(data.displaySize, 48)
  assert.equal(data.displayColor, '#1677FF')
  assert.equal(data.displayBackground, true)
})
