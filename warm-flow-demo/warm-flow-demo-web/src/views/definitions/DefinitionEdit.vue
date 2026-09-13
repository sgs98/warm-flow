<template>
  <div class="designer-wrap">
    <div class="designer-bar">
      <el-button text @click="back">← 返回列表</el-button>
      <span class="title">{{ isNew ? '新建流程' : '编辑流程 #' + id }}</span>
      <div style="flex: 1" />
      <el-button type="primary" plain size="small" @click="readonly = !readonly">
        {{ readonly ? '退出只读预览' : '只读预览' }}
      </el-button>
    </div>

    <div v-if="loading" class="state">流程加载中…</div>
    <div v-else-if="modelJson == null" class="state">
      流程数据不可用
      <div style="margin-top: 12px"><el-button type="primary" @click="back">返回列表</el-button></div>
    </div>
    <iframe
      v-else
      :key="designKey"
      ref="designerFrame"
      class="designer-frame"
      :src="designerUrl"
      title="Warm-Flow 流程设计器"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { httpGet } from '../../api/http'

const route = useRoute()
const router = useRouter()

const id = computed<string | null>(() => (route.params.id ? String(route.params.id) : null))
const isNew = computed(() => id.value == null)

const loading = ref(false)
const readonly = ref(false)
const modelJson = ref<Record<string, any> | null>(null)
const designKey = ref(0)
const designerFrame = ref<HTMLIFrameElement | null>(null)

const designerUrl = computed(() => {
  const params = new URLSearchParams()
  if (id.value != null) params.set('id', id.value)
  if (readonly.value) params.set('disabled', 'true')
  return `/warm-flow-ui/index.html?${params.toString()}`
})

async function init() {
  loading.value = true
  try {
    if (id.value != null) {
      modelJson.value = await httpGet<Record<string, any>>(`/definitions/${id.value}`)
    } else {
      modelJson.value = { flowCode: '', flowName: '', modelValue: 'CLASSICS', formCustom: 'N', nodeList: [] }
    }
    designKey.value += 1
  } catch (e) {
    // 接口错误已由 http 层统一提示；定义不存在（过期链接/已删除）时回到列表避免停留在坏页面
    modelJson.value = null
    router.replace('/definitions')
  } finally {
    loading.value = false
  }
}

function onFrameMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== designerFrame.value?.contentWindow) return
  if (event.data?.method === 'close' || event.data?.method === 'submitSuccess') {
    ElMessage.success(event.data.method === 'close' ? '保存成功' : '操作成功')
    back()
  }
}

function back() {
  router.push('/definitions')
}

onMounted(init)
onMounted(() => window.addEventListener('message', onFrameMessage))
onUnmounted(() => window.removeEventListener('message', onFrameMessage))
</script>

<style scoped>
.designer-wrap {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 108px);
  min-height: 540px;
  background: #fff;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-lg);
  overflow: hidden;
  box-shadow: var(--wf-shadow-card);
}
.designer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}
.designer-bar .title { font-weight: 600; color: var(--wf-text); }
.state { padding: 40px; text-align: center; color: var(--wf-text-muted); }
.designer-frame { flex: 1; width: 100%; min-height: 0; border: 0; background: #fff; }
</style>
