<template>
  <div class="login-wrapper">
    <!-- 全屏背景图与深蓝遮罩，彻底消除左右割裂感 -->
    <div class="tech-bg"></div>
    <div class="tech-overlay"></div>

    <div class="login-main">
      <!-- 左侧：悬浮的系统介绍与特色区 -->
      <div class="login-left">
        <div class="left-content fade-in">
          <div class="brand-header">
            <h1 class="brand-title-cn">人口数据库管理系统</h1>
            <p class="brand-title-en">Population Database Management System</p>
          </div>

          <div class="sub-title-wrapper slide-in">
            <h2>基层人口信息统一管理与分析平台</h2>
            <div class="sub-title-line"></div>
          </div>

          <div class="feature-grid">
            <div class="feature-item delay-1">
              <el-icon class="feature-icon"><UserFilled /></el-icon>
              <span>人口信息管理</span>
            </div>
            <div class="feature-item delay-2">
              <el-icon class="feature-icon"><HomeFilled /></el-icon>
              <span>户口管理</span>
            </div>
            <div class="feature-item delay-3">
              <el-icon class="feature-icon"><Sort /></el-icon>
              <span>迁入迁出管理</span>
            </div>
            <div class="feature-item delay-4">
              <el-icon class="feature-icon"><Postcard /></el-icon>
              <span>证件管理</span>
            </div>
            <div class="feature-item delay-5">
              <el-icon class="feature-icon"><DataAnalysis /></el-icon>
              <span>数据统计分析</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：悬浮在背景上的纯白表单卡片 -->
      <div class="login-right">
        <div class="login-card fade-in-up">
          <div class="card-header">
            <h2>欢迎登录</h2>
            <p>请使用账号密码登录系统</p>
          </div>

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
            class="strict-form"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="系统管理员账号"
                clearable
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="登录密码"
                show-password
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <div class="form-actions-area">
              <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
            </div>

            <el-form-item class="submit-form-item">
              <el-button
                type="primary"
                :loading="loading"
                class="login-button"
                @click="handleLoginSubmit"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="mock-tip">
            <el-icon><CircleCheck /></el-icon>
            <span>测试账号: admin / 123456</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部统一 Footer -->
    <footer class="login-footer">
      <div class="footer-left">
        <el-icon><StarFilled /></el-icon> 依法管理人口信息 &nbsp; 服务社会治理现代化
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
.login-wrapper {
  width: 100vw;
  height: 100vh;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

/* 全屏背景图与遮罩 */
.tech-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('../assets/images/login-bg.png');
  background-size: cover;
  background-position: center;
  z-index: 1;
}
.tech-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* 深邃的皇家蓝渐变，覆盖全屏 */
  background: linear-gradient(135deg, rgba(30, 64, 175, 0.85) 0%, rgba(15, 23, 42, 0.95) 100%);
  z-index: 2;
}

/* 主容器 */
.login-main {
  flex: 1;
  display: flex;
  position: relative;
  z-index: 10; /* 在背景图之上 */
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

/* ================= 左侧：文本区 ================= */
.login-left {
  flex: 5.5; 
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 40px 0 60px;
  color: #ffffff;
}
.left-content {
  max-width: 600px;
}

.brand-header {
  margin-bottom: 40px;
}
.brand-title-cn {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 2px;
  margin: 0 0 8px 0;
  color: #ffffff;
}
.brand-title-en {
  font-size: 14px;
  font-family: var(--font-system);
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 1.5px;
  margin: 0;
  text-transform: uppercase;
}

.sub-title-wrapper {
  margin-bottom: 50px;
}
.sub-title-wrapper h2 {
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 1.5px;
  margin: 0 0 16px 0;
  color: #ffffff;
}
.sub-title-line {
  width: 40px;
  height: 4px;
  background-color: #60a5fa; 
}

/* 功能网格 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.06); 
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-base);
  transition: background var(--transition-base), border-color var(--transition-base);
}
.feature-item:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.3);
}
.feature-icon {
  font-size: 20px;
  color: #93c5fd;
}
.feature-item span {
  font-size: 14px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.95);
}

/* ================= 右侧：悬浮卡片区 ================= */
.login-right {
  flex: 4.5; 
  display: flex;
  justify-content: center;
  align-items: center;
  padding-right: 60px;
}

/* 登录表单区 - 悬浮在背景之上 */
.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-surface); 
  padding: 48px 40px;
  border-radius: var(--radius-large);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3); /* 强烈的阴影让白色卡片立体感十足，不再是死板的拼接 */
}

.card-header {
  margin-bottom: 36px;
}
.card-header h2 {
  font-size: 26px;
  color: var(--color-ink);
  margin: 0 0 8px 0;
  font-weight: 700;
}
.card-header p {
  font-size: 14px;
  color: var(--color-ink-muted);
  margin: 0;
}

/* 错误提示 */
.error-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  color: var(--color-danger);
  padding: 12px 16px;
  border-radius: var(--radius-base);
  margin-bottom: 24px;
  font-size: 14px;
}

:deep(.el-input__wrapper) {
  padding: 8px 12px;
}
:deep(.el-input__inner) {
  height: 32px;
}

.form-actions-area {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 24px;
  margin-top: -8px;
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 4px;
  font-weight: 600;
}

.mock-tip {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
  color: var(--color-ink-muted);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ================= 底部区域 ================= */
.login-footer {
  height: 48px;
  background: transparent; /* 背景透明，融入全屏底色 */
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.6);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  font-size: 13px;
  z-index: 20;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.divider {
  margin: 0 8px;
  color: rgba(255, 255, 255, 0.2);
}

.fade-in { animation: fadeIn 0.6s ease forwards; }
.fade-in-up { animation: fadeInUp 0.4s ease forwards; opacity: 0; transform: translateY(10px); }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeInUp { to { opacity: 1; transform: translateY(0); } }
.delay-1 { animation: fadeIn 0.4s ease 0.1s forwards; opacity: 0; }
.delay-2 { animation: fadeIn 0.4s ease 0.15s forwards; opacity: 0; }
.delay-3 { animation: fadeIn 0.4s ease 0.2s forwards; opacity: 0; }
.delay-4 { animation: fadeIn 0.4s ease 0.25s forwards; opacity: 0; }
.delay-5 { animation: fadeIn 0.4s ease 0.3s forwards; opacity: 0; }

@media (max-width: 1024px) {
  .login-left { display: none; }
  .login-right { flex: 1; padding: 0 20px; }
}
</style>
