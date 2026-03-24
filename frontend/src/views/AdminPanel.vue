<template>
  <el-container class="app-wrapper">
    <el-aside width="240px" class="sidebar-container">
      <div class="sidebar-logo">
        <el-icon :size="28" style="margin-right: 10px"><Setting /></el-icon>
        <transition name="el-fade-in">
          <span class="logo-title">Admin Console</span>
        </transition>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        background-color="#001529"
        text-color="rgba(255,255,255,0.7)"
        active-text-color="#ffffff"
        @select="onMenuSelect"
      >
        <el-menu-item index="users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="files">
          <el-icon><Folder /></el-icon>
          <span>文件管理</span>
        </el-menu-item>
        <el-menu-item index="recycle">
          <el-icon><Delete /></el-icon>
          <span>回收站</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container class="main-container">
      <el-header class="navbar">
        <div class="navbar-left">
          <h2 class="page-title">{{ pageTitle }}</h2>
        </div>
        <div class="navbar-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-wrapper">
              <el-avatar :size="32" class="user-avatar" :src="avatarUrl" :icon="UserFilled" :style="{ backgroundColor: '#F56C6C' }" />
              <span class="user-name">管理员: {{ displayName }}</span>
              <el-icon class="el-icon--right"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item divided command="logout" style="color: #f56c6c;">退出管理后台</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main class="app-main">
        <transition name="fade-transform" mode="out-in">
          <keep-alive>
            <component :is="currentComponent" :key="activeMenu" />
          </keep-alive>
        </transition>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { User, Folder, Delete, Setting, UserFilled, CaretBottom } from '@element-plus/icons-vue';
import AdminUsers from './AdminUsers.vue';
import AdminFiles from './AdminFiles.vue';
import AdminRecycle from './AdminRecycle.vue';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const activeMenu = ref('users');

const { displayName, avatarUrl, logout } = useUser('admin');

const currentComponent = computed(() => {
  if (activeMenu.value === 'users') return AdminUsers;
  if (activeMenu.value === 'files') return AdminFiles;
  return AdminRecycle;
});

const pageTitle = computed(() => {
  if (activeMenu.value === 'users') return '用户管理';
  if (activeMenu.value === 'files') return '文件管理';
  return '回收站';
});

const onMenuSelect = (index) => {
  activeMenu.value = index;
};

const handleCommand = (command) => {
  if (command === 'logout') {
    logout();
  }
};
</script>

<style scoped>
.app-wrapper {
  height: 100vh;
  width: 100%;
}

.sidebar-container {
  background-color: #001529;
  height: 100%;
  box-shadow: 2px 0 6px rgba(0,21,41,0.35);
  z-index: 10;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 60px;
  line-height: 60px;
  background: #002140;
  text-align: center;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sidebar-menu {
  border: none;
  flex: 1;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: #1890ff !important;
  color: white !important;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: #000c17 !important;
  color: white !important;
}

.main-container {
  background-color: #f0f2f5;
  display: flex;
  flex-direction: column;
}

.navbar {
  height: 60px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  color: #303133;
}

.navbar-right {
  display: flex;
  align-items: center;
}

.avatar-wrapper {
  margin-top: 5px;
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: #606266;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

/* 路由切换动画 */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
