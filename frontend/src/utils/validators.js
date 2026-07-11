export function isPhone(value) {
  return /^1[3-9]\d{9}$/.test(value)
}

/**
 * 18 位身份证格式 + 出生日期段 + 校验位。
 * 15 位旧证仅做宽松数字校验。
 */
export function isIdCard(value) {
  if (!value) return false
  const text = String(value).trim().toUpperCase()

  if (/^\d{15}$/.test(text)) {
    return true
  }

  if (!/^\d{17}[\dX]$/.test(text)) {
    return false
  }

  const year = Number(text.slice(6, 10))
  const month = Number(text.slice(10, 12))
  const day = Number(text.slice(12, 14))
  const birth = new Date(year, month - 1, day)
  if (
    birth.getFullYear() !== year
    || birth.getMonth() + 1 !== month
    || birth.getDate() !== day
  ) {
    return false
  }

  // 校验位
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const codes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
  let sum = 0
  for (let i = 0; i < 17; i += 1) {
    sum += Number(text[i]) * weights[i]
  }
  return codes[sum % 11] === text[17]
}

export function validatePhone(rule, value, callback) {
  if (!value || isPhone(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的手机号'))
  }
}

export function validateIdCard(rule, value, callback) {
  if (!value) {
    callback(new Error('请输入身份证号'))
    return
  }
  if (isIdCard(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的身份证号'))
  }
}

export function validateBirthDate(rule, value, callback) {
  if (!value) {
    callback(new Error('请选择出生日期'))
    return
  }
  const date = new Date(value)
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  if (Number.isNaN(date.getTime()) || date > today) {
    callback(new Error('出生日期不得晚于今天'))
    return
  }
  callback()
}
