/**
 * 返回上一页；当页面由刷新、书签或新标签直接打开时，改回业务列表页。
 *
 * Vue Router 会在 history.state.back 中记录站内上一条记录。直接使用
 * router.back() 在没有该记录时可能跳出系统，或让当前路由没有可渲染内容。
 */
export function goBackOrFallback(router, fallback) {
  if (window.history.state?.back) {
    router.back()
    return
  }

  router.replace(fallback)
}
