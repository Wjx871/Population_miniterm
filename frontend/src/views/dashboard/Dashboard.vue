<template>
  <div class="dashboard-container">
    <div class="page-header">
      <div class="header-left">
        <h1>工作台</h1>
        <p class="subtitle">欢迎使用人口数据库管理系统，今日系统运行平稳。</p>
      </div>
    </div>
    
    <div class="stats-grid">
      <el-card shadow="hover" class="stat-card">
        <template #header>
          <div class="card-header">
            <span>系统状态</span>
            <el-icon class="icon-success"><CircleCheck /></el-icon>
          </div>
        </template>
        <div class="stat-value" :class="{'text-success': health === '正常运行', 'text-placeholder': health === '暂无数据'}">
          {{ health }}
        </div>
      </el-card>
      
      <el-card shadow="hover" class="stat-card">
        <template #header>
          <div class="card-header">
            <span>总常驻人口</span>
            <el-icon class="icon-primary"><User /></el-icon>
          </div>
        </template>
        <div class="stat-value" :class="{'text-placeholder': totalPersons === '暂无数据'}">
          {{ totalPersons }}
        </div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <template #header>
          <div class="card-header">
            <span>本月迁入登记</span>
            <el-icon class="icon-warning"><Switch /></el-icon>
          </div>
        </template>
        <div class="stat-value" :class="{'text-placeholder': migrationsIn === '暂无数据'}">
          {{ migrationsIn }}
        </div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <template #header>
          <div class="card-header">
            <span>即将过期证件</span>
            <el-icon class="icon-danger"><Warning /></el-icon>
          </div>
        </template>
        <div class="stat-value" :class="{'text-placeholder': expireSoon === '暂无数据'}">
          {{ expireSoon }}
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { CircleCheck, User, Switch, Warning } from '@element-plus/icons-vue';
import { 
  getSystemHealth, 
  getPersonsStatistics, 
  getMigrationsInStatistics, 
  getCertificatesExpireSoon 
} from '../../api/dashboard';

const health = ref('检查中...');
const totalPersons = ref('加载中...');
const migrationsIn = ref('加载中...');
const expireSoon = ref('加载中...');

onMounted(async () => {
  try {
    const res = await getSystemHealth();
    health.value = res?.status || '正常运行';
  } catch (e) {
    health.value = '暂无数据';
  }

  try {
    const res = await getPersonsStatistics();
    totalPersons.value = res?.total !== undefined ? res.total : '暂无数据';
  } catch (e) {
    totalPersons.value = '暂无数据';
  }

  try {
    const res = await getMigrationsInStatistics();
    migrationsIn.value = res?.monthTotal !== undefined ? res.monthTotal : '暂无数据';
  } catch (e) {
    migrationsIn.value = '暂无数据';
  }

  try {
    const res = await getCertificatesExpireSoon();
    expireSoon.value = res?.count !== undefined ? res.count : '暂无数据';
  } catch (e) {
    expireSoon.value = '暂无数据';
  }
});
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.page-header h1 {
  font-size: 24px;
  margin-bottom: 8px;
}
.subtitle {
  color: var(--color-ink-muted);
  font-size: 14px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.stat-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: var(--color-ink-muted);
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--color-ink);
  margin-top: 10px;
}
.text-success { color: var(--color-success); }
.text-placeholder { color: var(--color-ink-lighter); font-size: 20px; font-weight: normal; }
.icon-success { color: var(--color-success); font-size: 18px; }
.icon-primary { color: var(--color-primary); font-size: 18px; }
.icon-warning { color: var(--color-warning); font-size: 18px; }
.icon-danger { color: var(--color-danger); font-size: 18px; }
</style>
