import { test, expect } from './fixtures.js'
import { USERS, assertCurrentUser, loginAs, resetBrowserState } from './helpers/auth.js'

test.describe('认证生命周期', () => {
  test('管理员登录、刷新恢复会话并调用 /auth/me', async ({ page }) => {
    await loginAs(page, USERS.admin)
    const meResponse = page.waitForResponse((response) => response.url().includes('/api/auth/me'))
    await page.reload()
    expect((await meResponse).status()).toBe(200)
    await expect(page.locator('.layout-container')).toBeVisible()
    await assertCurrentUser(page, USERS.admin)
  })

  test('登录失败保持在登录页且不产生会话', async ({ page }) => {
    await resetBrowserState(page)
    const responsePromise = page.waitForResponse((response) => response.url().includes('/api/auth/login'))
    await page.getByPlaceholder('系统管理员账号').fill('admin')
    await page.getByPlaceholder('登录密码').fill('invalid-password')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    expect((await responsePromise).status()).toBe(401)
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.locator('.error-alert')).toBeVisible()
    expect(await page.evaluate(() => localStorage.getItem('population_user_v2'))).toBeNull()
  })

  test('退出登录清理会话并返回登录页', async ({ page }) => {
    await loginAs(page, USERS.admin)
    const logoutResponse = page.waitForResponse((response) => response.url().includes('/api/auth/logout'))
    await page.getByRole('button', { name: /退出/ }).click()
    await page.getByRole('button', { name: '确定退出' }).click()
    expect((await logoutResponse).status()).toBe(200)
    await expect(page).toHaveURL(/\/login$/)
    expect(await page.evaluate(() => localStorage.getItem('population_user_v2'))).toBeNull()
    await page.goto('/persons')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })
})
