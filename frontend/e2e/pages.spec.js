import { test } from './fixtures.js'
import { USERS, loginAs } from './helpers/auth.js'
import { expectHealthyPage } from './helpers/page.js'

const pageGroups = [
  [
    ['/home', '工作台'],
    ['/persons', '人口信息管理'],
    ['/households', '户籍管理'],
    ['/migrations/in', '迁入申请'],
    ['/migrations/out', '迁出申请'],
    ['/cancellations', '注销管理'],
  ],
  [
    ['/floating-population', '流动人口管理'],
    ['/residence-permits', '居住证管理'],
    ['/certificates', '通用证件管理'],
    ['/key-population', '重点人口管理'],
    ['/approvals', '审批中心'],
    ['/queries/comprehensive', '人口综合查询'],
  ],
  [
    ['/statistics/dashboard', '数据统计大屏'],
    ['/logs/operations', '操作日志'],
    ['/logs/logins', '登录日志'],
    ['/region', '行政区划管理'],
    ['/dictionary', '数据字典管理'],
  ],
]

for (const [index, pages] of pageGroups.entries()) {
  test(`管理员全页面巡检 ${index + 1}/${pageGroups.length}`, async ({ page }) => {
    await loginAs(page, USERS.admin)
    for (const [path, heading] of pages) {
      await test.step(`${heading} ${path}`, () => expectHealthyPage(page, path, heading))
    }
  })
}
