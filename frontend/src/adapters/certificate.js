export function containsMaskedValue(value) {
  return /[*•●]/.test(String(value ?? ''))
}

export function toCertificateCreatePayload(form) {
  return {
    personId: form.personId,
    certificateType: form.certificateType,
    certificateNo: form.certificateNo?.trim(),
    issueDate: form.issueDate,
    expireDate: form.expireDate || null,
  }
}

export function toCertificateUpdatePayload(form, version) {
  if (containsMaskedValue(form.certificateNo)) {
    throw new Error('禁止提交脱敏证件号')
  }

  return {
    certificateType: form.certificateType,
    certificateNo: form.certificateNo?.trim(),
    issueDate: form.issueDate,
    expireDate: form.expireDate || null,
    version,
  }
}

export function toCertificateCancelPayload(reason, version) {
  return {
    reason: reason?.trim(),
    version,
  }
}
