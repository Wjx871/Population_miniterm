import { downloadBlob, normalizeBlobError } from '../utils/download.js'

/**
 * 鉴权下载导出文件。
 * 强制 responseType=blob + rawResponse=true，禁止拼接 storage_path / 静态 URL。
 */
export async function downloadExportById(exportId, fallbackFilename = 'export.xlsx') {
  // 动态导入，避免 Node 单测加载 Vite 风格 API 模块图
  const { downloadExportFile } = await import('../api/exports.js')
  try {
    const response = await downloadExportFile(exportId)
    await downloadBlob(response, fallbackFilename)
    return response
  } catch (error) {
    const normalized = await normalizeBlobError(error)
    const status = normalized?.response?.status
    if (status === 410) {
      const err = new Error(normalized?.response?.data?.message || normalized?.message || '文件已过期或已清理')
      err.code = 410
      err.status = 410
      throw err
    }
    if (status === 403) {
      const err = new Error(normalized?.response?.data?.message || '无权下载该导出文件')
      err.code = 403
      err.status = 403
      throw err
    }
    if (status === 409) {
      const err = new Error(normalized?.response?.data?.message || '导出状态冲突，无法下载')
      err.code = 409
      err.status = 409
      throw err
    }
    throw normalized
  }
}

/** 供契约测试断言下载请求配置 */
export function buildExportDownloadConfig(exportId) {
  return {
    url: `/exports/${exportId}/download`,
    method: 'get',
    responseType: 'blob',
    rawResponse: true
  }
}
