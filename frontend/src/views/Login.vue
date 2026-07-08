<template>
  <div class="login-wrapper">
    <!-- 主体区域：左右分栏 -->
    <div class="login-main">
      <!-- 左侧：深蓝色科技背景与系统介绍 -->
      <div class="login-left">
        <!-- 动态背景图层 (从 assets 加载刚生成的科技城市背景) -->
        <div class="tech-bg"></div>
        <div class="tech-overlay"></div>

        <div class="left-content fade-in">
          <!-- 头部 Logo 与 标题 -->
          <div class="brand-header">
            <div class="brand-titles">
              <h1 class="brand-title-cn">人口数据库管理系统</h1>
              <p class="brand-title-en">Population Database Management System</p>
            </div>
          </div>

          <!-- 副标题 -->
          <div class="sub-title-wrapper slide-in">
            <h2>基层人口信息统一管理与分析平台</h2>
            <div class="sub-title-line"></div>
          </div>

          <!-- 六边形功能区 -->
          <div class="feature-hexagons">
            <div class="hex-item delay-1">
              <div class="hex-shape"><el-icon><UserFilled /></el-icon></div>
              <span>人口信息管理</span>
            </div>
            <div class="hex-item delay-2">
              <div class="hex-shape"><el-icon><HomeFilled /></el-icon></div>
              <span>户口管理</span>
            </div>
            <div class="hex-item delay-3">
              <div class="hex-shape"><el-icon><Sort /></el-icon></div>
              <span>迁入迁出管理</span>
            </div>
            <div class="hex-item delay-4">
              <div class="hex-shape"><el-icon><Postcard /></el-icon></div>
              <span>证件管理</span>
            </div>
            <div class="hex-item delay-5">
              <div class="hex-shape"><el-icon><DataAnalysis /></el-icon></div>
              <span>数据统计分析</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：白色表单区 -->
      <div class="login-right">
        <!-- 右侧浅色波浪/几何背景装饰 -->
        <div class="light-bg-deco"></div>

        <div class="login-card fade-in-up">
          <div class="card-header">
            <h2>欢迎登录</h2>
            <p>请使用账号密码登录系统</p>
            <div class="header-line"></div>
          </div>

          <!-- 错误提示组件 -->
          <transition name="fade">
            <div v-if="errorMessage" class="error-alert">
              <el-icon class="error-icon"><Warning /></el-icon>
              <span>{{ errorMessage }}</span>
            </div>
          </transition>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            size="large"
            @keyup.enter="handleLoginSubmit"
            class="classic-form"
          >
            <!-- 用户名 -->
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                clearable
                class="line-input"
              >
                <template #prefix>
                  <el-icon class="input-icon"><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <!-- 密码 -->
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                show-password
                class="line-input"
              >
                <template #prefix>
                  <el-icon class="input-icon"><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <!-- 辅助选项 -->
            <div class="form-actions-area">
              <el-checkbox v-model="rememberMe" class="classic-checkbox">记住我</el-checkbox>
              <a href="#" class="forgot-link" @click.prevent>忘记密码</a>
            </div>

            <!-- 登录按钮 -->
            <el-form-item class="submit-form-item">
              <el-button
                type="primary"
                :loading="loading"
                class="classic-button"
                @click="handleLoginSubmit"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 底部 Mock 提示 -->
          <div class="mock-tip">
            <el-icon class="mock-icon"><CircleCheck /></el-icon>
            <span>Mock 账号: admin / 123456</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部统一 Footer -->
    <footer class="login-footer">
      <div class="footer-left">
        <el-icon class="footer-icon"><Shield /></el-icon> 依法管理人口信息 &nbsp; 服务社会治理现代化
      </div>
      <div class="footer-right">
        © 2026 Population Database Management System <span class="divider">|</span> 版权所有
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { 
  User, Lock, Warning, StarFilled, UserFilled, HomeFilled, Sort, Postcard, DataAnalysis, CircleCheck
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { login } from '../api/auth';

const router = useRouter();
const loginFormRef = ref(null);
const loading = ref(false);
const errorMessage = ref('');
const rememberMe = ref(false);

const loginForm = reactive({
  username: '',
  password: '',
});

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
};

