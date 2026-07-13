import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getCachedDictionary,
  invalidateDictionaryCache,
  getCachedRegionTree,
  invalidateRegionCache,
  clearAllReferenceCache,
  _setCacheStateForTesting,
  _getCacheStateForTesting,
  _setApiForTesting
} from '../src/services/referenceDataCache.js'

test('referenceDataCache: separates active and all scope caches', async () => {
  clearAllReferenceCache()

  // Fake the API call to avoid real network
  _setApiForTesting(async () => [{ dictId: 1, dictType: 'CERTIFICATE_TYPE', dictCode: 'id', dictName: 'id', status: 'ENABLED', sortNo: 1 }, { dictId: 2, dictType: 'CERTIFICATE_TYPE', dictCode: 'other', dictName: 'other', status: 'DISABLED', sortNo: 2 }], undefined)

  const active = await getCachedDictionary('CERTIFICATE_TYPE', false)
  const all = await getCachedDictionary('CERTIFICATE_TYPE', true)

  assert.equal(active.length, 1)
  assert.equal(all.length, 2)
  clearAllReferenceCache()
})

test('referenceDataCache: handles concurrent requests correctly', async () => {
  clearAllReferenceCache()

  let callCount = 0
  _setApiForTesting(async () => {
    callCount++
    return [{ dictId: 1, dictType: 'TEST', dictCode: '1', dictName: '1', status: 'ENABLED', sortNo: 1 }]
  }, undefined)

  // Fire two requests concurrently
  const p1 = getCachedDictionary('TEST', false)
  const p2 = getCachedDictionary('TEST', false)

  await Promise.all([p1, p2])

  assert.equal(callCount, 1, 'API should only be called once for concurrent requests')
  clearAllReferenceCache()
})

test('referenceDataCache: does not pollute cache on failure', async () => {
  clearAllReferenceCache()

  _setApiForTesting(async () => {
    throw new Error('API Error')
  }, undefined)

  try {
    await getCachedDictionary('FAIL_TEST', false)
    assert.fail('Should have thrown')
  } catch (err) {
    assert.equal(err.message, 'API Error')
  }

  const cache = _getCacheStateForTesting()
  assert.equal(cache.has('dictionary:FAIL_TEST:active'), false, 'Cache should not store failed promise')
  clearAllReferenceCache()
})

test('referenceDataCache: invalidate dictionary cache removes both scopes', () => {
  clearAllReferenceCache()
  const cache = new Map()
  cache.set('dictionary:CERT_TYPE:active', Promise.resolve([]))
  cache.set('dictionary:CERT_TYPE:all', Promise.resolve([]))
  cache.set('dictionary:OTHER:active', Promise.resolve([]))
  _setCacheStateForTesting(cache)

  invalidateDictionaryCache('CERT_TYPE')

  const current = _getCacheStateForTesting()
  assert.equal(current.has('dictionary:CERT_TYPE:active'), false)
  assert.equal(current.has('dictionary:CERT_TYPE:all'), false)
  assert.equal(current.has('dictionary:OTHER:active'), true)
  clearAllReferenceCache()
})

test('referenceDataCache: clearAllReferenceCache clears everything', () => {
  clearAllReferenceCache()
  const cache = new Map()
  cache.set('dictionary:CERT_TYPE:active', Promise.resolve([]))
  cache.set('region:tree:all', Promise.resolve([]))
  _setCacheStateForTesting(cache)

  clearAllReferenceCache()

  const current = _getCacheStateForTesting()
  assert.equal(current.size, 0)
})
