function readFilename(disposition) {
  if (!disposition) return ''
  const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8) return decodeURIComponent(utf8[1])
  const plain = disposition.match(/filename="?([^";]+)"?/i)
  return plain ? plain[1] : ''
}

export async function downloadBlob(response, fallbackFilename = 'download') {
  const filename = readFilename(response.headers?.['content-disposition']) || fallbackFilename
  const contentType = response.headers?.['content-type'] || 'application/octet-stream'
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: contentType })
  if (contentType.toLowerCase().includes('application/json')) {
    const text = await blob.text()
    let payload = null
    try { payload = JSON.parse(text) } catch { /* keep a safe generic error below */ }
    const error = new Error(payload?.message || '下载请求未返回文件')
    error.code = payload?.code
    throw error
  }
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export async function normalizeBlobError(error) {
  const response = error?.response
  const contentType = response?.headers?.['content-type'] || ''
  if (!response?.data || !contentType.toLowerCase().includes('application/json') || !(response.data instanceof Blob)) {
    return error
  }

  try {
    const payload = JSON.parse(await response.data.text())
    response.data = payload
    error.message = payload?.message || error.message
  } catch {
    // Keep the original transport error when the body is not valid JSON.
  }
  return error
}
