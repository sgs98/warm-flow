<template>
  <div class="page-card instance-detail" v-loading="loading">
    <div class="table-toolbar">
      <el-button text @click="router.push('/instances')">← 返回实例列表</el-button>
    </div>

    <template v-if="instance">
      <div class="instance-meta">
        <div>
          <span class="meta-label">实例ID</span>
          <span class="meta-value mono">{{ instance.id }}</span>
        </div>
        <div>
          <span class="meta-label">流程名称</span>
          <span class="meta-value">{{ instance.flowName || '-' }}</span>
        </div>
        <div>
          <span class="meta-label">业务ID</span>
          <span class="meta-value mono">{{ instance.businessId || '-' }}</span>
        </div>
        <div>
          <span class="meta-label">当前节点</span>
          <span class="meta-value">{{ instance.nodeName || '-' }}</span>
        </div>
        <div>
          <span class="meta-label">状态</span>
          <el-tag size="small" :type="statusTagType(instance.businessStatus, instance.businessStatusName || instance.flowStatusName)">
            {{ instance.businessStatusName || instance.flowStatusName || '-' }}
          </el-tag>
        </div>
        <div>
          <span class="meta-label">发起人</span>
          <span class="meta-value">{{ instance.createBy || '-' }}</span>
        </div>
      </div>
      <ProcessContextPanel :instance-id="instance.id" />
    </template>
    <el-empty v-else-if="!loading" description="实例不存在" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { httpGet } from '../../api/http'
import ProcessContextPanel from '../../components/instances/ProcessContextPanel.vue'
import { statusTagType } from '../../utils/status'

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

const route = useRoute()
const router = useRouter()
const instance = ref<InstanceRow | null>(null)
const loading = ref(false)

onMounted(async () => {
  const id = route.params.id as string
  loading.value = true
  try {
    instance.value = await httpGet<InstanceRow>(`/instances/${id}`)
  } finally {
    loading.value = false
  }
})
</script>
