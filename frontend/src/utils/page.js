function toFiniteInteger(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.trunc(number) : fallback
}

export function toSpringPageParams(params = {}) {
  const { current, size, ...filters } = params
  const normalizedCurrent = Math.max(1, toFiniteInteger(current, 1))
  const normalizedSize = Math.max(1, toFiniteInteger(size, 10))

  return {
    ...filters,
    page: normalizedCurrent - 1,
    size: normalizedSize,
  }
}

export function normalizePageResult(data) {
  return {
    records: Array.isArray(data?.content) ? data.content : [],
    total: Math.max(0, toFiniteInteger(data?.totalElements, 0)),
    pages: Math.max(0, toFiniteInteger(data?.totalPages, 0)),
    current: Math.max(0, toFiniteInteger(data?.number, 0)) + 1,
    size: Math.max(1, toFiniteInteger(data?.size, 10)),
  }
}
