Component({
  data: {
    selected: 0,
    color: '#8A94A6',
    selectedColor: '#1677FF',
    list: [
      { pagePath: '/pages/dashboard/index', text: '工作台', icon: 'dashboard' },
      { pagePath: '/pages/business/index', text: '业务', icon: 'database' },
      { pagePath: '/pages/handling/index', text: '办理', icon: 'application' },
      { pagePath: '/pages/profile/index', text: '我的', icon: 'profile' }
    ]
  },

  methods: {
    switchTab(event) {
      const index = Number(event.currentTarget.dataset.index)
      const item = this.data.list[index]
      if (!item || index === this.data.selected) return
      wx.switchTab({ url: item.pagePath })
    }
  }
})
