const { resolveErrorState } = require('../../utils/error-state')

Component({
  properties: {
    type: { type: String, value: '' },
    statusCode: { type: Number, value: -1 },
    message: { type: String, value: '' },
    showRetry: { type: Boolean, value: true },
    retryText: { type: String, value: '重新加载' }
  },
  data: {
    displayType: 'unknown',
    title: '加载失败',
    description: '请稍后重试'
  },
  observers: {
    'type,statusCode,message': function updateError(type, statusCode, message) {
      const state = resolveErrorState({ type, statusCode, message })
      this.setData({ displayType: state.type, title: state.title, description: state.description })
    }
  },
  methods: {
    retry() {
      if (this.data.showRetry) this.triggerEvent('retry')
    }
  }
})
