import test from 'node:test'
import assert from 'node:assert/strict'

import { buildExportDownloadConfig } from '../src/services/fileDownload.js'
import { toNormalExportPayload } from '../src/adapters/export.js'

test('下载配置必须 blob + rawResponse，且路径为鉴权 API', () => {
  const config = buildExportDownloadConfig(42)
  assert.equal(config.responseType, 'blob')
  assert.equal(config.rawResponse, true)
  assert.equal(config.method, 'get')
  assert.equal(config.url, '/exports/42/download')
  assert.ok(!String(config.url).includes('storage_path'))
  assert.ok(!String(config.url).includes('/static/'))
  assert.ok(!String(config.url).includes('file://'))
})

test('普通导出流程语义：POST 创建记录再 download，而非 POST 直接 blob', () => {
  // 普通导出创建 payload 不是文件流请求
  const createPayload = toNormalExportPayload({
    module: 'PERSON',
    fields: ['name'],
    filters: {}
  })
  assert.equal(typeof createPayload.module, 'string')
  assert.ok(Array.isArray(createPayload.fields))

  // 下载是独立 GET
  const download = buildExportDownloadConfig(99)
  assert.equal(download.url, '/exports/99/download')
  assert.notEqual(download.method, 'post')
})

test('下载配置禁止 window.open / 静态目录拼装语义', () => {
  const config = buildExportDownloadConfig(1)
  // 契约：必须走 request 封装字段，而不是 href
  assert.ok(config.url.startsWith('/exports/'))
  assert.equal('href' in config, false)
  assert.equal('windowOpen' in config, false)
})
