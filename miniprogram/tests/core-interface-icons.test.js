const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

test('core miniprogram interfaces use semantic icons without old characters', () => {
  const login = source('../pages/login/index.wxml')
  const dashboard = source('../pages/dashboard/index.wxml')
  const personList = source('../pages/persons/list/index.wxml')

  assert.match(login, /name="account"/)
  assert.match(login, /name="lock"/)
  assert.match(login, /'eye-off' : 'eye'/)
  assert.doesNotMatch(login, />账<|>密</)
  assert.match(dashboard, /name="\{\{item\.icon\}\}"/)
  assert.doesNotMatch(dashboard, /entry-icon__shape|metric-icon__shape/)
  assert.match(personList, /name="chevron-right"/)
  assert.doesNotMatch(personList, /›/)
})
