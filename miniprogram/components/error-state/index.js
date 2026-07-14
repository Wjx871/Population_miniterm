Component({ properties: { message: { type: String, value: '加载失败' } }, methods: { retry() { this.triggerEvent('retry') } } })
