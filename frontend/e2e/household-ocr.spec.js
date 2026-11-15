import { test, expect } from './fixtures.js'
import { loginAs, USERS } from './helpers/auth.js'

test('迁入草稿的户口簿预览需显式识别，核对后仍可正常上传', async ({ page }) => {
  await loginAs(page, USERS.household)
  const id = 890001
  await page.route(`**/api/migrations/applications/${id}`, route => route.fulfill({ json: {
    code: 200,
    data: {
      application: { applicationId: id, title: '户口簿预览测试草稿', status: 'DRAFT' },
      migrationIn: { migrationType: 'OUTSIDE_CITY', version: 0 },
      materials: [],
    },
  } }))
  let recognitionRequests = 0
  let uploadRequests = 0
  await page.route(`**/api/applications/${id}/materials/household-ocr`, async route => {
    recognitionRequests++
    expect(route.request().postData()).toContain('name="pageType"')
    expect(route.request().postData()).toContain('subpage')
    await route.fulfill({ json: {
      code: 200,
      data: {
        page: 'subpage',
        pageNumber: 1,
        fields: [{ key: 'name', label: '姓名', value: '虚构测试成员' }],
        warnings: ['未识别到身份证号，请人工核对'],
      },
    } })
  })
  await page.route(`**/api/applications/${id}/materials`, async route => {
    if (route.request().method() === 'POST') uploadRequests++
    await route.fulfill({ json: { code: 200, data: [] } })
  })

  await page.goto(`/migrations/in/apply?applicationId=${id}`)
  const uploader = page.locator('.material-uploader')
  await uploader.getByText('请选择业务规则要求的材料', { exact: true }).click()
  await page.getByRole('option', { name: '户口簿', exact: true }).click()
  await uploader.locator('input[type=file]').setInputFiles({
    name: 'household.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg==', 'base64'),
  })
  await uploader.getByRole('button', { name: '户口簿识别预览' }).click()
  const dialog = page.getByRole('dialog', { name: '户口簿识别预览' })
  await expect(dialog.getByRole('img', { name: '户口簿原图' })).toBeVisible()
  expect(recognitionRequests).toBe(0)
  await dialog.getByText('成员页', { exact: true }).click()
  await dialog.getByRole('button', { name: '发送并识别' }).click()
  await expect(dialog.getByText('虚构测试成员', { exact: true })).toBeVisible()
  await expect(dialog.getByText('未识别到身份证号，请人工核对', { exact: true })).toBeVisible()
  expect(recognitionRequests).toBe(1)
  expect(uploadRequests).toBe(0)
  await dialog.getByRole('button', { name: '返回材料上传' }).click()
  await expect(uploader.getByRole('button', { name: '上传材料' })).toBeEnabled()
})

test('选传的迁入申请表可按字段定位原文，预览不会上传材料', async ({ page }) => {
  await loginAs(page, USERS.household)
  const id = 890002
  await page.route(`**/api/migrations/applications/${id}`, route => route.fulfill({ json: {
    code: 200,
    data: {
      application: { applicationId: id, title: '申请表测试草稿', status: 'DRAFT' },
      migrationIn: { migrationType: 'OUTSIDE_CITY', version: 0 },
      materials: [],
    },
  } }))
  let uploaded = false
  await page.route(`**/api/applications/${id}/materials`, route => {
    uploaded ||= route.request().method() === 'POST'
    return route.fulfill({ json: { code: 200, data: [] } })
  })
  await page.route(`**/api/applications/${id}/materials/migration-form-ocr`, route => route.fulfill({ json: {
    code: 200,
    data: {
      page: 'applicationForm',
      pageNumber: 1,
      fields: [{
        key: 'name', label: '申请人姓名', value: '合成测试',
        location: { left: 0, top: 0, width: 1, height: 1 },
      }],
      warnings: ['身份证号需人工核对'],
    },
  } }))

  await page.goto(`/migrations/in/apply?applicationId=${id}`)
  const uploader = page.locator('.material-uploader')
  await uploader.getByText('请选择业务规则要求的材料', { exact: true }).click()
  await page.getByRole('option', { name: '迁入申请表', exact: true }).click()
  await expect(uploader.getByText('非必需材料', { exact: true })).toBeVisible()
  await uploader.locator('input[type=file]').setInputFiles({
    name: 'migration-form.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg==', 'base64'),
  })
  await uploader.getByRole('button', { name: '迁入申请表识别预览' }).click()
  const preview = page.getByRole('dialog', { name: '迁入申请表识别预览' })
  await preview.getByRole('button', { name: '发送并识别' }).click()
  await expect(preview.getByText('身份证号需人工核对')).toBeVisible()
  await expect(preview.getByRole('img', { name: '迁入申请表原图' })).toBeVisible()
  await preview.getByRole('button', { name: '合成测试' }).click()
  await expect(preview.locator('.review-highlight')).toBeVisible()
  expect(uploaded).toBe(false)
  await preview.getByRole('button', { name: '返回材料上传' }).click()
  await expect(uploader.getByRole('button', { name: '上传材料' })).toBeEnabled()
})
