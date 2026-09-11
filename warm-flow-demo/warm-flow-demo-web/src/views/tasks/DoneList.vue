<template>
  <div class="page-card">
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="历史任务ID" width="160" />
      <el-table-column label="流程名称" min-width="140">
        <template #default="{ row }">{{ row.flowName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="nodeName" label="节点" min-width="120" />
      <el-table-column label="业务ID" width="130">
        <template #default="{ row }">{{ row.businessId || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag type="success">{{ row.businessStatusName || row.flowStatusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="办理时间" width="170" />
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
      :total="total" v-model:current-page="pageNum" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { httpGet, type PageVo } from '../../api/http'

interface DoneRow {
  id: string
  flowName: string
  nodeName: string
  businessId: string
  flowStatusName: string
  businessStatus: string
  businessStatusName: string
  createTime: string
}

const rows = ref<DoneRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<DoneRow>>('/tasks/done', { pageNum: pageNum.value, pageSize: 10 })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
