<template>
  <div id="app">
    <el-container class="layout-container">
      <!-- 侧边栏 -->
      <el-aside width="250px" class="sidebar">
        <div class="logo">
          <h2>FS外呼系统</h2>
        </div>
        <el-menu
          :default-active="$route.path"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Monitor /></el-icon>
            <span>仪表板</span>
          </el-menu-item>
          <el-menu-item index="/calls">
            <el-icon><Phone /></el-icon>
            <span>通话管理</span>
          </el-menu-item>
          <el-menu-item index="/agents">
            <el-icon><User /></el-icon>
            <span>坐席管理</span>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <span>外呼任务</span>
          </el-menu-item>
          <el-menu-item index="/customers">
            <el-icon><UserFilled /></el-icon>
            <span>客户管理</span>
          </el-menu-item>
          <el-menu-item index="/statistics">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-container>
        <!-- 顶部导航 -->
        <el-header class="header">
          <div class="header-left">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="header-right">
            <el-badge :value="systemStatus.queue_length" class="item">
              <el-button type="text">
                <el-icon><Bell /></el-icon>
              </el-button>
            </el-badge>
            <el-dropdown>
              <span class="el-dropdown-link">
                管理员
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item>个人设置</el-dropdown-item>
                  <el-dropdown-item divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <!-- 主要内容 -->
        <el-main class="main-content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <!-- 全局状态指示器 -->
    <div class="status-indicator">
      <el-tag :type="connectionStatus === 'connected' ? 'success' : 'danger'" size="small">
        {{ connectionStatus === 'connected' ? '已连接' : '连接断开' }}
      </el-tag>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useSystemStore } from './stores/system'
import { useWebSocketStore } from './stores/websocket'

const route = useRoute()
const systemStore = useSystemStore()
const websocketStore = useWebSocketStore()

const systemStatus = computed(() => systemStore.status)
const connectionStatus = computed(() => websocketStore.connectionStatus)

const currentPageTitle = computed(() => {
  const titleMap = {
    '/dashboard': '仪表板',
    '/calls': '通话管理',
    '/agents': '坐席管理',
    '/tasks': '外呼任务',
    '/customers': '客户管理',
    '/statistics': '数据统计',
    '/settings': '系统设置'
  }
  return titleMap[route.path] || '未知页面'
})

onMounted(() => {
  // 初始化WebSocket连接
  websocketStore.connect()
  
  // 定期获取系统状态
  systemStore.startStatusPolling()
})

onUnmounted(() => {
  websocketStore.disconnect()
  systemStore.stopStatusPolling()
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4b;
  color: white;
  margin-bottom: 0;
}

.logo h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.main-content {
  background-color: #f5f5f5;
  padding: 20px;
}

.status-indicator {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 1000;
}

.el-dropdown-link {
  cursor: pointer;
  color: #409EFF;
  display: flex;
  align-items: center;
}
</style>