import test from 'node:test'
import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'

async function missing(url) {
  try {
    await access(url)
    return false
  } catch {
    return true
  }
}

test('注册和用户 CRUD 旧契约已从可执行前端删除', async () => {
  const [auth, router] = await Promise.all([
    readFile(new URL('../src/api/auth.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/router/index.js', import.meta.url), 'utf8'),
  ])
  assert.doesNotMatch(auth, /auth\/register/)
  assert.doesNotMatch(router, /path:\s*['"]users['"]/)
  assert.equal(await missing(new URL('../src/api/users.js', import.meta.url)), true)
  assert.equal(await missing(new URL('../src/views/users/UserList.vue', import.meta.url)), true)
})

test('人口和家庭户不再暴露普通删除，成员离户使用正式 POST', async () => {
  const [persons, households] = await Promise.all([
    readFile(new URL('../src/api/persons.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/households.js', import.meta.url), 'utf8'),
  ])
  assert.doesNotMatch(persons, /deletePerson|method:\s*['"]delete['"]/)
  assert.doesNotMatch(households, /deleteHousehold|removeHouseholdMember|method:\s*['"]delete['"]/)
  assert.match(households, /members\/\$\{memberId\}\/leave/)
  assert.match(households, /method:\s*['"]post['"]/)
})
