function cleanQueryParams(params) {
  if (!params || typeof params !== 'object' || Array.isArray(params)) return params
  return Object.keys(params).reduce((cleaned, key) => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') cleaned[key] = value
    return cleaned
  }, {})
}

module.exports = { cleanQueryParams }