onMounted(() => {
  const savedUsername = localStorage.getItem('saved_username');
  if (savedUsername) {
    loginForm.username = savedUsername;
    rememberMe.value = true;
  }
});

const handleLoginSubmit = () => {
  if (!loginFormRef.value) return;
  
  loginFormRef.value.validate(async (valid) => {
    if (!valid) return;

    loading.value = true;
    errorMessage.value = '';

    try {
      const response = await login({
        username: loginForm.username,
        password: loginForm.password,
      });

      if (response.code === 200) {
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('username', response.data.username);
        localStorage.setItem('role', response.data.role);

        if (rememberMe.value) {
          localStorage.setItem('saved_username', loginForm.username);
        } else {
          localStorage.removeItem('saved_username');
        }

        ElMessage.success({ message: '登录成功', duration: 1500 });
        router.push('/home');
      }
    } catch (error) {
      errorMessage.value = error.message || '账号或密码验证失败';
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
/* 全局页面包裹器 */
.login-wrapper {
  width: 100vw;
  height: 100vh;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Helvetica Neue', 'PingFang SC', sans-serif;
  overflow: hidden;
  background-color: #f0f4f8;
}

/* 主体内容区，占据底部 Footer 之外的所有空间 */
.login-main {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

/* ================= 左侧区域 ================= */
.login-left {
  flex: 5.5; /* 约 55% 宽度 */
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 5%;
  color: #ffffff;
  overflow: hidden;
}

/* 背景图与遮罩 */
.tech-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('../assets/images/login-bg.png');
  background-size: cover;
  background-position: center bottom;
  z-index: 1;
}
.tech-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(8, 41, 102, 0.95) 0%, rgba(13, 61, 145, 0.7) 100%);
  z-index: 2;
}

/* 左侧主要内容层 */
.left-content {
  position: relative;
  z-index: 10;
  max-width: 700px;
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 50px;
}


.brand-titles {
  display: flex;
  flex-direction: column;
}
.brand-title-cn {
  font-size: 42px;
  font-weight: 700;
  letter-spacing: 4px;
  margin: 0 0 6px 0;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
}
.brand-title-en {
  font-size: 16px;
  font-family: Arial, Helvetica, sans-serif;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 1.5px;
  margin: 0;
}

/* 副标题区 */
.sub-title-wrapper {
  margin-bottom: 60px;
}
.sub-title-wrapper h2 {
  font-size: 26px;
  font-weight: 500;
  letter-spacing: 2px;
  margin: 0 0 16px 0;
}
.sub-title-line {
  width: 50px;
  height: 4px;
  background-color: #3b82f6; /* 明亮的蓝色强调线 */
}

/* 六边形功能阵列 */
.feature-hexagons {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.hex-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  transition: transform 0.3s;
}
.hex-item:hover {
  transform: translateY(-5px);
}
.hex-shape {
  width: 60px;
  height: 68px; /* 六边形比例 */
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(5px);
  -webkit-clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 28px;
  color: #ffffff;
  border: 1px solid rgba(255,255,255,0.3); /* Chrome 并不完美支持 clip-path 的 border，此处为降级 */
  position: relative;
}
/* 用伪元素做亮边 */
.hex-shape::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(255,255,255,0.4), transparent);
  -webkit-clip-path: inherit;
  clip-path: inherit;
  pointer-events: none;
}
.hex-item span {
  font-size: 13px;
  letter-spacing: 1px;
  color: rgba(255,255,255,0.9);
}

