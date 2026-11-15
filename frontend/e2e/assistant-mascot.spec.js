import { test, expect } from './fixtures.js'
import { loginAs, USERS } from './helpers/auth.js'

test('政策助手的眼睛以面罩为中心追随光标', async ({ page }) => {
  await loginAs(page, USERS.population)
  await page.goto('/assistant/policy')
  const head = page.locator('.hero-mascot .robot-head')
  const eyes = head.locator('.robot-eyes')
  await expect(head).toBeVisible()

  const box = await head.boundingBox()
  const centerX = box.x + box.width / 2
  const centerY = box.y + box.height / 2
  const gaze = () => eyes.evaluate(element => {
    const transform = new DOMMatrixReadOnly(getComputedStyle(element).transform)
    return { x: transform.m41, y: transform.m42 }
  })

  // 光标在窗口右半边，但在面罩左侧时，眼睛应向左看。
  const left = centerX - 36
  expect(left).toBeGreaterThan(page.viewportSize().width / 2)
  await page.mouse.move(left, centerY - 28)
  await expect.poll(async () => {
    const { x, y } = await gaze()
    return x < -5 && y < -3
  }).toBe(true)

  await page.mouse.move(centerX + 36, centerY + 28)
  await expect.poll(async () => {
    const { x, y } = await gaze()
    return x > 5 && y > 3 && Math.hypot(x, y) <= 14.1
  }).toBe(true)

  const current = await head.boundingBox()
  await page.mouse.move(current.x + current.width / 2, current.y + current.height / 2)
  await expect.poll(async () => {
    const { x, y } = await gaze()
    return Math.hypot(x, y)
  }).toBeLessThan(2)
})
