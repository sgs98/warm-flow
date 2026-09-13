<template>
  <section class="process-context-panel">
    <div class="context-tabs">
      <button :class="{ active: activeTab === 'chart' }" @click="activeTab = 'chart'">流程图</button>
      <button :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">办理历史 <span>{{ history.length }}</span></button>
    </div>
    <div class="context-body">
      <FlowHistoryChart v-if="activeTab === 'chart'" :instance-id="instanceId" />
      <HistoryTimeline v-else :items="history" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { httpGet } from '../../api/http'
import type { WorkflowHistoryItem } from '../../types/workflow'
import FlowHistoryChart from './FlowHistoryChart.vue'
import HistoryTimeline from './HistoryTimeline.vue'

const props = defineProps<{ instanceId: string }>()
const activeTab = ref<'chart' | 'history'>('chart')
const history = ref<WorkflowHistoryItem[]>([])

async function loadHistory() {
  history.value = await httpGet<WorkflowHistoryItem[]>(`/instances/${props.instanceId}/history`)
}

watch(() => props.instanceId, loadHistory)
onMounted(loadHistory)
</script>
