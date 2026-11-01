const { resolveIcon } = require('../../utils/icons')

function isDevelopment() {
  try {
    return typeof wx !== 'undefined' &&
      typeof wx.getAccountInfoSync === 'function' &&
      wx.getAccountInfoSync().miniProgram.envVersion === 'develop'
  } catch (error) {
    return false
  }
}

function updateIcon(name, size, color, tone, background, disabled) {
  const resolved = resolveIcon({ name, size, color, tone, background, disabled })

  if (name && !resolved.known && isDevelopment() && typeof console !== 'undefined' && console.warn) {
    console.warn(`[app-icon] Unknown semantic icon: ${name}`)
  }

  this.setData({
    iconSrc: resolved.src,
    renderable: resolved.renderable,
    source: resolved.source,
    displaySize: resolved.size,
    displayColor: resolved.color,
    displayBackground: resolved.background,
    displayDisabled: resolved.disabled
  })
}

Component({
  properties: {
    name: { type: String, value: '' },
    size: { type: Number, value: 32 },
    color: { type: String, value: '' },
    tone: { type: String, value: 'default' },
    background: { type: Boolean, value: false },
    disabled: { type: Boolean, value: false },
    customClass: { type: String, value: '' }
  },
  data: {
    iconSrc: '',
    renderable: false,
    source: '',
    displaySize: 32,
    displayColor: 'currentColor',
    displayBackground: false,
    displayDisabled: false
  },
  observers: {
    'name,size,color,tone,background,disabled': updateIcon
  }
})