/* ================= 右侧区域 ================= */
.login-right {
  flex: 4.5; /* 约 45% 宽度 */
  position: relative;
  background: #f4f7fc;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 浅蓝色曲线/几何装饰 */
.light-bg-deco {
  position: absolute;
  top: -20%;
  right: -20%;
  width: 150%;
  height: 150%;
  background: radial-gradient(circle at top right, #e2eaf6 0%, transparent 60%);
  pointer-events: none;
}
.light-bg-deco::after {
  content: '';
  position: absolute;
  bottom: 10%;
  left: 10%;
  width: 80%;
  height: 80%;
  background: radial-gradient(circle at bottom left, #dce6f5 0%, transparent 50%);
}

/* 登录卡片 */
.login-card {
  position: relative;
  z-index: 10;
  width: 440px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 15px 40px rgba(15, 43, 90, 0.08);
  padding: 50px 48px;
}

.card-header {
  text-align: center;
  margin-bottom: 40px;
}
.card-header h2 {
  font-size: 30px;
  color: #0f2b5a; /* 主题深蓝色 */
  margin: 0 0 10px 0;
  letter-spacing: 2px;
}
.card-header p {
  font-size: 14px;
  color: #666666;
  margin: 0 0 20px 0;
}
.header-line {
  width: 36px;
  height: 3px;
  background-color: #2563eb;
  margin: 0 auto;
}

/* 错误提示 */
.error-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: #fef2f2;
  border: 1px solid #fee2e2;
  color: #ef4444;
  padding: 10px 14px;
  border-radius: 4px;
  margin-bottom: 20px;
  font-size: 13px;
}
.error-icon {
  font-size: 16px;
}

/* 表单输入框经典边框样式 */
:deep(.line-input .el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid #dcdfe6 !important;
  border-radius: 4px;
  padding: 6px 12px;
  transition: border-color 0.3s;
}
:deep(.line-input .el-input__wrapper:hover) {
  border-color: #a0cfff !important;
}
:deep(.line-input .el-input__wrapper.is-focus) {
  border-color: #2563eb !important;
}
:deep(.line-input .el-input__inner) {
  height: 36px;
}
.input-icon {
  color: #909399;
  font-size: 16px;
  margin-right: 6px;
}

/* 记住密码 & 忘记密码 */
.form-actions-area {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}
.forgot-link {
  font-size: 13px;
  color: #2563eb;
  text-decoration: none;
}
.forgot-link:hover {
  text-decoration: underline;
}

/* 实心蓝色按钮 */
.classic-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 6px;
  background-color: #165dff;
  border: none;
  border-radius: 4px;
  box-shadow: 0 4px 10px rgba(22, 93, 255, 0.3);
}
.classic-button:hover {
  background-color: #0050d2;
}

/* 底部 Mock 提示 */
.mock-tip {
  margin-top: 35px;
  padding-top: 25px;
  border-top: 1px dashed #ebeef5;
  text-align: center;
  color: #909399;
  font-size: 13px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

/* ================= 底部区域 ================= */
.login-footer {
  height: 50px;
  background-color: #0b1f41; /* 深邃的藏青色 */
  color: rgba(255, 255, 255, 0.6);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 5%;
  font-size: 13px;
  letter-spacing: 0.5px;
  z-index: 20;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.footer-icon {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
}
.divider {
  margin: 0 8px;
  color: rgba(255, 255, 255, 0.3);
}

/* 动画 */
.fade-in {
  animation: fadeIn 1s ease forwards;
}
.fade-in-up {
  animation: fadeInUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  opacity: 0;
  transform: translateY(30px);
}
.slide-in {
  animation: slideInLeft 0.8s ease forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes fadeInUp {
  to { opacity: 1; transform: translateY(0); }
}
@keyframes slideInLeft {
  from { opacity: 0; transform: translateX(-30px); }
  to { opacity: 1; transform: translateX(0); }
}

.delay-1 { animation: fadeIn 0.6s ease 0.1s forwards; opacity: 0; }
.delay-2 { animation: fadeIn 0.6s ease 0.2s forwards; opacity: 0; }
.delay-3 { animation: fadeIn 0.6s ease 0.3s forwards; opacity: 0; }
.delay-4 { animation: fadeIn 0.6s ease 0.4s forwards; opacity: 0; }
.delay-5 { animation: fadeIn 0.6s ease 0.5s forwards; opacity: 0; }

/* 响应式调整 */
@media (max-width: 1024px) {
  .login-left {
    display: none;
  }
  .login-right {
    flex: 1;
    background-image: url('../assets/images/login-bg.png');
    background-size: cover;
    background-position: center;
  }
  .login-right::before {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(15, 43, 90, 0.7);
  }
}
</style>
