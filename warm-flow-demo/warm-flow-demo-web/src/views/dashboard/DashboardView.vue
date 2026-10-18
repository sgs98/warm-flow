<template>
  <div class="dash-page">
    <!-- 欢迎区 + 快捷发起 -->
    <section class="dash-hero">
      <div class="dash-hero-text">
        <span class="eyebrow">Workflow Console</span>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p>
          {{ todayText }} · 当前身份 {{ session.currentUser }}<template v-if="deptName">（{{ deptName }}）</template>
          · 在顶栏可随时切换演示身份
        </p>
      </div>
      <div class="dash-hero-actions">
        <el-select
          v-model="startFlowCode"
          filterable
          clearable
          placeholder="选择要发起的流程"
          :loading="defLoading"
        >
          <el-option
            v-for="d in definitions"
            :key="d.id"
            :label="`${d.flowName}（${d.flowCode}）`"
            :value="d.flowCode"
          />
        </el-select>
        <el-input v-model="startBusinessId" placeholder="业务ID（留空自动生成）" clearable />
        <el-button
          type="primary"
          :loading="starting"
          :disabled="!startFlowCode"
          @click="doStart"
        >发起流程</el-button>
      </div>
    </section>

    <!-- 统计卡 -->
    <section class="dash-stats" aria-label="数据概览">
      <button
        v-for="card in statCards"
        :key="card.label"
        class="stat-card"
        type="button"
        @click="go(card.path)"
      >
        <span class="stat-icon" :class="card.tone" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
              :d="card.icon"
            />
          </svg>
        </span>
        <span class="stat-body">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-value">{{ loading ? '—' : card.value }}</span>
          <span class="stat-hint">{{ card.hint }}</span>
        </span>
      </button>
    </section>

    <!-- 主内容 -->
    <div class="dash-grid">
      <div class="dash-col">
        <section class="dash-panel">
          <div class="panel-head">
            <h3>我的待办</h3>
            <span class="panel-sub">共 {{ todoTotal }} 条</span>
            <el-button link type="primary" @click="go('/todo')">进入办理台</el-button>
          </div>

          <div v-if="loading" class="skeleton-stack">
            <el-skeleton v-for="i in 3" :key="i" animated>
              <template #template>
                <el-skeleton-item variant="rect" style="height: 58px; border-radius: 10px" />
              </template>
            </el-skeleton>
          </div>

          <div v-else-if="error" class="state-error">
            <span>概览数据加载失败</span>
            <el-button size="small" type="primary" plain @click="loadAll">重试</el-button>
          </div>

          <el-empty v-else-if="!todos.length" description="当前没有待办，先在上方发起一条流程试试" :image-size="64" />

          <div v-else class="todo-queue">
            <button
              v-for="task in todos"
              :key="task.id"
              class="todo-queue-item"
              type="button"
              @click="openTask(task)"
            >
              <span class="stat-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M9 11l3 3L22 4" />
                  <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                </svg>
              </span>
              <span class="queue-main">
                <span class="queue-title">
                  <strong>{{ task.flowName || '-' }}</strong>
                  <span class="queue-node">{{ task.nodeName || '待办理' }}</span>
                </span>
                <span class="queue-sub">业务ID {{ task.businessId || '-' }} · 到达于 {{ task.createTime || '-' }}</span>
              </span>
              <el-button text type="primary" @click.stop="openTask(task)">办理</el-button>
            </button>
          </div>
        </section>

        <section class="dash-panel">
          <div class="panel-head">
            <h3>最近实例</h3>
            <el-button link type="primary" @click="go('/instances')">查看全部</el-button>
          </div>

          <el-empty v-if="!loading && !recentInstances.length" description="还没有流程实例" :image-size="64" />

          <div v-else class="recent-list">
            <button
              v-for="inst in recentInstances"
              :key="inst.id"
              class="recent-item"
              type="button"
              @click="goDetail(inst.id)"
            >
              <span class="recent-dot" :class="dotClass(inst)" aria-hidden="true" />
              <span class="recent-main">
                <strong>{{ inst.flowName || '-' }}</strong>
                <small>{{ inst.businessId || '-' }} · {{ inst.nodeName || '-' }}</small>
              </span>
              <el-tag size="small" :type="statusTagType(inst.businessStatus, inst.businessStatusName || inst.flowStatusName)">
                {{ inst.businessStatusName || inst.flowStatusName }}
              </el-tag>
              <span class="recent-time">{{ shortTime(inst.createTime) }}</span>
            </button>
          </div>
        </section>
      </div>

      <div class="dash-col">
        <section class="dash-panel">
          <div class="panel-head">
            <h3>定时任务</h3>
            <el-button link type="primary" @click="go('/timers')">明细</el-button>
          </div>

          <div v-if="loading" class="skeleton-stack">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="rect" style="height: 96px; border-radius: 10px" />
              </template>
            </el-skeleton>
          </div>

          <div v-else class="timer-grid">
            <div class="timer-cell">
              <b>{{ timerSummary?.waiting ?? 0 }}</b>
              <span>等待中</span>
            </div>
            <div class="timer-cell">
              <b>{{ timerSummary?.running ?? 0 }}</b>
              <span>执行中</span>
            </div>
            <div class="timer-cell">
              <b>{{ timerSummary?.retry ?? 0 }}</b>
              <span>待重试</span>
            </div>
            <div class="timer-cell">
              <b>{{ timerSummary?.dead ?? 0 }}</b>
              <span>已终止</span>
            </div>
          </div>
          <p class="panel-foot">数据来自引擎事件等待表，仅用于观测。</p>
        </section>

        <section class="dash-panel">
          <div class="panel-head">
            <h3>快捷入口</h3>
          </div>
          <div class="shortcut-grid">
            <button
              v-for="item in shortcuts"
              :key="item.path"
              class="shortcut"
              type="button"
              @click="go(item.path)"
            >
              <span class="shortcut-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    :d="item.icon"
                  />
                </svg>
              </span>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </section>
      </div>
    </div>

    <TaskActionDialog v-model="taskDialogVisible" :task="activeTask" @saved="onTaskSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TaskActionDialog, { type TodoTask } from '../../components/tasks/TaskActionDialog.vue'
