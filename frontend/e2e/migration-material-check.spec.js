import { test, expect } from './fixtures.js'
import { loginAs, USERS } from './helpers/auth.js'

const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg==', 'base64')

test('迁入草稿可连续上传材料并从冲突提示返回对应原图', async ({ page }) => {
  await loginAs(page, USERS.household)
  const draft = 890003
  const materials = []
  await page.route(`**/api/migrations/applications/${draft}`, route => route.fulfill({ json: {
    code: 200,
    data: {
      application: { applicationId: draft, title: '材料预核对草稿', status: 'DRAFT' },
      migrationIn: { migrationType: 'OUTSIDE_CITY', version: 0 },
      materials,
    },
  } }))
  const types = ['IDENTITY_PROOF', 'HOUSEHOLD_BOOK', 'APPLICATION_FORM']
  const names = ['身份证明', '户口簿', '迁入申请表']
  await page.route(`**/api/applications/${draft}/materials`, route => {
    const index = materials.length
    const material = {
      materialId: index + 1, materialType: types[index], materialName: names[index],
      originalFilename: `sample-${index}.png`, contentType: 'image/png', verifyStatus: 'PENDING',
    }
    materials.push(material)
    return route.fulfill({ status: 201, json: { code: 201, data: material } })
  })
  let checks = 0
  await page.route(`**/api/applications/${draft}/materials/consistency-check`, route => {
    checks++
    expect(route.request().postDataJSON()).toEqual({ identityMaterialId: 1, householdMaterialId: 2, formMaterialId: 3 })
    return route.fulfill({ json: { code: 200, data: {
      status: 'CONFLICT', nameStatus: 'CONFLICT', idCardStatus: 'MATCH',
      documents: materials.map(({ materialId, materialName, materialType }) => ({
        materialId, materialName, materialType,
        name: materialId === 3
          ? { value: '合成乙', location: { left: 0, top: 0, width: 1, height: 1 } }
          : { value: '合成甲' },
        idCard: { value: '9900**********001X' },
      })),
    } } })
  })
  await page.route('**/api/materials/3/download', route => route.fulfill({ contentType: 'image/png', body: image }))

  await page.goto(`/migrations/in/apply?applicationId=${draft}`)
  const uploader = page.locator('.material-uploader')
  for (const [index, name] of names.entries()) {
    await uploader.getByText('请选择业务规则要求的材料', { exact: true }).click()
    await page.getByRole('option', { name, exact: true }).click()
    await uploader.locator('input[type=file]').setInputFiles({
      name: `sample-${index}.png`, mimeType: 'image/png', buffer: image,
    })
    await uploader.getByRole('button', { name: '上传材料', exact: true }).click()
    await expect(uploader.getByText('请选择业务规则要求的材料', { exact: true })).toBeVisible()
    await expect(uploader.getByText(`sample-${index}.png`, { exact: true })).toHaveCount(0)
  }

  const labels = ['身份证正面', '户口簿成员页', '迁入申请表（选传）']
  const check = page.locator('.el-card').filter({ has: page.getByText('材料一致性预核对', { exact: true }) })
  for (const [index, label] of labels.entries()) {
    const field = check.locator('.el-form-item').filter({ hasText: label })
    await field.locator('.el-select__wrapper').click()
    await page.getByRole('option', { name: `sample-${index}.png`, exact: true }).click()
  }
  expect(checks).toBe(0)
  await check.getByRole('button', { name: '开始预核对' }).click()
  await expect(check.getByText('发现疑似字段冲突，请对照原图核实')).toBeVisible()
  await expect(check.getByText('合成乙')).toBeVisible()
  await expect(check.getByText('9900**********001X').first()).toBeVisible()
  expect(checks).toBe(1)
  await check.getByRole('row').filter({ hasText: '合成乙' }).getByRole('button', { name: '查看原图' }).click()
  const preview = page.getByRole('dialog', { name: '材料原图' })
  await expect(preview.getByRole('img', { name: '迁入申请表（选传）原图' })).toBeVisible()
  await expect(preview.locator('.original-highlight')).toBeVisible()
})
