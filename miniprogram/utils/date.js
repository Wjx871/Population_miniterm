function formatDate(value) { return value ? String(value).slice(0, 10) : '—' }
function formatDateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' }
module.exports = { formatDate, formatDateTime }
