import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const routerContent = fs.readFileSync(path.join(__dirname, '../src/router/index.js'), 'utf8')

function extractRouteMeta(content, pathName) {
  const routeRegex = new RegExp(`path:\\s*['"]${pathName}['"][\\s\\S]*?meta:\\s*{([^}]*)}`)
  const match = content.match(routeRegex)
  if (!match) return null
  
  const metaText = match[1]
  const permissionMatch = metaText.match(/permission:\s*['"]([^'"]+)['"]/)
  const minLevelMatch = metaText.match(/minLevel:\s*(\d+)/)
  
  return {
    permission: permissionMatch ? permissionMatch[1] : null,
    minLevel: minLevelMatch ? parseInt(minLevelMatch[1], 10) : null
  }
}

test('routeContract: verify permission and minLevel requirements', () => {
  // Verify dictionary route
  const dictMeta = extractRouteMeta(routerContent, 'dictionary')
  assert.ok(dictMeta, 'Dictionary route meta should exist')
  assert.equal(dictMeta.permission, 'dictionary:view')
  assert.equal(dictMeta.minLevel, 1)

  // Verify region route
  const regionMeta = extractRouteMeta(routerContent, 'region')
  assert.ok(regionMeta, 'Region route meta should exist')
  assert.equal(regionMeta.permission, 'region:view')
  assert.equal(regionMeta.minLevel, 1)
})
