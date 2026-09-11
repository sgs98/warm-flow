<template>
  <el-container class="app-shell">
    <el-aside width="226px" class="app-aside">
      <div class="app-brand">
        <span class="logo-mark">W</span>
        <div class="brand-text">
          <b>Warm-Flow</b>
          <span>Workflow Demo</span>
        </div>
      </div>

      <el-menu :default-active="$route.path" router class="side-menu">
        <el-menu-item index="/definitions">流程定义</el-menu-item>
        <el-menu-item index="/instances">流程实例</el-menu-item>
        <el-menu-item index="/todo">我的待办</el-menu-item>
        <el-menu-item index="/done">我的已办</el-menu-item>
        <el-menu-item index="/copy">我的抄送</el-menu-item>
        <el-menu-item index="/users">用户测试</el-menu-item>
      </el-menu>

      <div class="aside-foot">Warm-Flow v1.8.9 · 本地演示</div>
    </el-aside>

    <el-container class="app-right">
      <el-header class="app-header">
        <div class="page-crumb">{{ pageTitle }}</div>
        <div class="user-session">
          <span class="user-avatar">{{ session.currentUser.charAt(0).toUpperCase() }}</span>
          <span class="user-name">{{ session.currentUser }}</span>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()

const pageTitle = computed(() => {
  const path = route.path
  if (path.startsWith('/definitions/design')) return '流程设计'
  if (path.startsWith('/definitions')) return '流程定义'
  if (path.startsWith('/instances')) return '流程实例'
  if (path.startsWith('/todo')) return '我的待办'
  if (path.startsWith('/done')) return '我的已办'
  if (path.startsWith('/copy')) return '我的抄送'
  if (path.startsWith('/users')) return '用户测试'
  return 'Warm-Flow Demo'
})

function logout() {
  session.clearCurrentUser()
  router.push('/login')
}
</script>

<style scoped>
/* 布局结构样式在 src/styles/index.scss 中以全局设计令牌统一维护 */
</style>
