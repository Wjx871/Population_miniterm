import { getDictionaryItems } from '../api/dictionaries.js'
import { normalizeDictionaryList } from '../adapters/dictionary.js'
import { getRegionTree } from '../api/regions.js'
import { normalizeRegionTree } from '../adapters/region.js'

let cache = new Map()

let getDictionaryItemsFn = getDictionaryItems
let getRegionTreeFn = getRegionTree

export function _setApiForTesting(dictMock, regionMock) {
  if (dictMock !== undefined) getDictionaryItemsFn = dictMock
  if (regionMock !== undefined) getRegionTreeFn = regionMock
}

function getCacheKey(domain, type, scope) {
  return `${domain}:${type}:${scope}`
}

export async function getCachedDictionary(type, includeInactive = false) {
  const scope = includeInactive ? 'all' : 'active'
  const key = getCacheKey('dictionary', type, scope)
  
  if (cache.has(key)) {
    return cache.get(key)
  }

  const promise = getDictionaryItemsFn(type).then(data => {
    return normalizeDictionaryList(data, includeInactive)
  }).catch(err => {
    cache.delete(key) // 失败不污染缓存
    throw err
  })

  cache.set(key, promise)
  return promise
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

  // silent: 区划是筛选辅助数据，403 不由全局拦截器抢先弹窗
  // 兼容测试 mock：无参调用 getRegionTreeFn()
  const loadTree = () => {
    try {
      return getRegionTreeFn({ silent: true })
    } catch {
      return getRegionTreeFn()
    }
  }
  const promise = Promise.resolve()
    .then(() => loadTree())
    .then((res) => {
      const data = res?.data || res
      return normalizeRegionTree(data, includeInactive)
    })
    .catch((err) => {
      cache.delete(key) // 失败不污染缓存
      throw err
    })

  cache.set(key, promise)
  return promise
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
