import { normalizePageResult } from '../utils/page'

export function normalizeApplicationPage(data) {
  const page = normalizePageResult(data)
  return {
    ...page,
    records: page.records.filter((item) => item?.applicationId != null),
  }
}

export function isDraft(application) {
  return application?.status === 'DRAFT'
}
