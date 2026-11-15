import { test, expect } from './fixtures.js'
import { loginAs, USERS } from './helpers/auth.js'

test('身份证识别结果经人工核对后才回填，证件住址与现居地址分开', async ({ page }) => {
  await loginAs(page, USERS.population)
  await page.route('**/api/persons/idcard-image', async (route) => {
    await route.fulfill({ json: {
      code: 200,
      data: {
        imageId: 91,
        originalFilename: 'ocr-test.png',
        fileSize: 68,
        ocrStatus: 'SUCCESS',
        ocrIdcardFull: '99000020000229001X',
        ocrIdcardMasked: '990000********001X',
        ocrName: '测试样例',
        ocrBirthDate: '2000-02-29',
        ocrGender: 'M',
        ocrEthnicity: '汉族',
        ocrAddress: '虚构证件住址',
      },
    } })
  })

  await page.goto('/persons')
  await page.getByRole('button', { name: '新增人员' }).click()
  const form = page.getByRole('dialog', { name: '持身份证建档' })
  const name = form.getByPlaceholder('请输入姓名')
  const idCard = form.getByPlaceholder('请输入身份证号')
  const address = form.getByPlaceholder('请输入详细地址')
  await address.fill('人工填写的现居住地址')
  await form.getByRole('button', { name: '扫描身份证' }).click()
  const scanner = page.getByRole('dialog', { name: '上传身份证影印本' })
  await scanner.locator('input[type=file]').setInputFiles({
    name: 'ocr-test.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg==', 'base64'),
  })
  await scanner.getByRole('button', { name: '上传并识别' }).click()
  await expect(scanner.getByText('虚构证件住址', { exact: true })).toBeVisible()
  await expect(scanner.getByRole('img', { name: '身份证正面原图' })).toBeVisible()
  await expect(name).toHaveValue('')
  await expect(idCard).toHaveValue('')
  await scanner.getByRole('button', { name: '核对无误，回填表单' }).click()
  await expect(scanner).toBeHidden()
  await expect(name).toHaveValue('测试样例')
  await expect(idCard).toHaveValue('99000020000229001X')
  await expect(address).toHaveValue('人工填写的现居住地址')
})
