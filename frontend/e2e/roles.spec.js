import { test, expect } from './fixtures.js'
import { USERS, expectForbiddenAndSession, loginAs, refreshAndAssertIdentity } from './helpers/auth.js'
import { expectHealthyPage } from './helpers/page.js'

test.describe('五角色权限与会话隔离', () => {
  test('查询统计人员仅使用查询入口，越权后会话仍有效', async ({ page }) => {
    await loginAs(page, USERS.viewer)
    await refreshAndAssertIdentity(page, USERS.viewer)
    await expectHealthyPage(page, '/persons', '人口信息管理')
    await expect(page.getByRole('button', { name: /新增|编辑|删除|审批|执行/ })).toHaveCount(0)
    await expectHealthyPage(page, '/queries/comprehensive', '人口综合查询')
    await expectForbiddenAndSession(page, USERS.viewer, '/approvals')
  })

  test('人口管理人员可访问人口与重点人口，但无户籍写和日志权限', async ({ page }) => {
    await loginAs(page, USERS.population)
    await refreshAndAssertIdentity(page, USERS.population)
    await expectHealthyPage(page, '/persons', '人口信息管理')
    await expectHealthyPage(page, '/key-population', '重点人口管理')
    await expectHealthyPage(page, '/households', '户籍管理')
    await expect(page.getByRole('button', { name: '新增户籍' })).toHaveCount(0)
    await expectForbiddenAndSession(page, USERS.population, '/logs/operations')
  })

  test('户籍管理人员可访问户籍迁移，但无人口写和日志权限', async ({ page }) => {
    await loginAs(page, USERS.household)
    await refreshAndAssertIdentity(page, USERS.household)
    await expectHealthyPage(page, '/households', '户籍管理')
    await expectHealthyPage(page, '/migrations/in', '迁入申请')
    await expectHealthyPage(page, '/migrations/out', '迁出申请')
    await expectHealthyPage(page, '/persons', '人口信息管理')
    await expect(page.getByRole('button', { name: '新增人口' })).toHaveCount(0)
    await expectForbiddenAndSession(page, USERS.household, '/logs/operations')
  })

  test('审批人员可查看待办已办，但无执行和日志权限', async ({ page }) => {
    await loginAs(page, USERS.approver)
    await refreshAndAssertIdentity(page, USERS.approver)
    await expectHealthyPage(page, '/approvals', '审批中心')
    await expect(page.getByRole('tab', { name: '待办审批' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '已办审批' })).toBeVisible()
    await page.getByRole('tab', { name: '已办审批' }).click()
    await expect(page.getByRole('tab', { name: '已办审批' })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('button', { name: /执行/ })).toHaveCount(0)
    await expectForbiddenAndSession(page, USERS.approver, '/logs/operations')
  })

  test('系统管理员拥有完整菜单和管理入口', async ({ page }) => {
    await loginAs(page, USERS.admin)
    await refreshAndAssertIdentity(page, USERS.admin)
    await expectHealthyPage(page, '/home', '工作台')
    await expect(page.getByText('系统管理', { exact: true })).toBeVisible()
    await page.getByText('业务办理', { exact: true }).click()
    await expect(page.getByText('审批中心', { exact: true })).toBeVisible()
  })
})
