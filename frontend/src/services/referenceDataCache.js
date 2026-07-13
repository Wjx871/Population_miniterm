import { getDictionaryItems } from '../api/dictionaries.js'
import { normalizeDictionaryList } from '../adapters/dictionary.js'
import { getRegionTree } from '../api/regions.js'
import { normalizeRegionTree } from '../adapters/region.js'

let cache = new Map()

function getCacheKey(domain, type, scope) {
  return `${domain}:${type}:${scope}`
}

export async function getCachedDictionary(type, includeInactive = false) {
  const scope = includeInactive ? 'all' : 'active'
  const key = getCacheKey('dictionary', type, scope)
  
  if (cache.has(key)) {
    return cache.get(key)
  }

  const res = await getDictionaryItems(type)
  const data = res.data || res
  const list = normalizeDictionaryList(data, includeInactive)
  cache.set(key, list)
  return list
}

export function invalidateDictionaryCache(type) {
  if (!type) return
  cache.delete(getCacheKey('dictionary', type, 'active'))
  cache.delete(getCacheKey('dictionary', type, 'all'))
}

export async function getCachedRegionTree(includeInactive = false) {
  const scope = includeInactive ? 'all' : 'active'
  const key = getCacheKey('region', 'tree', scope)
  
  if (cache.has(key)) {
    return cache.get(key)
  }

  const res = await getRegionTree()
  const data = res.data || res
  const tree = normalizeRegionTree(data, includeInactive)
  cache.set(key, tree)
  return tree
}

export function invalidateRegionCache() {
  cache.delete(getCacheKey('region', 'tree', 'active'))
  cache.delete(getCacheKey('region', 'tree', 'all'))
}

export function clearAllReferenceCache() {
  cache.clear()
}

export async function getDictionaryLabel(type, code) {
  if (!code) return ''
  const list = await getCachedDictionary(type, true)
  const item = list.find(x => String(x.value) === String(code))
  return item ? item.label : code
}

export function _getCacheStateForTesting() {
  return new Map(cache)
}

export function _setCacheStateForTesting(newCache) {
  cache = newCache
}
