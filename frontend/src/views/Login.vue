<template>
  <div class="login-wrapper">
    <!-- 全屏背景视频层 -->
    <video
      class="tech-bg-video"
      autoplay
      muted
      loop
      playsinline
      poster="../assets/images/login-bg.png"
    >
      <source src="../assets/videos/login-bg.mp4" type="video/mp4" />
    </video>

    <!-- 背景图片(作为fallback)与遮罩层 -->
    <div class="tech-bg"></div>
    <div class="tech-overlay"></div>
    <div class="card-safe-overlay"></div>

    <div class="login-main">
      <!-- 左侧：系统介绍与功能复刻网格 -->
      <div class="login-left">
        <div class="left-content fade-in">
          <div class="brand-header">
            <h1 class="brand-title-cn">人口数据库管理系统</h1>
            <p class="brand-title-en">Population Database Management System</p>
            <div class="sub-title-line"></div>
          </div>

          <div class="sub-title-wrapper slide-in">
            <h2>基层人口信息统一管理与分析平台</h2>
          </div>

          <!-- 3列网格功能卡片，完美复刻参考图 -->
          <div class="feature-grid">
            <div class="feature-card delay-1">
              <el-icon class="feature-icon"><User /></el-icon>
              <div class="feature-title">人口信息管理</div>
              <div class="feature-desc">统一管理人口基础信息</div>
            </div>
            <div class="feature-card delay-2">
              <el-icon class="feature-icon"><HomeFilled /></el-icon>
              <div class="feature-title">户口管理</div>
              <div class="feature-desc">户籍信息全流程管理</div>
            </div>
            <div class="feature-card delay-3">
              <el-icon class="feature-icon"><Switch /></el-icon>
              <div class="feature-title">迁入迁出管理</div>
              <div class="feature-desc">人口流动动态管理</div>
            </div>
            <div class="feature-card delay-4">
              <el-icon class="feature-icon"><Postcard /></el-icon>
              <div class="feature-title">证件管理</div>
              <div class="feature-desc">证件信息集中管理</div>
            </div>
            <div class="feature-card delay-5">
              <el-icon class="feature-icon"><TrendCharts /></el-icon>
              <div class="feature-title">数据统计分析</div>
              <div class="feature-desc">多维数据分析与可视化</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：悬浮在背景上的纯白登录卡片 -->
      <div class="login-right">
        <div class="login-card fade-in-up">
          <!-- 顶部加盖深蓝色官方象征徽章 -->
          <div class="badge-container">
            <img class="emblem-img" src="../assets/images/login-badge.png" alt="徽章" />
          </div>

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
        <el-icon><CircleCheck /></el-icon> 依法管理人口信息 &nbsp; 服务社会治理现代化
      </div>
      <div class="footer-right">
        © 2026 Population Database Management System 版权所有
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { 
  User, Lock, Warning, StarFilled, UserFilled, HomeFilled, Switch, Postcard, TrendCharts, CircleCheck
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();
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
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password,
      });

      if (rememberMe.value) {
        localStorage.setItem('saved_username', loginForm.username);
      } else {
        localStorage.removeItem('saved_username');
      }

      ElMessage.success({ message: '登录成功', duration: 1500 });
      router.push('/home');
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

/* 视频背景层 */
.tech-bg-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 1;
  transform-origin: top left;
  transform: scale(1.12);
}

/* 全屏背景图与遮罩 */
.tech-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background-image: url('../assets/images/login-bg.png');
  background-size: cover;
  background-position: center;
  z-index: 0;
}
.tech-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 24% 32%, rgba(96, 165, 250, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(30, 64, 175, 0.2) 0%, rgba(15, 23, 42, 0.45) 100%);
  z-index: 2;
}
.card-safe-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, transparent 0%, rgba(15, 23, 42, 0.12) 52%, rgba(15, 23, 42, 0.36) 100%);
  z-index: 3;
  pointer-events: none;
}

