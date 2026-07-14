import { expect } from '../fixtures.js'

export async function expectHealthyPage(page, path, heading) {
  await page.goto(path)
  await expect.poll(() => new URL(page.url()).pathname).toBe(path)
  await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
  await expect(page.locator('.page-wrapper')).not.toBeEmpty()
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)

  const visibleText = await page.locator('body').innerText()
  expect(visibleText, `${path} 不应显示 undefined`).not.toMatch(/\bundefined\b/i)
  expect(visibleText, `${path} 不应显示对象字符串`).not.toContain('[object Object]')
  expect(visibleText, `${path} 不应泄漏 Vue 模板表达式`).not.toContain('{{')
}
