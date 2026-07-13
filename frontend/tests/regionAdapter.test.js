import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeRegionTree,
  findRegionPath,
  toRegionCreatePayload,
  toRegionUpdatePayload,
  canCreateRegionChild,
  isValidRegionLevel,
} from '../src/adapters/region.js'

test('regionAdapter: normalizes tree and drops inactive when includeInactive is false', () => {
  const input = [
    {
      regionId: 1, regionCode: '100', regionName: 'A', status: 'ENABLED',
      children: [
        { regionId: 2, regionCode: '101', regionName: 'A1', status: 'DISABLED' },
        { regionId: 3, regionCode: '102', regionName: 'A2', status: 'ENABLED' }
      ]
    }
  ]
  const tree = normalizeRegionTree(input, false)
  assert.equal(tree.length, 1)
  assert.equal(tree[0].children.length, 1)
  assert.equal(tree[0].children[0].value, '102')
})

test('regionAdapter: includeInactive keeps all', () => {
  const input = [{ regionId: 1, regionCode: '100', regionName: 'A', status: 'DISABLED' }]
  const tree = normalizeRegionTree(input, true)
  assert.equal(tree.length, 1)
  assert.equal(tree[0].disabled, true)
})

test('regionAdapter: findRegionPath returns array of nodes', () => {
  const tree = normalizeRegionTree([{
    regionId: 1, regionCode: '100', regionName: 'A', status: 'ENABLED',
    children: [
      { regionId: 3, regionCode: '102', regionName: 'A2', status: 'ENABLED' }
    ]
  }])
  const path = findRegionPath(tree, '102')
  assert.equal(path.length, 2)
  assert.equal(path[0].value, '100')
  assert.equal(path[1].value, '102')
})

test('regionAdapter: toRegionCreatePayload formats properly', () => {
  const form = {
    regionCode: '110000',
    regionName: '北京市',
    parentCode: '',
    regionLevel: 1,
    fullName: '北京市',
    sortNo: 1,
    version: 0
  }

  const payload = toRegionCreatePayload(form)

  assert.deepEqual(payload, {
    regionCode: '110000',
    regionName: '北京市',
    parentCode: null,
    regionLevel: 1,
    fullName: '北京市',
    sortNo: 1
  })
  
  assert.equal(payload.version, undefined)
})

test('regionAdapter: toRegionUpdatePayload formats properly', () => {
  const form = {
    regionCode: '110000',
    regionName: '北京市',
    parentCode: '',
    regionLevel: 1,
    fullName: '北京市',
    sortNo: 1,
    version: 3
  }

  const payload = toRegionUpdatePayload(form)

  assert.deepEqual(payload, {
    regionName: '北京市',
    parentCode: null,
    regionLevel: 1,
    fullName: '北京市',
    sortNo: 1,
    version: 3
  })
  
  assert.equal(payload.regionCode, undefined)
})

test('regionAdapter: create payload does not include version', () => {
  const payload = toRegionCreatePayload({
    regionCode: '110101',
    regionName: '东城区',
    parentCode: '110100',
    regionLevel: 3,
    fullName: '北京市市辖区东城区',
    sortNo: 1,
    version: 9,
  })

  assert.equal(Object.hasOwn(payload, 'version'), false)
})

test('regionAdapter: update payload does not include regionCode', () => {
  const payload = toRegionUpdatePayload({
    regionCode: '110101',
    regionName: '东城区',
    parentCode: '110100',
    regionLevel: 3,
    fullName: '北京市市辖区东城区',
    sortNo: 1,
    version: 2,
  })

  assert.equal(Object.hasOwn(payload, 'regionCode'), false)
  assert.equal(payload.version, 2)
})

test('regionAdapter: regionLevel must be between 1 and 5', () => {
  assert.equal(isValidRegionLevel(1), true)
  assert.equal(isValidRegionLevel(5), true)
  assert.equal(isValidRegionLevel(0), false)
  assert.equal(isValidRegionLevel(6), false)
  assert.equal(isValidRegionLevel(10), false)
  assert.equal(isValidRegionLevel('3'), true)
})

test('五级行政区划不能继续创建下级', () => {
  const parent = { level: 5 }
  assert.equal(canCreateRegionChild(parent), false)
})

test('regionAdapter: levels 1-4 can create children', () => {
  assert.equal(canCreateRegionChild({ level: 1 }), true)
  assert.equal(canCreateRegionChild({ level: 4 }), true)
  assert.equal(canCreateRegionChild({ level: 0 }), false)
  assert.equal(canCreateRegionChild(null), false)
})