/* 主容器 */
.login-main {
  flex: 1;
  display: flex;
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 1300px;
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
  max-width: 720px;
}

.brand-header {
  margin-bottom: 24px;
}
.brand-title-cn {
  font-size: 42px;
  font-weight: 700;
  letter-spacing: 3px;
  margin: 0 0 10px 0;
  color: #ffffff;
}
.brand-title-en {
  font-size: 13px;
  font-family: var(--font-system);
  color: rgba(255, 255, 255, 0.65);
  letter-spacing: 1.8px;
  margin: 0 0 16px 0;
  text-transform: uppercase;
}
.sub-title-line {
  width: 44px;
  height: 3px;
  background-color: #2563eb; 
}

.sub-title-wrapper {
  margin-bottom: 40px;
}
.sub-title-wrapper h2 {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 2px;
  margin: 0;
  color: #ffffff;
}

/* 功能网格 - 复刻参考图3列网格 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  max-width: 660px;
}
.feature-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: rgba(15, 23, 42, 0.4); 
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  transition: all var(--transition-base);
  text-align: center;
}
.feature-card:hover {
  background: rgba(15, 23, 42, 0.6);
  border-color: rgba(96, 165, 250, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
}
.feature-icon {
  font-size: 28px;
  color: #3b82f6; 
  margin-bottom: 12px;
}
.feature-title {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}
.feature-desc {
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.4;
}

/* ================= 右侧：悬浮卡片区 ================= */
.login-right {
  flex: 4.5; 
  display: flex;
  justify-content: center;
  align-items: center;
  padding-right: 40px;
}

/* 登录表单区 - 悬浮在背景之上 */
.login-card {
  width: 100%;
  max-width: 410px;
  background: #ffffff; 
  padding: 44px 38px;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.35);
}

/* 顶部标志徽章 */
.badge-container {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.emblem-img {
  width: 72px;
  height: 72px;
  object-fit: contain;
}

.card-header {
  margin-bottom: 30px;
  text-align: center;
}
.card-header h2 {
  font-size: 25px;
  color: var(--color-ink);
  margin: 0 0 6px 0;
  font-weight: 700;
  letter-spacing: 1px;
}
.card-header p {
  font-size: 13.5px;
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
  margin-bottom: 20px;
  font-size: 13.5px;
}

:deep(.el-input__wrapper) {
  padding: 8px 14px;
  border-radius: 6px;
}
:deep(.el-input__inner) {
  height: 32px;
}

.form-actions-area {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 24px;
  margin-top: -6px;
}

.login-button {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 6px;
  font-weight: 600;
  background-color: #1e40af;
  border-color: #1e40af;
  border-radius: 6px;
}
.login-button:hover {
  background-color: #1d4ed8;
  border-color: #1d4ed8;
}

.mock-tip {
  margin-top: 28px;
  padding-top: 18px;
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
  background: transparent;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.6);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 50px;
  font-size: 13px;
  z-index: 20;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.footer-left .el-icon {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.7);
}

.fade-in { animation: fadeIn 0.6s ease forwards; }
.fade-in-up { animation: fadeInUp 0.4s ease forwards; opacity: 0; transform: translateY(10px); }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeInUp { to { opacity: 1; transform: translateY(0); } }
.delay-1 { animation: fadeIn 0.4s ease 0.1s forwards; opacity: 0; }
.delay-2 { animation: fadeIn 0.4s ease 0.14s forwards; opacity: 0; }
.delay-3 { animation: fadeIn 0.4s ease 0.18s forwards; opacity: 0; }
.delay-4 { animation: fadeIn 0.4s ease 0.22s forwards; opacity: 0; }
.delay-5 { animation: fadeIn 0.4s ease 0.26s forwards; opacity: 0; }

@media (max-width: 1024px) {
  .login-left { display: none; }
  .login-right { flex: 1; padding: 0 20px; }
  .login-footer { padding: 0 20px; }
}
</style>
