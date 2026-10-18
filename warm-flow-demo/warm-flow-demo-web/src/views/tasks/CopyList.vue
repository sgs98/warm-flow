<template>
  <div class="page-card">
    <StateError v-if="error" message="抄送记录加载失败" :loading="loading" @retry="load" />

    <template v-else>
    <el-table :data="rows" v-loading="loading" border>
      <template #empty>
        <el-empty description="暂无抄送记录，他人在办理时选择抄送你后会出现在这里" :image-size="72" />
      </template>
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
      layout="total, prev, pager, next"
      :total="total"
      v-model:current-page="pageNum"
      @current-change="load"
    />
    </template>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import StateError from '../../components/common/StateError.vue'
import { pageCopyTasks } from '../../api/tasks'
import { usePagedList } from '../../composables/usePagedList'
import { statusTagType } from '../../utils/status'
import type { TaskRow } from '../../types/workflow'

const router = useRouter()
const { rows, total, pageNum, loading, error, load } = usePagedList<TaskRow>(
  (pageNum, pageSize) => pageCopyTasks({ pageNum, pageSize }),
)

function viewInstance(row: TaskRow) {
  if (row.instanceId) router.push(`/instances/${row.instanceId}`)
}
</script>
