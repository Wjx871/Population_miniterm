import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeRegionTree, findRegionPath } from '../src/adapters/region.js'

test('regionAdapter: normalizes tree and drops inactive when includeInactive is false', () => {
  const input = [
    {
      regionId: 1, regionCode: '100', regionName: 'A', status: 'ACTIVE',
      children: [
        { regionId: 2, regionCode: '101', regionName: 'A1', status: 'DISABLED' },
        { regionId: 3, regionCode: '102', regionName: 'A2', status: 'ACTIVE' }
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
    regionId: 1, regionCode: '100', regionName: 'A', status: 'ACTIVE',
    children: [
      { regionId: 3, regionCode: '102', regionName: 'A2', status: 'ACTIVE' }
    ]
  }])
  const path = findRegionPath(tree, '102')
  assert.equal(path.length, 2)
  assert.equal(path[0].value, '100')
  assert.equal(path[1].value, '102')
})
