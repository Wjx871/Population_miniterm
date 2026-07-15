import { test, expect } from './fixtures.js'
import { USERS, loginAs } from './helpers/auth.js'
import { expectHealthyPage } from './helpers/page.js'

test.describe('人工验收问题回归', () => {
  test.beforeEach(async ({ page }) => loginAs(page, USERS.admin))

  test('审批 Tab 可渲染切换且无 runtime template warning', async ({ page }) => {
    await expectHealthyPage(page, '/approvals', '审批中心')
    await page.getByRole('tab', { name: '已办审批' }).click()
    await expect(page.getByRole('tab', { name: '已办审批' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.locator('.el-table:visible')).toHaveCount(1)
  })

  test('家庭户待注销状态中文完整显示', async ({ page }) => {
    await expectHealthyPage(page, '/households', '户籍管理')
    const status = page.getByText('待注销', { exact: true }).first()
    await expect(status).toBeVisible()
    const box = await status.boundingBox()
    expect(box?.width || 0).toBeGreaterThan(30)
  })

  test('迁入长标签保持单行', async ({ page }) => {
    await expectHealthyPage(page, '/migrations/in/apply', '迁入申请')
    for (const text of ['迁出地行政区划', '迁入地行政区划', '目标家庭户']) {
      const label = page.locator('.el-form-item__label', { hasText: text })
      await expect(label).toBeVisible()
      expect(await label.evaluate((node) => getComputedStyle(node).whiteSpace)).toBe('nowrap')
    }
  })

  test('迁入可按区划搜索并选择有效 DEMO 家庭户', async ({ page }) => {
    await expectHealthyPage(page, '/migrations/in/apply', '迁入申请')
    const regionItem = page.locator('.el-form-item', { hasText: '迁入地行政区划' })
    await regionItem.locator('.el-input__wrapper').click()
    for (let level = 0; level < 3; level += 1) {
      const menu = page.locator('.el-cascader-menu:visible').nth(level)
      await menu.locator('.el-cascader-node').first().click()
    }
    const householdItem = page.locator('.el-form-item', { hasText: '目标家庭户' })
    const input = householdItem.getByRole('combobox')
    const response = page.waitForResponse((item) => item.url().includes('/api/households') && item.request().method() === 'GET')
    await input.fill('DEMO-HH-IN')
    expect((await response).status()).toBe(200)
    const option = page.getByRole('option').filter({ hasText: 'DEMO-HH-IN' })
    await expect(option).toBeVisible()
    await option.click()
    await expect(householdItem).toContainText('DEMO-HH-IN')
  })

  test('无有效户籍人员在迁出页被提前阻止', async ({ page }) => {
    await expectHealthyPage(page, '/migrations/out/apply', '迁出申请')
    const personItem = page.locator('.el-form-item', { hasText: '办理人员' })
    const input = personItem.getByRole('combobox')
    await input.fill('王家兴')
    await input.press('Enter')
    const option = page.getByRole('option').filter({ hasText: '王家兴' })
    await expect(option).toBeVisible()
    await option.click()
    await expect(page.getByText('该人员没有当前有效户籍，无法办理迁出')).toBeVisible()
    await expect(page.getByRole('button', { name: '保存草稿' })).toBeDisabled()
  })

  test('人口详情加载真实关联信息或正确空状态', async ({ page }) => {
    await expectHealthyPage(page, '/persons', '人口信息管理')
    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('关联信息', { exact: true })).toBeVisible()
    await expect(page.getByText(/有效户籍|暂无有效户籍或家庭关系/).first()).toBeVisible()
    await expect(page.getByText(/本阶段|后接入/)).toHaveCount(0)
  })

  test('注销对象 Radio 切换无废弃警告', async ({ page }) => {
    await expectHealthyPage(page, '/cancellations/apply', '人员注销申请')
    await page.getByText('人员注销', { exact: true }).click()
    await page.getByText('家庭户销户', { exact: true }).click()
  })

  test('DEMO 注销详情不出现 500', async ({ page }) => {
    const response = page.waitForResponse((item) => item.url().includes('/api/cancellations/applications/700002'))
    await page.goto('/applications/700002')
    expect((await response).status()).toBe(200)
    await expect(page.getByText('注销专业信息')).toBeVisible()
  })

  test('居住证专业详情正常或明确提示缺失记录', async ({ page }) => {
    const response = page.waitForResponse((item) => item.url().includes('/api/residence-permits/applications/700001'))
    await page.goto('/applications/700001')
    expect([200, 404]).toContain((await response).status())
    await expect(page.getByText(/居住证专业信息|该申请缺少居住证专业记录/)).toBeVisible()
  })
})
