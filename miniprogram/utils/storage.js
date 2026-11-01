const KEYS = { token: 'population.token', user: 'population.user', username: 'population.username' }

function getToken() { return wx.getStorageSync(KEYS.token) || '' }
function setToken(token) { token ? wx.setStorageSync(KEYS.token, token) : wx.removeStorageSync(KEYS.token) }
function getUser() { return wx.getStorageSync(KEYS.user) || null }
function setUser(user) { user ? wx.setStorageSync(KEYS.user, user) : wx.removeStorageSync(KEYS.user) }
function getRememberedUsername() { return wx.getStorageSync(KEYS.username) || '' }
function rememberUsername(username) { username ? wx.setStorageSync(KEYS.username, username) : wx.removeStorageSync(KEYS.username) }
function clearSession() { wx.removeStorageSync(KEYS.token); wx.removeStorageSync(KEYS.user) }

module.exports = { KEYS, getToken, setToken, getUser, setUser, getRememberedUsername, rememberUsername, clearSession }
