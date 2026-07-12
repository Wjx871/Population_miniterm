export function getApiErrorCode(error) {
  return error?.response?.data?.code ?? error?.response?.status ?? error?.code
}

export function getApiErrorMessage(error, fallback = '操作失败，请稍后重试') {
  return error?.response?.data?.message || error?.message || fallback
}

export function isApiConflict(error) {
  return Number(getApiErrorCode(error)) === 409
}
