<template>
  <div class="page-card todo-page">
    <div v-if="!loading && !rows.length" class="todo-workspace-empty">
      <el-empty description="当前没有待办。可发起一条流程，或在顶栏切换身份查看其他人的待办。" />
    </div>
    <div v-else class="todo-workspace" v-loading="loading">
      <aside class="todo-list-pane">
        <div class="todo-list-head">
          <b>待办列表</b>
          <span>{{ total }} 条</span>
        </div>
        <div class="todo-list-search">
          <el-input v-model="keyword" clearable placeholder="节点 / 流程 / 业务ID" />
        </div>
        <div class="todo-list">
          <button
            v-for="row in filteredRows"
            :key="row.id"
            class="todo-list-item"
            :class="{ selected: currentTask?.id === row.id }"
            @click="selectTask(row)"
          >
            <span class="queue-node">{{ row.nodeName || '待办理' }}</span>
            <strong>{{ row.flowName || '-' }}</strong>
            <small>{{ row.businessId || '-' }}</small>
          </button>
          <el-empty v-if="!filteredRows.length" description="没有匹配的待办" :image-size="56" />
        </div>
        <el-pagination
          v-if="total > pageSize"
          class="todo-list-pager"
          small
          layout="total, prev, next"
          :total="total"
          :page-size="pageSize"
          v-model:current-page="pageNum"
          @current-change="load"
        />
      </aside>
      <div class="todo-chart-pane">
        <ProcessContextPanel v-if="currentTask" :instance-id="currentTask.instanceId" />
        <el-empty v-else description="请选择一条待办" />
        <el-button
          v-if="currentTask"
          class="todo-handle-fab"
          type="primary"
          @click="dialogVisible = true"
        >办理</el-button>
      </div>
    </div>

    <div class="todo-table-fallback">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="id" label="任务ID" width="160" />
        <el-table-column label="流程名称" min-width="140">
          <template #default="{ row }">{{ row.flowName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="nodeName" label="待办节点" min-width="120" />
        <el-table-column label="业务ID" width="130">
          <template #default="{ row }">{{ row.businessId || '-' }}</template>
        </el-table-column>
        <el-table-column label="办理人" min-width="120">
          <template #default="{ row }">{{ (row.assignees || []).join(', ') }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectTask(row); openDialog(row)">办理</el-button>
            <el-button link type="primary" @click="viewHistory(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total"
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="pageNum"
        @current-change="load"
      />
    </div>

    <TaskActionDialog v-model="dialogVisible" :task="currentTask" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { httpGet, type PageVo } from '../../api/http'
import TaskActionDialog from '../../components/tasks/TaskActionDialog.vue'
import ProcessContextPanel from '../../components/instances/ProcessContextPanel.vue'
import { useSessionStore } from '../../stores/session'
import type { WorkflowTask } from '../../types/workflow'

interface TaskRow {
  id: string
  instanceId: string
  flowName: string
  nodeName: string
  businessId: string
  assignees: string[]
  createTime: string
}

const rows = ref<TaskRow[]>([])
const router = useRouter()
const session = useSessionStore()
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const currentTask = ref<WorkflowTask | null>(null)

const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((row) =>
    [row.nodeName, row.flowName, row.businessId].some((v) => (v || '').toLowerCase().includes(q)),
  )
})

async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<TaskRow>>('/tasks/todo', { pageNum: pageNum.value, pageSize })
    rows.value = res.list
    total.value = res.total
    if (rows.value.length && !rows.value.some(row => row.id === currentTask.value?.id)) {
      selectTask(rows.value[0])
    }
    if (!rows.value.length) currentTask.value = null
    session.todoCount = total.value
  } finally {
    loading.value = false
  }
}

function openDialog(row: TaskRow) {
  currentTask.value = { ...row }
  dialogVisible.value = true
}

function selectTask(row: TaskRow) {
  currentTask.value = { ...row }
}

function viewHistory(row: TaskRow) {
  if (row.instanceId) {
    router.push(`/instances/${row.instanceId}`)
  }
}

function onSaved() {
  load()
}

onMounted(load)
</script>
