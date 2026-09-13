<template>
  <div class="page-card">
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="历史任务ID" width="160" />
      <el-table-column label="流程名称" min-width="140">
        <template #default="{ row }">{{ row.flowName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="nodeName" label="抄送节点" min-width="120" />
      <el-table-column label="业务ID" width="130">
        <template #default="{ row }">{{ row.businessId || '-' }}</template>
      </el-table-column>
      <el-table-column label="流程状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(undefined, row.flowStatusName || row.businessStatusName)">
            {{ row.flowStatusName || row.businessStatusName || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="抄送时间" width="170" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewInstance(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      v-model:current-page="pageNum"
      @current-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { httpGet, type PageVo } from '../../api/http'
import { statusTagType } from '../../utils/status'

interface CopyRow {
  /** 历史任务主键。 */
  id: string
  /** 流程名称。 */
  flowName: string
  /** 抄送发生时所在节点名称。 */
  nodeName: string
  /** 业务主键。 */
  businessId: string
  /** 流程状态名称。 */
  flowStatusName: string
  /** 业务状态名称。 */
  businessStatusName: string
  /** 抄送时间。 */
  createTime: string
  instanceId?: string
}

const rows = ref<CopyRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const router = useRouter()

function viewInstance(row: CopyRow) {
  if (row.instanceId) router.push(`/instances/${row.instanceId}`)
}

/** 加载当前用户收到的抄送分页数据。 */
async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<CopyRow>>('/tasks/copy', { pageNum: pageNum.value, pageSize: 10 })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
