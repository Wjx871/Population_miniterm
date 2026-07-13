export function normalizeRegionNode(node, includeInactive = false) {
  if (!node) return null
  const status = node.status || 'DISABLED'
  const isActive = status === 'ACTIVE' || status === '正常' || status === '启用'
  
  if (!includeInactive && !isActive) return null

  let children = null
  if (Array.isArray(node.children) && node.children.length > 0) {
    children = normalizeRegionTree(node.children, includeInactive)
    if (children.length === 0) children = null
  }

  return {
    id: node.regionId,
    value: node.regionCode,
    label: node.regionName,
    fullName: node.fullName || node.regionName,
    level: node.regionLevel,
    parentCode: node.parentCode,
    sortNo: node.sortNo || 0,
    status: status,
    version: node.version || 0,
    disabled: !isActive,
    isLeaf: !children,
    children
  }
}

export function normalizeRegionTree(nodes, includeInactive = false) {
  if (!Array.isArray(nodes)) return []
  return nodes.map(node => normalizeRegionNode(node, includeInactive)).filter(Boolean)
}

export function findRegionPath(tree, value) {
  if (!Array.isArray(tree) || !value) return null
  for (const node of tree) {
    if (node.value === value) return [node]
    if (node.children) {
      const path = findRegionPath(node.children, value)
      if (path) return [node, ...path]
    }
  }
  return null
}
