<template>
  <div class="layout-container">
    <header class="navbar">
      <div class="logo-area">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="system-title">人口数据库管理系统</span>
        <span class="system-badge">管理后台</span>
      </div>
      <div class="user-area" v-if="userStore.isLoggedIn">
        <span class="username">
          欢迎您，{{ userStore.displayName }}
          <span class="role-badge">{{ userStore.roleName === 'admin' ? '系统管理员' : userStore.roleName }}</span>
        </span>
        <el-button class="logout-btn" text @click="handleLogout">
          <el-icon class="logout-icon"><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </header>

    <div class="main-layout">
      <aside class="sidebar">
        <el-menu
          router
          :default-active="$route.path"
          class="side-menu"
        >
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('application:view')" index="/applications">
            <el-icon><Document /></el-icon><span>我的申请</span>
          </el-menu-item>
          <el-sub-menu v-if="userStore.hasPermission('approval:view')" index="approval-center">
            <template #title><el-icon><Finished /></el-icon><span>审批中心</span></template>
            <el-menu-item index="/approvals/pending">待办审批</el-menu-item>
            <el-menu-item index="/approvals/processed">已办审批</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/persons">
            <el-icon><User /></el-icon>
            <span>人口信息管理</span>
          </el-menu-item>
          <el-menu-item index="/households">
            <el-icon><HomeFilled /></el-icon>
            <span>户籍管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('migration:view')" index="/migrations/in">
            <el-icon><Switch /></el-icon>
            <span>迁入管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('migration:view')" index="/migrations/out">
            <el-icon><Switch /></el-icon>
            <span>迁出管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('migration:archive:view')" index="/residence-archives">
            <el-icon><Document /></el-icon><span>历史户籍归档</span>
          </el-menu-item>
          <el-menu-item index="/floating-population">
            <el-icon><User /></el-icon>
            <span>流动人口管理</span>
          </el-menu-item>
          <el-menu-item index="/key-population">
            <el-icon><StarFilled /></el-icon>
            <span>重点人口管理</span>
          </el-menu-item>
          <el-menu-item index="/certificates">
            <el-icon><Postcard /></el-icon>
            <span>证件管理</span>
          </el-menu-item>
          <el-menu-item index="/users">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/dictionary">
            <el-icon><Setting /></el-icon>
            <span>数据字典</span>
          </el-menu-item>
        </el-menu>
        <div class="sidebar-footer">
          <p>系统版本 V1.0</p>
          <p>东软政府事业部</p>
        </div>
      </aside>

      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { 
  Platform, User, HomeFilled, Switch, Postcard, Setting, SwitchButton, StarFilled, UserFilled, Document, Finished
} from '@element-plus/icons-vue';
import { useUserStore } from '../stores/user';
import { useRouter } from 'vue-router';
import { ElMessageBox, ElMessage } from 'element-plus';

const userStore = useUserStore();
const router = useRouter();

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录并返回登录页面吗？', '系统安全提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await userStore.logout();
      router.push('/login');
      ElMessage.success('已安全退出系统');
    })
    .catch(() => {});
};
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--el-bg-color-page, #f5f7fa);
}

.navbar {
  height: 60px;
  background-color: var(--el-color-primary, #409eff);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 24px;
}

.system-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.system-badge {
  font-size: 12px;
  background: rgba(255,255,255,0.2);
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 20px;
}

.username {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-badge {
  font-size: 12px;
  background-color: #f59e0b;
  padding: 2px 6px;
  border-radius: 4px;
  color: white;
}

.logout-btn {
  color: rgba(255,255,255,0.85);
}
.logout-btn:hover {
  color: white;
}

.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  background-color: white;
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  display: flex;
  flex-direction: column;
}

.side-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
}

.sidebar-footer {
  padding: 16px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  text-align: center;
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
  background-color: #fafafa;
}

.sidebar-footer p {
  margin: 4px 0;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: var(--el-bg-color-page, #f5f7fa);
}
</style>
