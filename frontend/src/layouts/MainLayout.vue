<template>
  <div class="layout-container">
    <header class="navbar" v-if="!isImmersive">
      <div class="logo-area">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="system-title">人口数据库管理系统</span>
        <span class="system-badge">管理后台</span>
      </div>
      <div class="user-area" v-if="userStore.isLoggedIn">
        <span class="username">
          欢迎您，{{ userStore.displayName }}
          <el-tag color="#f56c6c" size="small" effect="dark" class="role-badge" style="border: none;">
            {{ userStore.roleLabel }}
          </el-tag>
        </span>
        <el-button class="logout-btn" text @click="handleLogout">
          <el-icon class="logout-icon"><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </header>

    <div class="main-layout">
      <aside class="sidebar" v-if="!isImmersive">
        <el-menu
          router
          :default-active="activeMenu"
          class="side-menu"
        >
          <!-- 动态渲染菜单 -->
          <template v-for="group in menuGroups" :key="group.name">
            <!-- 单个菜单项（无分组名或独立项，例如工作台） -->
            <template v-if="!group.name || group.name === '工作台'">
              <el-menu-item
                v-for="item in group.children"
                :key="item.path"
                :index="item.path"
              >
                <el-icon><component :is="item.menuIcon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </template>
            <!-- 分组子菜单 -->
            <el-sub-menu v-else :index="group.name">
              <template #title>
                <el-icon><component :is="group.icon" /></el-icon>
                <span>{{ group.name }}</span>
              </template>
              <el-menu-item
                v-for="item in group.children"
                :key="item.path"
                :index="item.path"
              >
                <el-icon><component :is="item.menuIcon" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
        <div class="sidebar-footer">
          <p>系统版本 V1.0</p>
          <p>东软政府事业部</p>
        </div>
      </aside>

      <main class="main-content" :class="{ 'is-immersive': isImmersive }">
        <!-- 面包屑导航 -->
        <div class="breadcrumb-container" v-if="$route.path !== '/home' && !isImmersive">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/home' }">工作台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.group">{{ $route.meta.group }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="page-wrapper">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw } from 'vue';
import {
  Platform,
  User,
  HomeFilled,
  Switch,
  Postcard,
  Setting,
  SwitchButton,
  StarFilled,
  UserFilled,
  Menu,
  Document,
  Finished,
  Collection,
  Search,
  DataAnalysis,
  TrendCharts,
  Download,
  CircleClose,
  Location
} from '@element-plus/icons-vue';
import { useUserStore } from '../stores/user';
import { ROLE_BADGE_TYPE } from '../constants/roles';
import { useRouter, useRoute } from 'vue-router';
import { ElMessageBox, ElMessage } from 'element-plus';

const userStore = useUserStore();
const router = useRouter();
const route = useRoute();

const isImmersive = computed(() => route.meta.immersive === true);

/** 菜单图标映射：路由 meta.icon 为字符串名，这里转为真实组件 */
const MENU_ICON_MAP = Object.freeze({
  Platform: markRaw(Platform),
  User: markRaw(User),
  HomeFilled: markRaw(HomeFilled),
  Switch: markRaw(Switch),
  Postcard: markRaw(Postcard),
  Setting: markRaw(Setting),
  SwitchButton: markRaw(SwitchButton),
  StarFilled: markRaw(StarFilled),
  UserFilled: markRaw(UserFilled),
  Menu: markRaw(Menu),
  Document: markRaw(Document),
  Finished: markRaw(Finished),
  Collection: markRaw(Collection),
  Search: markRaw(Search),
  DataAnalysis: markRaw(DataAnalysis),
  TrendCharts: markRaw(TrendCharts),
  Download: markRaw(Download),
  CircleClose: markRaw(CircleClose),
  Location: markRaw(Location)
});

function resolveMenuIcon(name) {
  return MENU_ICON_MAP[name] || MENU_ICON_MAP.Menu;
}

const roleBadgeType = computed(() => ROLE_BADGE_TYPE[userStore.roleCode] || 'info');

const activeMenu = computed(() => {
  return route.meta.activeMenu || route.path;
});

