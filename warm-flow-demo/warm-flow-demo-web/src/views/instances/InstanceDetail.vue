<template>
  <div class="page-card">
    <div class="table-toolbar">
      <el-button text @click="router.push('/instances')">← 返回实例列表</el-button>
      <span style="font-weight: 600">实例详情</span>
    </div>

    <template v-if="instance">
      <el-descriptions :column="3" border style="margin-bottom: 16px">
        <el-descriptions-item label="实例ID">{{ instance.id }}</el-descriptions-item>
        <el-descriptions-item label="流程名称">{{ instance.flowName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ instance.businessId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ instance.nodeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ instance.businessStatusName || instance.flowStatusName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ instance.createBy || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin-bottom: 12px">办理历史</h4>
      <HistoryTimeline :items="history" />
    </template>
    <el-empty v-else-if="!loading" description="实例不存在" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { httpGet } from '../../api/http'
import HistoryTimeline from '../../components/instances/HistoryTimeline.vue'

interface InstanceRow {
  id: string
  flowName: string
  businessId: string
  nodeName: string
  flowStatusName: string
  businessStatus: string
  businessStatusName: string
  createBy: string
}
interface HistoryItem {
  id: string
  nodeName: string
  nodeCode: string
  targetNodeName: string
  approver: string
  skipType: string
  flowStatusName: string
  businessStatus: string
  businessStatusName: string
  message: string
  createTime: string
}

const route = useRoute()
const router = useRouter()
const instance = ref<InstanceRow | null>(null)
const history = ref<HistoryItem[]>([])
const loading = ref(false)

onMounted(async () => {
  const id = route.params.id as string
  loading.value = true
  try {
    instance.value = await httpGet<InstanceRow>(`/instances/${id}`)
    history.value = await httpGet<HistoryItem[]>(`/instances/${id}/history`)
  } finally {
    loading.value = false
  }
})
</script>
