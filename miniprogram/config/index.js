const defaults = { BASE_URL: 'http://127.0.0.1:8080' }
let local = {}
try {
  local = require('./local')
} catch (error) {
  local = {}
}

const BASE_URL = String(local.BASE_URL || defaults.BASE_URL).replace(/\/$/, '')
module.exports = { BASE_URL }
