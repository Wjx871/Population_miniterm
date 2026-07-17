const { resolveStatus } = require('../../utils/status')

Component({
  properties: {
    text: { type: String, value: '' },
    status: { type: String, value: '' },
    type: { type: String, value: '' },
    size: { type: String, value: 'medium' }
  },
  data: {
    displayText: '—',
    semanticType: 'info',
    displaySize: 'medium'
  },
  observers: {
    'text,status,type,size': function updateStatus(text, status, type, size) {
      const resolved = resolveStatus({ text, status, type, size })
      this.setData({
        displayText: resolved.text,
        semanticType: resolved.type,
        displaySize: resolved.size
      })
    }
  }
})
