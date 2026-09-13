<template>
  <div class="flow-history-chart">
    <div v-if="loading" class="chart-state">正在加载流程图…</div>
    <div v-else-if="error" class="chart-state">
      <el-empty description="流程图加载失败"><el-button type="primary" size="small" @click="reload">重新加载</el-button></el-empty>
    </div>
    <iframe
      v-show="!loading && !error"
      :src="chartUrl"
      title="流程历史图"
      frameborder="0"
      scrolling="no"
      @load="onLoad"
      @error="onError"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  instanceId: string | number
}>()
const loading = ref(true)
const error = ref(false)
const reloadKey = ref(0)

const chartUrl = computed(() => {
  const params = new URLSearchParams({
    id: String(props.instanceId),
    type: 'FlowChart',
    t: `${Date.now()}-${reloadKey.value}`,
  })
  return `/warm-flow-ui/index.html?${params.toString()}`
})

function reload() {
  loading.value = true
  error.value = false
  reloadKey.value += 1
}

function onLoad() {
  loading.value = false
}

function onError() {
  loading.value = false
  error.value = true
}
</script>

<style scoped>
.flow-history-chart {
  height: 100%;
  min-height: 420px;
  overflow: hidden;
  background: var(--wf-bg);
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-lg);
}

.flow-history-chart iframe {
  display: block;
  width: 100%;
  height: 100%;
}

.chart-state { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--wf-text-muted); }
</style>
