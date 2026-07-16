const SEMANTICS = ['info', 'success', 'warning', 'danger', 'neutral']

const STATUS_MAP = Object.freeze({
  ACTIVE: { text: '有效', type: 'success' },
  NORMAL: { text: '正常', type: 'success' },
  VALID: { text: '有效', type: 'success' },
  APPROVED: { text: '已通过', type: 'success' },
  COMPLETED: { text: '已办结', type: 'success' },
  EXECUTED: { text: '已执行', type: 'success' },
  VERIFIED: { text: '已核验', type: 'success' },
  PENDING: { text: '待处理', type: 'warning' },
  UNDER_REVIEW: { text: '审批中', type: 'warning' },
  PENDING_APPROVAL: { text: '待审批', type: 'warning' },
  PENDING_CANCELLATION: { text: '待注销', type: 'warning' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REJECTED: { text: '已驳回', type: 'danger' },
  FAILED: { text: '失败', type: 'danger' },
  CANCELLED: { text: '已注销', type: 'neutral' },
  DISABLED: { text: '已禁用', type: 'neutral' }
})

const TEXT_TYPES = Object.freeze({
  有效: 'success',
  正常: 'success',
  已通过: 'success',
  已办结: 'success',
  已完成: 'success',
  已执行: 'success',
  已核验: 'success',
  待处理: 'warning',
  待审批: 'warning',
  审批中: 'warning',
  待注销: 'warning',
  待核验: 'warning',
  已提交: 'warning',
  已驳回: 'danger',
  核验不通过: 'danger',
  失败: 'danger',
  已死亡: 'danger',
  草稿: 'neutral',
  已注销: 'neutral',
  已取消: 'neutral',
  已撤回: 'neutral',
  已禁用: 'neutral',
  已归档: 'neutral'
})

function normalizedCode(value) {
  return typeof value === 'string' ? value.trim().toUpperCase() : ''
}

function isEnumCode(value) {
  return /^[A-Z][A-Z0-9_]*$/.test(value)
}

function resolveStatus({ text = '', status = '', type = '', size = '' } = {}) {
  const statusCode = normalizedCode(status)
  const textCode = normalizedCode(text)
  const mapped = STATUS_MAP[statusCode] || STATUS_MAP[textCode]
  const explicitText = typeof text === 'string' ? text.trim() : ''
  const displayText = explicitText && !isEnumCode(textCode)
    ? explicitText
    : mapped
      ? mapped.text
      : isEnumCode(statusCode || textCode)
        ? '未知状态'
        : explicitText || '—'
  const explicitType = SEMANTICS.includes(type) ? type : ''
  return {
    text: displayText,
    type: explicitType || (mapped && mapped.type) || TEXT_TYPES[displayText] || 'info',
    size: size === 'small' ? 'small' : 'medium'
  }
}

module.exports = { SEMANTICS, STATUS_MAP, TEXT_TYPES, resolveStatus }
