<template>
  <div class="page-card">
    <el-table :data="rows" v-loading="loading" border stripe>
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
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">办理</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
      :total="total" v-model:current-page="pageNum" @current-change="load" />

    <TaskActionDialog v-model="dialogVisible" :task="currentTask" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { httpGet, type PageVo } from '../../api/http'
import TaskActionDialog, { type TodoTask } from '../../components/tasks/TaskActionDialog.vue'

interface TaskRow {
  id: string
  flowName: string
  nodeName: string
  businessId: string
  assignees: string[]
  createTime: string
}

const rows = ref<TaskRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const dialogVisible = ref(false)
const currentTask = ref<TodoTask | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<TaskRow>>('/tasks/todo', { pageNum: pageNum.value, pageSize: 10 })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openDialog(row: TaskRow) {
  currentTask.value = { id: row.id, nodeName: row.nodeName }
  dialogVisible.value = true
}

function onSaved() {
  load()
}

onMounted(load)
</script>
