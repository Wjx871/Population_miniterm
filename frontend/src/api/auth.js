import axios from 'axios';

// 配置基础 axios 实例（供后续与真实后端对接时使用）
const request = axios.create({
  baseURL: '/api',
  timeout: 5000,
});

/**
 * 登录接口
 * @param {Object} data 登录信息
 * @param {string} data.username 用户名
 * @param {string} data.password 密码
 * @returns {Promise} 登录结果
 */
export function login(data) {
  // --- 真实后端 API 请求预留位置 ---
  /*
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  });
  */

  // --- 当前使用 Mock 登录逻辑 ---
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const { username, password } = data;
      if (username === 'admin' && password === '123456') {
        resolve({
          code: 200,
          message: '登录成功',
          data: {
            token: 'mock-token-admin-123456',
            username: 'admin',
            role: 'admin'
          }
        });
      } else {
        reject(new Error('用户名或密码错误'));
      }
    }, 500); // 模拟网络延迟
  });
}
