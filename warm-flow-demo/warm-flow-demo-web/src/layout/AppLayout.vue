<template>
  <el-container class="app-shell">
    <el-aside width="226px" class="app-aside" :class="{ 'is-open': drawerOpen }">
      <div class="app-brand">
        <span class="logo-mark" aria-hidden="true">W</span>
        <div class="brand-text">
          <b>Warm-Flow</b>
          <span>Workflow Demo</span>
        </div>
      </div>

      <el-menu :default-active="menuActive" router class="side-menu">
        <el-menu-item index="/dashboard">
          <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="3" y="3" width="7.5" height="8.5" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.8" />
            <rect x="13.5" y="3" width="7.5" height="5" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.8" />
            <rect x="13.5" y="11" width="7.5" height="10" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.8" />
            <rect x="3" y="14.5" width="7.5" height="6.5" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.8" />
          </svg>
          工作台
        </el-menu-item>

        <el-menu-item-group title="流程">
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
        </el-menu-item-group>

        <el-menu-item-group title="任务">
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
        </el-menu-item-group>

        <el-menu-item-group title="调试工具">
          <el-menu-item index="/events">
            <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
              <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M13 2 4.5 13.5H11L9.5 22 19 9.5h-6.5z" />
            </svg>
            事件测试
          </el-menu-item>
          <el-menu-item index="/timers">
            <svg class="nav-ico" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" />
              <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M12 7v5.2l3.2 1.9" />
            </svg>
            定时任务
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
        </el-menu-item-group>
      </el-menu>

      <div class="aside-foot">Warm-Flow v2.0 · 本地演示</div>
    </el-aside>

    <button
      v-if="drawerOpen"
      class="aside-mask"
      aria-label="关闭导航菜单"
      @click="drawerOpen = false"
    />

    <el-container class="app-right">
      <el-header class="app-header">
        <div class="header-left">
          <button
            class="icon-button menu-toggle"
            aria-label="打开导航菜单"
            @click="drawerOpen = true"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </button>
          <div class="header-crumb">
            <b>{{ pageTitle }}</b>
            <span>{{ pageDesc }}</span>
          </div>
        </div>

        <div class="header-actions">
          <el-dropdown trigger="click" @command="onThemeCommand">
            <button class="icon-button" :aria-label="`外观：${theme.modeLabel}，点击切换`">
              <svg v-if="theme.isDark" viewBox="0 0 24 24" aria-hidden="true">
                <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
              </svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" stroke-width="1.8" />
                <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
              </svg>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light" :disabled="theme.mode === 'light'">亮色</el-dropdown-item>
                <el-dropdown-item command="dark" :disabled="theme.mode === 'dark'">暗色</el-dropdown-item>
                <el-dropdown-item command="auto" :disabled="theme.mode === 'auto'">跟随系统</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click" @command="switchUser">
            <div class="user-session" role="button" :aria-label="`当前身份 ${displayName}，点击切换`">
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
import { useThemeStore, type ThemeMode } from '../stores/theme'
import { listUsers, type DemoUser } from '../api/users'

const session = useSessionStore()
const theme = useThemeStore()
const route = useRoute()
const router = useRouter()
const users = ref<DemoUser[]>([])
const drawerOpen = ref(false)

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

const PAGE_META: Record<string, { title: string; desc: string }> = {
  dashboard: { title: '工作台', desc: '待办概览、快捷发起与近期动态' },
  definitions: { title: '流程定义', desc: '管理流程模型、版本与发布状态' },
  design: { title: '流程设计', desc: '拖拽编排流程节点与连线' },
  instances: { title: '流程实例', desc: '查询与发起流程实例' },
  instanceDetail: { title: '实例详情', desc: '查看流程图、办理历史与流程变量' },
  todo: { title: '我的待办', desc: '办理分配给你的流程任务' },
  done: { title: '我的已办', desc: '你已处理完成的历史任务' },
  copy: { title: '我的抄送', desc: '他人办理时抄送给你的记录' },
  events: { title: '事件测试', desc: '消息启动、消息捕获与错误事件调试' },
  timers: { title: '定时任务', desc: '观测引擎事件等待记录与触发状态' },
  users: { title: '用户测试', desc: '查看演示用户与部门数据' },
}

const pageTitle = computed(() => resolveMeta().title)

const pageDesc = computed(() => resolveMeta().desc)

function resolveMeta() {
  const path = route.path
  if (path.startsWith('/definitions/design')) return PAGE_META.design
  if (path.startsWith('/definitions')) return PAGE_META.definitions
  if (/^\/instances\/[^/]+/.test(path)) return PAGE_META.instanceDetail
  if (path.startsWith('/instances')) return PAGE_META.instances
  if (path.startsWith('/todo')) return PAGE_META.todo
  if (path.startsWith('/done')) return PAGE_META.done
  if (path.startsWith('/copy')) return PAGE_META.copy
  if (path.startsWith('/events')) return PAGE_META.events
  if (path.startsWith('/timers')) return PAGE_META.timers
  if (path.startsWith('/users')) return PAGE_META.users
  return PAGE_META.dashboard
}

async function loadUsers() {
  try {
    users.value = await listUsers()
  } catch {
    users.value = []
  }
}

function onThemeCommand(mode: ThemeMode) {
  theme.setMode(mode)
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

watch(
  () => route.path,
  () => {
    drawerOpen.value = false
    session.refreshTodoCount()
  },
)

onMounted(() => {
  loadUsers()
  session.refreshTodoCount()
})
</script>
