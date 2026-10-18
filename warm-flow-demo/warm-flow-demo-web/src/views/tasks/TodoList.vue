<template>
  <div class="page-card todo-page">
    <StateError v-if="error" message="待办列表加载失败" :loading="loading" @retry="load" />

    <div v-else-if="!loading && !rows.length" class="todo-workspace-empty">
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
        <template #empty>
          <el-empty description="暂无待办任务" :image-size="60" />
        </template>
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
import { pageTodoTasks } from '../../api/tasks'
import TaskActionDialog from '../../components/tasks/TaskActionDialog.vue'
import ProcessContextPanel from '../../components/instances/ProcessContextPanel.vue'
import StateError from '../../components/common/StateError.vue'
import { useSessionStore } from '../../stores/session'
import { usePagedList } from '../../composables/usePagedList'
import type { TaskRow, WorkflowTask } from '../../types/workflow'

const router = useRouter()
const session = useSessionStore()
const keyword = ref('')
const dialogVisible = ref(false)
const currentTask = ref<WorkflowTask | null>(null)

const { rows, total, pageNum, pageSize, loading, error, load } = usePagedList<TaskRow>(
  (pageNum, pageSize) => pageTodoTasks({ pageNum, pageSize }),
  { pageSize: 20 },
)

const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((row) =>
    [row.nodeName, row.flowName, row.businessId].some((v) => (v || '').toLowerCase().includes(q)),
  )
})

function afterLoad() {
  if (rows.value.length && !rows.value.some(row => row.id === currentTask.value?.id)) {
    selectTask(rows.value[0])
  }
  if (!rows.value.length) currentTask.value = null
  session.todoCount = total.value
}

onMounted(async () => {
  await load()
  afterLoad()
})

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

async function onSaved() {
  await load()
  afterLoad()
}
</script>
