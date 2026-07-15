const TAB_INDEX = Object.freeze({
  dashboard: 0,
  business: 1,
  handling: 2,
  profile: 3
})

function syncTabBar(page, name) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  const selected = TAB_INDEX[name]
  if (tabBar && typeof tabBar.setData === 'function' && Number.isInteger(selected)) {
    tabBar.setData({ selected })
  }
}

function resetTabBar(page) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (tabBar && typeof tabBar.setData === 'function') tabBar.setData({ selected: TAB_INDEX.dashboard })
}

module.exports = { TAB_INDEX, syncTabBar, resetTabBar }
