import dayjs from 'dayjs'

export function formatDate(value) {
  return value ? dayjs(value).format('YYYY-MM-DD') : ''
}

export function formatDateTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : ''
}

export function toDatePayload(value) {
  return value ? dayjs(value).format('YYYY-MM-DD') : null
}
