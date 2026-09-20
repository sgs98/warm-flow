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

      <el-menu :default-active="menuActive" router class="side-menu">
        <el-menu-item index="/definitions">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M14 2v6h6M8 13h8M8 17h5" />
          </svg>
          流程定义
        </el-menu-item>
        <el-menu-item index="/instances">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M12 2 2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
          </svg>
          流程实例
        </el-menu-item>
        <el-menu-item index="/todo">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M9 11l3 3L22 4" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
          </svg>
          我的待办
          <el-badge v-if="session.todoCount" :value="session.todoCount" class="nav-badge" />
        </el-menu-item>
        <el-menu-item index="/done">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="m8.5 12.5 2.5 2.5 4.5-5" />
          </svg>
          我的已办
        </el-menu-item>
        <el-menu-item index="/copy">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="9" y="9" width="11" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M5 15V5a2 2 0 0 1 2-2h10" />
          </svg>
          我的抄送
        </el-menu-item>
        <el-menu-item index="/users">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="9" cy="8" r="3" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M3.5 19c.6-3 3-5 5.5-5s4.9 2 5.5 5" />
            <circle cx="17" cy="9" r="2.4" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M21.5 19c-.4-2.4-2.1-4-4-4" />
          </svg>
          用户测试
        </el-menu-item>
      </el-menu>

      <div class="aside-foot">Warm-Flow v2.0.0 · 本地演示</div>
    </el-aside>

    <el-container class="app-right">
      <el-header class="app-header">
        <div class="page-crumb">{{ pageTitle }}</div>
        <div class="header-actions">
          <el-dropdown trigger="click" @command="switchUser">
            <div class="user-session">
              <span class="user-avatar">{{ avatarLetter }}</span>
              <span class="user-name">{{ displayName }}</span>
              <svg class="user-caret" viewBox="0 0 12 12" aria-hidden="true">
                <path fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" d="M2.5 4.5 6 8l3.5-3.5" />
              </svg>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="u in users"
                  :key="u.userName"
                  :command="u.userName"
                  :disabled="u.userName === session.currentUser"
                >
                  {{ u.realName }}（{{ u.userName }}）
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view :key="session.currentUser" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../stores/session'
import { httpGet, type DemoUser } from '../api/http'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const users = ref<DemoUser[]>([])

const currentDemoUser = computed(() =>
  users.value.find((u) => u.userName === session.currentUser),
)

const displayName = computed(() => currentDemoUser.value?.realName || session.currentUser)

const avatarLetter = computed(() => {
  const source = currentDemoUser.value?.realName || session.currentUser || '?'
  return source.charAt(0).toUpperCase()
})

const menuActive = computed(() => {
  const path = route.path
  if (path.startsWith('/definitions')) return '/definitions'
  if (path.startsWith('/instances')) return '/instances'
  return path
})

const pageTitle = computed(() => {
  const path = route.path
  if (path.startsWith('/definitions/design')) return '流程设计'
  if (path.startsWith('/definitions')) return '流程定义'
  if (/^\/instances\/[^/]+/.test(path)) return '实例详情'
  if (path.startsWith('/instances')) return '流程实例'
  if (path.startsWith('/todo')) return '我的待办'
  if (path.startsWith('/done')) return '我的已办'
  if (path.startsWith('/copy')) return '我的抄送'
  if (path.startsWith('/users')) return '用户测试'
  return 'Warm-Flow Demo'
})

async function loadUsers() {
  try {
    users.value = await httpGet<DemoUser[]>('/users')
  } catch {
    users.value = []
  }
}

function switchUser(userName: string) {
  if (!userName || userName === session.currentUser) return
  const next = users.value.find((u) => u.userName === userName)
  session.setCurrentUser(userName)
  ElMessage.success(`已切换为 ${next?.realName || userName}`)
  session.refreshTodoCount()
}

function logout() {
  router.push('/login').finally(() => {
    session.clearCurrentUser()
  })
}

watch(() => route.path, () => {
  session.refreshTodoCount()
})

onMounted(() => {
  loadUsers()
  session.refreshTodoCount()
})
</script>