import { listDefinitions } from '../../api/definitions'
import { pageInstances, startInstance } from '../../api/instances'
import { pageCopyTasks, pageDoneTasks, pageTodoTasks } from '../../api/tasks'
import { getEventWaitSummary } from '../../api/eventWaits'
import { listUsers } from '../../api/users'
import { useSessionStore } from '../../stores/session'
import { statusTagType } from '../../utils/status'
import type { DefinitionOption, EventWaitSummary, InstanceRow, TaskRow } from '../../types/workflow'

const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const error = ref(false)
const defLoading = ref(false)
const starting = ref(false)

const todos = ref<TaskRow[]>([])
const todoTotal = ref(0)
const doneTotal = ref(0)
const copyTotal = ref(0)
const instanceTotal = ref(0)
const definitionTotal = ref(0)
const recentInstances = ref<InstanceRow[]>([])
const timerSummary = ref<EventWaitSummary | null>(null)

const definitions = ref<DefinitionOption[]>([])
const startFlowCode = ref('')
const startBusinessId = ref('')
const users = ref<{ userName: string; realName: string; deptName?: string }[]>([])

const taskDialogVisible = ref(false)
const activeTask = ref<TodoTask | null>(null)

const displayName = computed(
  () => users.value.find((u) => u.userName === session.currentUser)?.realName || session.currentUser,
)

const deptName = computed(
  () => users.value.find((u) => u.userName === session.currentUser)?.deptName,
)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }),
)

const statCards = computed(() => [
  {
    label: '我的待办',
    value: todoTotal.value,
    hint: '等待你处理',
    path: '/todo',
    tone: '',
    icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
  },
  {
    label: '我的已办',
    value: doneTotal.value,
    hint: '累计处理',
    path: '/done',
    tone: 'is-success',
    icon: 'M20 6 9 17l-5-5',
  },
  {
    label: '流程实例',
    value: instanceTotal.value,
    hint: '我可见的实例',
    path: '/instances',
    tone: 'is-indigo',
    icon: 'M12 2 2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
  },
  {
    label: '已发布流程',
    value: definitionTotal.value,
    hint: '可发起的定义',
    path: '/definitions',
    tone: 'is-info',
    icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h5',
  },
  {
    label: '我的抄送',
    value: copyTotal.value,
    hint: '抄送给我的',
    path: '/copy',
    tone: 'is-warning',
    icon: 'M9 9h11v11H9zM5 15V5a2 2 0 0 1 2-2h10',
  },
])

