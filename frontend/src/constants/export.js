/** 与后端 ExportPolicy 保持一致的受控导出白名单 */

export const EXPORT_MODULES = Object.freeze({
  PERSON: '人口信息',
  HOUSEHOLD: '家庭户',
  MIGRATION: '迁入迁出',
  RESIDENCE_ARCHIVE: '户籍归档',
  CANCELLATION: '注销记录',
  FLOATING_POPULATION: '流动人口',
  RESIDENCE_PERMIT: '居住证',
  OPERATION_LOG: '操作日志'
})

export const EXPORT_MODULE_FIELDS = Object.freeze({
  PERSON: [
    { value: 'name', label: '姓名', sensitive: false },
    { value: 'maskedIdentityNo', label: '脱敏身份证号', sensitive: false },
    { value: 'identityNo', label: '完整身份证号', sensitive: true },
    { value: 'maskedPhone', label: '脱敏手机号', sensitive: false },
    { value: 'phone', label: '完整手机号', sensitive: true },
    { value: 'gender', label: '性别', sensitive: false },
    { value: 'status', label: '状态', sensitive: false },
    { value: 'registeredAddress', label: '登记地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false }
  ],
  HOUSEHOLD: [
    { value: 'householdNo', label: '户号', sensitive: false },
    { value: 'address', label: '地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'status', label: '状态', sensitive: false }
  ],
  MIGRATION: [
    { value: 'applicationNo', label: '申请编号', sensitive: false },
    { value: 'businessType', label: '业务类型', sensitive: false },
    { value: 'status', label: '状态', sensitive: false },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'createdAt', label: '创建时间', sensitive: false }
  ],
  RESIDENCE_ARCHIVE: [
    { value: 'name', label: '姓名', sensitive: false },
    { value: 'maskedIdentityNo', label: '脱敏身份证号', sensitive: false },
    { value: 'identityNo', label: '完整身份证号', sensitive: true },
    { value: 'registeredAddress', label: '登记地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'archiveType', label: '归档类型', sensitive: false },
    { value: 'archivedAt', label: '归档时间', sensitive: false }
  ],
  CANCELLATION: [
    { value: 'cancellationNo', label: '注销编号', sensitive: false },
    { value: 'name', label: '姓名', sensitive: false },
    { value: 'maskedIdentityNo', label: '脱敏身份证号', sensitive: false },
    { value: 'identityNo', label: '完整身份证号', sensitive: true },
    { value: 'registeredAddress', label: '登记地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'status', label: '状态', sensitive: false }
  ],
  FLOATING_POPULATION: [
    { value: 'registrationNo', label: '登记编号', sensitive: false },
    { value: 'name', label: '姓名', sensitive: false },
    { value: 'maskedIdentityNo', label: '脱敏身份证号', sensitive: false },
    { value: 'identityNo', label: '完整身份证号', sensitive: true },
    { value: 'maskedPhone', label: '脱敏手机号', sensitive: false },
    { value: 'phone', label: '完整手机号', sensitive: true },
    { value: 'registeredAddress', label: '登记地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'status', label: '状态', sensitive: false }
  ],
  RESIDENCE_PERMIT: [
    { value: 'maskedPermitNo', label: '脱敏证件号', sensitive: false },
    { value: 'permitNo', label: '完整证件号', sensitive: true },
    { value: 'name', label: '姓名', sensitive: false },
    { value: 'maskedIdentityNo', label: '脱敏身份证号', sensitive: false },
    { value: 'identityNo', label: '完整身份证号', sensitive: true },
    { value: 'maskedPhone', label: '脱敏手机号', sensitive: false },
    { value: 'phone', label: '完整手机号', sensitive: true },
    { value: 'registeredAddress', label: '登记地址', sensitive: true },
    { value: 'regionCode', label: '行政区划', sensitive: false },
    { value: 'status', label: '状态', sensitive: false },
    { value: 'validUntil', label: '有效期至', sensitive: false }
  ],
  OPERATION_LOG: [
    { value: 'operationType', label: '操作类型', sensitive: false },
    { value: 'moduleName', label: '业务模块', sensitive: false },
    { value: 'operationTime', label: '操作时间', sensitive: false },
    { value: 'operationResult', label: '结果', sensitive: false }
  ]
})

export const EXPORT_FILTER_KEYS = Object.freeze([
  'name',
  'regionCode',
  'status',
  'createdFrom',
  'createdTo'
])

export const EXPORT_FILTER_LABELS = Object.freeze({
  name: '姓名',
  regionCode: '行政区划',
  status: '状态',
  createdFrom: '创建开始',
  createdTo: '创建结束'
})

export const SENSITIVE_EXPORT_FIELDS = Object.freeze([
  'identityNo',
  'phone',
  'permitNo',
  'registeredAddress',
  'address'
])

export const EXPORT_TYPE = Object.freeze({
  NORMAL_MASKED: 'NORMAL_MASKED',
  SENSITIVE_APPROVED: 'SENSITIVE_APPROVED'
})

export const EXPORT_TYPE_LABEL = Object.freeze({
  NORMAL_MASKED: '普通脱敏导出',
  SENSITIVE_APPROVED: '敏感批准导出',
  // 兼容展示
  NORMAL: '普通导出',
  SENSITIVE: '敏感导出'
})

export const EXPORT_STATUS = Object.freeze({
  COMPLETED: '已完成',
  FAILED: '失败',
  EXPIRED: '已过期',
  PROCESSING: '处理中'
})

export function getModuleOptions() {
  return Object.entries(EXPORT_MODULES).map(([value, label]) => ({ value, label }))
}

export function getFieldOptions(module, { sensitive = false } = {}) {
  const fields = EXPORT_MODULE_FIELDS[module] || []
  if (sensitive) return fields
  return fields.filter((f) => !f.sensitive)
}

export function getFieldLabel(module, field) {
  const found = (EXPORT_MODULE_FIELDS[module] || []).find((f) => f.value === field)
  return found?.label || field
}

/**
 * 敏感导出材料为可选辅助材料（后端未强制材料类型校验）。
 * 若材料带 requiredFlag，则审批时要求全部 VERIFIED。
 */
export function getSensitiveExportMaterialOptions() {
  return [
    { value: 'EXPORT_JUSTIFICATION', label: '导出依据', required: false },
    { value: 'APPLICANT_IDENTITY_PROOF', label: '申请人身份证明', required: false },
    { value: 'SITUATION_DESCRIPTION', label: '情况说明', required: false }
  ]
}

export function getSensitiveExportMaterialRuleText() {
  return '敏感导出可按需上传导出依据与身份证明；若标记为必需材料，审批前须全部核验通过。'
}

export function hasVerifiedSensitiveExportMaterials(materials = []) {
  const required = (materials || []).filter((m) => m?.requiredFlag)
  if (required.length === 0) return true
  return required.every((m) => m.verifyStatus === 'VERIFIED')
}
