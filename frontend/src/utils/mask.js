function toSafeString(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function stars(count) {
  return '*'.repeat(Math.max(0, count))
}

/**
 * 身份证号脱敏。
 * 18 位：前 6 + 8 星 + 后 4
 * 其他长度：足够时前 3 + 星 + 后 2；过短则全星号
 * 空值返回 '-'
 */
export function maskIdCard(value) {
  const text = toSafeString(value)
  if (!text) return '-'

  if (text.length === 18) {
    return `${text.slice(0, 6)}${stars(8)}${text.slice(-4)}`
  }

  if (text.length <= 4) {
    return stars(text.length)
  }

  if (text.length <= 5) {
    return `${text.slice(0, 1)}${stars(text.length - 2)}${text.slice(-1)}`
  }

  return `${text.slice(0, 3)}${stars(text.length - 5)}${text.slice(-2)}`
}

/**
 * 手机号脱敏。
 * 11 位：前 3 + 4 星 + 后 4
 * 其他长度：足够时前 2 + 星 + 后 2；过短则全星号
 * 空值返回 '-'
 */
export function maskPhone(value) {
  const text = toSafeString(value)
  if (!text) return '-'

  if (text.length === 11) {
    return `${text.slice(0, 3)}${stars(4)}${text.slice(-4)}`
  }

  if (text.length <= 4) {
    return stars(text.length)
  }

  return `${text.slice(0, 2)}${stars(text.length - 4)}${text.slice(-2)}`
}

/**
 * 通用文本脱敏。
 * options: { keepStart, keepEnd, maskChar }
 */
export function maskText(value, options = {}) {
  const text = toSafeString(value)
  if (!text) return '-'

  const keepStart = Number.isFinite(options.keepStart) ? Math.max(0, options.keepStart) : 1
  const keepEnd = Number.isFinite(options.keepEnd) ? Math.max(0, options.keepEnd) : 1
  const maskChar = options.maskChar || '*'

  if (text.length <= keepStart + keepEnd) {
    return maskChar.repeat(text.length)
  }

  const middle = maskChar.repeat(text.length - keepStart - keepEnd)
  return `${text.slice(0, keepStart)}${middle}${text.slice(text.length - keepEnd)}`
}

/**
 * 居住证编号脱敏。
 * 前 3 + 星 + 后 3
 * 空值返回 '-'
 */
export function maskPermitNo(value) {
  const text = toSafeString(value)
  if (!text) return '-'
  if (text.length <= 6) return stars(text.length)
  return `${text.slice(0, 3)}${stars(text.length - 6)}${text.slice(-3)}`
}
