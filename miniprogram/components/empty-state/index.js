Component({
  properties: {
    text: { type: String, value: '' },
    title: { type: String, value: '' },
    description: { type: String, value: '' },
    actionText: { type: String, value: '' }
  },
  methods: {
    action() {
      if (this.data.actionText) this.triggerEvent('action')
    }
  }
})
