function markForRefresh(route, flag) {
  if (typeof getCurrentPages !== 'function') return
  const pages = getCurrentPages()
  pages.forEach((page) => {
    if (page && page.route === route) page[flag || '_needsRefresh'] = true
  })
}

module.exports = { markForRefresh }
