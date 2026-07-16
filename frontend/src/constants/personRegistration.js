export const PERSON_REGISTRATION_TYPES = Object.freeze([
  {
    value: 'ID_CARD_ARCHIVE', label: '持居民身份证建档', directCreate: true,
    description: '适用于已持有居民身份证、可直接建立人口基础档案的人员。',
    documents: [{ type: 'ID_CARD_COPY', name: '居民身份证影印本', required: true, ocr: true }]
  },
  {
    value: 'BIRTH_REGISTRATION', label: '出生登记（新生儿）', directCreate: false,
    description: '先创建出生登记申请，由人工审核后再建立人口档案。',
    documents: [
      { type: 'BIRTH_MEDICAL_CERTIFICATE', name: '出生医学证明', required: true },
      { type: 'PARENT_IDENTITY', name: '父母一方身份证明', required: true },
      { type: 'HOUSEHOLD_BOOK', name: '拟落户方居民户口簿', required: true },
      { type: 'MARRIAGE_OR_OTHER_PROOF', name: '结婚证或其他情形证明', required: false }
    ]
  },
  {
    value: 'RELEASED_RESTORE', label: '恢复户口（刑满释放/假释）', directCreate: false,
    description: '适用于符合当地恢复户口条件的人员，材料由人工审核。',
    documents: [
      { type: 'RELEASE_OR_PAROLE_NOTICE', name: '释放证或假释通知书', required: true },
      { type: 'HOUSEHOLD_BOOK', name: '落户方居民户口簿', required: true },
      { type: 'CANCELLATION_PROOF', name: '户口注销证明或相关证明', required: false }
    ]
  },
  {
    value: 'VETERAN_RESTORE', label: '退役军人恢复户口', directCreate: false,
    description: '先提交恢复登记申请，由经办人员按安置和落户材料审核。',
    documents: [
      { type: 'PLACEMENT_INTRODUCTION', name: '安置部门介绍信', required: true },
      { type: 'IDENTITY_PROOF', name: '居民身份证明', required: true },
      { type: 'HOUSEHOLD_OR_PROPERTY_PROOF', name: '居民户口簿或产权证明', required: true }
    ]
  },
  {
    value: 'UNREGISTERED_CONSULTATION', label: '无户口人员登记咨询', directCreate: false,
    description: '用于资料预审和人工咨询，不会直接建立人口档案。',
    documents: [
      { type: 'IDENTITY_OR_BIRTH_PROOF', name: '出生或身份关系证明材料', required: true },
      { type: 'HOUSEHOLD_OR_ADDRESS_PROOF', name: '拟落户地户口簿或住所证明', required: false },
      { type: 'OTHER_SUPPORTING_PROOF', name: '其他佐证材料', required: false }
    ]
  }
])

export const getRegistrationType = (value) => PERSON_REGISTRATION_TYPES.find(item => item.value === value) || PERSON_REGISTRATION_TYPES[0]
