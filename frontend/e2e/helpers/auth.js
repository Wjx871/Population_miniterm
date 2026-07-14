import { expect } from '../fixtures.js'

export const USERS = Object.freeze({
  viewer: { username: 'viewer', roleCode: 'QUERY_VIEWER', roleLabel: '查询统计人员' },
  population: { username: 'population', roleCode: 'POPULATION_MANAGER', roleLabel: '人口信息管理人员' },
  household: { username: 'household', roleCode: 'HOUSEHOLD_MANAGER', roleLabel: '户籍管理人员' },
  approver: { username: 'approver', roleCode: 'APPROVER', roleLabel: '审批人员' },
  admin: { username: 'admin', roleCode: 'SYSTEM_ADMIN', roleLabel: '系统管理员' },
})

function password() {
  const value = process.env.E2E_PASSWORD
  if (!value) throw new Error('运行浏览器验收前必须设置 E2E_PASSWORD')
  return value
}

export async function resetBrowserState(page) {
  await page.context().clearCookies()
  await page.goto('/login')
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await page.reload()
  await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible()
}

export async function loginAs(page, account) {
  await resetBrowserState(page)
  const loginResponse = page.waitForResponse((response) =>
    response.url().includes('/api/auth/login') && response.request().method() === 'POST')
  await page.getByPlaceholder('系统管理员账号').fill(account.username)
  await page.getByPlaceholder('登录密码').fill(password())
  await page.getByRole('button', { name: /登\s*录/ }).click()
  expect((await loginResponse).status(), `${account.username} 登录接口`).toBe(200)
  await expect(page.locator('.layout-container')).toBeVisible()
  await expect(page.getByText(account.roleLabel, { exact: true })).toBeVisible()
  await assertCurrentUser(page, account)
}

export async function assertCurrentUser(page, account) {
  const result = await page.evaluate(async () => {
    const session = JSON.parse(localStorage.getItem('population_user_v2') || '{}')
    const response = await fetch('/api/auth/me', {
      headers: { Authorization: `${session.tokenType || 'Bearer'} ${session.accessToken || ''}` },
    })
    return { status: response.status, body: await response.json() }
  })
  expect(result.status).toBe(200)
  expect(result.body.data.username).toBe(account.username)
  expect(result.body.data.roleCode).toBe(account.roleCode)
}

export async function refreshAndAssertIdentity(page, account) {
  const meResponse = page.waitForResponse((response) => response.url().includes('/api/auth/me'))
  await page.reload()
  expect((await meResponse).status()).toBe(200)
  await expect(page.locator('.layout-container')).toBeVisible()
  await assertCurrentUser(page, account)
}

export async function expectForbiddenAndSession(page, account, path) {
  await page.goto(path)
  await expect(page).toHaveURL(/\/403(?:\?|$)/)
  await expect(page.getByRole('heading', { name: '无权访问' })).toBeVisible()
  await assertCurrentUser(page, account)
}
