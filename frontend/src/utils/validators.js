export function isPhone(value) {
  return /^1[3-9]\d{9}$/.test(value)
}

export function isIdCard(value) {
  return /^\d{17}[\dXx]$/.test(value)
}

export function validatePhone(rule, value, callback) {
  if (!value || isPhone(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的手机号'))
  }
}

export function validateIdCard(rule, value, callback) {
  if (!value || isIdCard(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的身份证号'))
  }
}