const shortcuts = [
  { label: '发起流程', path: '/instances', icon: 'M12 5v14M5 12h14' },
  { label: '设计流程', path: '/definitions', icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6' },
  { label: '事件测试', path: '/events', icon: 'M13 2 4.5 13.5H11L9.5 22 19 9.5h-6.5z' },
  { label: '定时任务', path: '/timers', icon: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5.2l3.2 1.9' },
]

function go(path: string) {
  router.push(path)
}

function goDetail(id: string) {
  router.push(`/instances/${id}`)
}

function dotClass(row: InstanceRow) {
  const type = statusTagType(row.businessStatus, row.businessStatusName || row.flowStatusName)
  return `is-${type === 'success' ? 'success' : type === 'warning' ? 'warning' : type === 'danger' ? 'danger' : 'info'}`
}

/** 只取日期与时间，去掉秒让列表更紧凑。 */
function shortTime(value?: string) {
  if (!value) return '-'
  return value.length > 16 ? value.slice(5, 16) : value
}

async function loadDefinitions() {
  defLoading.value = true
  try {
    const res = await listDefinitions({ pageNum: 1, pageSize: 100, isPublish: 1 })
    definitions.value = res.list || []
    definitionTotal.value = res.total || 0
  } catch {
    definitions.value = []
    definitionTotal.value = 0
  } finally {
    defLoading.value = false
  }
}

/** 并行拉取各模块概览；单模块失败不影响其余卡片。 */
async function loadAll() {
  loading.value = true
  error.value = false
  try {
    const [todo, done, copy, instances, timer] = await Promise.all([
      pageTodoTasks({ pageNum: 1, pageSize: 5 }),
      pageDoneTasks({ pageNum: 1, pageSize: 1 }),
      pageCopyTasks({ pageNum: 1, pageSize: 1 }),
      pageInstances({ pageNum: 1, pageSize: 5 }),
      getEventWaitSummary({ timerOnly: true }),
    ])

    todos.value = todo.list || []
    todoTotal.value = todo.total || 0
    doneTotal.value = done.total || 0
    copyTotal.value = copy.total || 0
    instanceTotal.value = instances.total || 0
    recentInstances.value = [...(instances.list || [])]
      .sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
      .slice(0, 5)
    timerSummary.value = timer || null

    session.todoCount = todoTotal.value
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    users.value = await listUsers()
  } catch {
    users.value = []
  }
}

function openTask(task: TaskRow) {
  activeTask.value = { ...task }
  taskDialogVisible.value = true
}

async function onTaskSaved() {
  await loadAll()
  session.refreshTodoCount()
}

async function doStart() {
  if (!startFlowCode.value) return
  starting.value = true
  try {
    const res = await startInstance({
      flowCode: startFlowCode.value,
      businessId: startBusinessId.value.trim() || `BIZ-${Date.now()}`,
    })
    startBusinessId.value = ''
    ElMessage.success(res.taskId ? '流程已启动，请办理首节点' : `实例发起成功：${res.instanceId}`)
    await loadAll()
    if (res.taskId) {
      activeTask.value = {
        id: res.taskId,
        instanceId: res.instanceId,
        nodeName: res.nodeName || '首节点',
      }
      taskDialogVisible.value = true
    }
  } catch {
    // 错误提示由 http 层统一处理
  } finally {
    starting.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadDefinitions()
  loadAll()
})
</script>

<style scoped>
.dash-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel-foot {
  margin: 12px 0 0;
  color: var(--wf-text-faint);
  font-size: 12px;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-md);
  background: var(--wf-surface-sunken);
  color: var(--wf-text-regular);
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--wf-duration-fast) var(--wf-ease),
    background-color var(--wf-duration-fast) var(--wf-ease),
    color var(--wf-duration-fast) var(--wf-ease);
}

.shortcut:hover {
  border-color: var(--wf-primary);
  background: var(--wf-primary-soft);
  color: var(--wf-primary);
}

.shortcut-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--wf-primary-soft);
  color: var(--wf-primary);
}

.shortcut-icon svg {
  width: 17px;
  height: 17px;
}

@media (max-width: 420px) {
  .shortcut-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
