<template>
  <div class="flow-history-chart">
    <iframe
      :src="chartUrl"
      title="流程历史图"
      frameborder="0"
      scrolling="no"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  instanceId: string | number
}>()

const chartUrl = computed(() => {
  const params = new URLSearchParams({
    id: String(props.instanceId),
    type: 'FlowChart',
    t: String(Date.now()),
  })
  return `/warm-flow-ui/index.html?${params.toString()}`
})
</script>

<style scoped>
.flow-history-chart {
  height: 68vh;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-lg);
}

.flow-history-chart iframe {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