const menuGroups = computed(() => {
  const routes = router.getRoutes();

  // 过滤出需要显示在菜单中并且当前角色有权限访问的路由
  const visibleRoutes = routes.filter(r =>
    r.meta &&
    r.meta.menu === true &&
    userStore.canAccess(r.meta)
  );

  // 按照 order 排序
  visibleRoutes.sort((a, b) => (a.meta.order || 99) - (b.meta.order || 99));

  // 分组
  const groups = {};
  visibleRoutes.forEach(r => {
    const groupName = r.meta.group || '';
    if (!groups[groupName]) {
      groups[groupName] = {
        name: groupName,
        icon: resolveMenuIcon(getGroupIcon(groupName) || r.meta.icon),
        children: []
      };
    }
    groups[groupName].children.push({
      ...r,
      menuIcon: resolveMenuIcon(r.meta.icon)
    });
  });

  const result = Object.values(groups);

  result.sort((a, b) => {
    if (a.name === '工作台' || a.name === '') return -1;
    if (b.name === '工作台' || b.name === '') return 1;
    return 0;
  });

  return result;
});

function getGroupIcon(groupName) {
  const map = {
    '人口户籍': 'User',
    '业务办理': 'Switch',
    '扩展业务': 'StarFilled',
    '查询统计': 'DataAnalysis',
    '系统管理': 'Setting'
  };
  return map[groupName] || 'Menu';
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录并返回登录页面吗？', '系统安全提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      let completed = false
      try {
        await userStore.logout()
        completed = true
      } finally {
        await router.replace('/login')
      }
      if (completed) ElMessage.success('已安全退出系统')
    })
    .catch(() => {});
};
</script>

<script>
// 图标组件注册供动态解析
import { 
  Platform, User, HomeFilled, Switch, Postcard, Setting, SwitchButton, StarFilled, UserFilled, Menu, Document, Finished, Collection, Search, DataAnalysis, TrendCharts
} from '@element-plus/icons-vue';

export default {
  components: {
    Platform, User, HomeFilled, Switch, Postcard, Setting, SwitchButton, StarFilled, UserFilled, Menu, Document, Finished, Collection, Search, DataAnalysis, TrendCharts
  }
}
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--el-bg-color-page, #f5f7fa);
}

.navbar {
  height: 65px;
  flex: 0 0 65px;
  background: linear-gradient(100deg, #0a1b33 0%, #112640 100%);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px 0 30px;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);
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
  font-size: 21px;
  font-weight: 700;
  letter-spacing: .5px;
}

.system-badge {
  font-size: 12px;
  background: rgba(255,255,255,0.25);
  padding: 4px 14px;
  border-radius: 14px;
  margin-left: 4px;
  color: rgba(255,255,255,0.9);
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
  gap: 12px;
}

.role-badge {
  font-size: 12px;
  border-radius: 4px;
}

.logout-btn {
  color: rgba(255,255,255,0.85);
}
.logout-btn:hover {
  color: white;
  background-color: rgba(255, 255, 255, 0.1);
}

.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 254px;
  flex: 0 0 254px;
  background-color: white;
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  display: flex;
  flex-direction: column;
}

.side-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  padding: 20px 15px 0;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 52px;
  margin-bottom: 7px;
  padding: 0 17px !important;
  border-radius: 8px;
  color: #263b59;
  font-size: 15px;
}

:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) {
  font-size: 20px;
  margin-right: 14px;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(100deg, #edf4ff 0%, #e9f0fd 100%) !important;
  color: #2861d5 !important;
}

:deep(.el-menu-item.is-active)::before {
  display: none;
}

.sidebar-footer {
  padding: 20px 16px 22px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  text-align: center;
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
  background: linear-gradient(180deg, #fbfcfe 0%, #f4f7fb 100%);
}

.sidebar-footer p {
  margin: 4px 0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #f4f7fb;
  min-width: 0; /* 修复 flex 子项内容过宽导致溢出的问题 */
  overflow: hidden;
}

.breadcrumb-container {
  padding: 16px 24px 0 24px;
  background-color: #f0f2f5;
}

.page-wrapper {
  flex: 1;
  padding: 22px;
  overflow: auto; /* 允许横向和纵向滚动 */
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.page-wrapper::-webkit-scrollbar {
  width: 6px;
}

.page-wrapper::-webkit-scrollbar-thumb {
  background: transparent;
}

/* 沉浸式大屏模式样式 */
.main-content.is-immersive {
  background-color: #020f28;
}

.main-content.is-immersive .page-wrapper {
  padding: 0;
  overflow: hidden;
}
</style>
