import { test as base, expect } from '@playwright/test'

const BLOCKED_WARNINGS = [
  'Component provided template option but runtime compilation is not supported',
  '[el-radio] [API] label act as value is about to be deprecated',
]

export const test = base.extend({
  page: async ({ page }, use, testInfo) => {
    const serverErrors = []
    const consoleErrors = []
    const pageErrors = []
    const blockedWarnings = []

    page.on('response', (response) => {
      if (response.status() >= 500) {
        serverErrors.push(`${response.status()} ${response.request().method()} ${response.url()} (page: ${page.url()})`)
      }
    })
    page.on('console', (message) => {
      const text = message.text()
      const isExpectedHttpFailure = /status of (401|403|404|409)\b/.test(text)
      if (message.type() === 'error' && !isExpectedHttpFailure) consoleErrors.push(text)
      if (message.type() === 'warning' && BLOCKED_WARNINGS.some((warning) => text.includes(warning))) blockedWarnings.push(text)
    })
    page.on('pageerror', (error) => pageErrors.push(error.stack || error.message))

    await use(page)

    if (blockedWarnings.length > 0) {
      await testInfo.attach('blocked-acceptance-warnings', {
        body: Buffer.from(blockedWarnings.join('\n')),
        contentType: 'text/plain',
      })
    }
    expect(serverErrors, '页面请求不应出现 HTTP 500').toEqual([])
    expect(blockedWarnings, '本轮已知 Vue/Element Plus warning 必须消失').toEqual([])
    expect(consoleErrors, '浏览器控制台不应出现 error').toEqual([])
    expect(pageErrors, '页面不应出现未捕获运行时异常').toEqual([])
  },
})

export { expect }
