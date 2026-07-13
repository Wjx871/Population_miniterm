import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getCachedDictionary,
  invalidateDictionaryCache,
  getCachedRegionTree,
  invalidateRegionCache,
  clearAllReferenceCache,
  _setCacheStateForTesting,
  _getCacheStateForTesting
} from '../src/services/referenceDataCache.js'

test('referenceDataCache: separates active and all scope caches', async () => {
  const cache = new Map()
  _setCacheStateForTesting(cache)
  
  // Fake setting caches
  cache.set('dictionary:CERTIFICATE_TYPE:active', [{ value: 'id', status: 'ACTIVE' }])
  cache.set('dictionary:CERTIFICATE_TYPE:all', [{ value: 'id', status: 'ACTIVE' }, { value: 'other', status: 'DISABLED' }])

  const active = await getCachedDictionary('CERTIFICATE_TYPE', false)
  const all = await getCachedDictionary('CERTIFICATE_TYPE', true)
  
  assert.equal(active.length, 1)
  assert.equal(all.length, 2)
  clearAllReferenceCache()
})

test('referenceDataCache: invalidate dictionary cache', () => {
  const cache = new Map()
  cache.set('dictionary:CERT_TYPE:active', [])
  cache.set('dictionary:CERT_TYPE:all', [])
  cache.set('dictionary:OTHER:active', [])
  _setCacheStateForTesting(cache)

  invalidateDictionaryCache('CERT_TYPE')
  
  const current = _getCacheStateForTesting()
  assert.equal(current.has('dictionary:CERT_TYPE:active'), false)
  assert.equal(current.has('dictionary:CERT_TYPE:all'), false)
  assert.equal(current.has('dictionary:OTHER:active'), true)
  clearAllReferenceCache()
})

test('referenceDataCache: clearAllReferenceCache clears everything', () => {
  const cache = new Map()
  cache.set('dictionary:CERT_TYPE:active', [])
  cache.set('region:tree:all', [])
  _setCacheStateForTesting(cache)

  clearAllReferenceCache()
  
  const current = _getCacheStateForTesting()
  assert.equal(current.size, 0)
})
