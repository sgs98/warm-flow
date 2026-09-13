<template>
  <el-timeline v-if="items.length" class="history-timeline">
    <el-timeline-item
      v-for="(h, idx) in items"
      :key="h.id"
      :timestamp="h.createTime || ''"
      placement="top"
      :type="h.current || idx === 0 ? 'primary' : ''"
    >
      <div class="tl-card">
        <div class="tl-row">
          <b>{{ h.nodeName || h.nodeCode }}</b>
          <el-tag
            v-if="h.current ? h.taskStatusName : (h.businessStatusName || h.flowStatusName)"
            size="small"
            :type="h.current ? 'warning' : statusTagType(h.businessStatus, h.businessStatusName || h.flowStatusName)"
          >
            {{ h.current ? h.taskStatusName : (h.businessStatusName || h.flowStatusName) }}
          </el-tag>
        </div>
        <div v-if="h.targetNodeName" class="tl-line">→ {{ h.targetNodeName }}</div>
        <div class="tl-line">办理人：{{ h.approver || '-' }}</div>
        <div v-if="h.current" class="tl-line">当前待办</div>
        <div v-if="h.message" class="tl-line">意见：{{ h.message }}</div>
      </div>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无历史记录" />
</template>

<script setup lang="ts">
import type { WorkflowHistoryItem } from '../../types/workflow'
import { statusTagType } from '../../utils/status'

defineProps<{ items: WorkflowHistoryItem[] }>()
</script>

<style scoped>
.history-timeline {
  padding: 16px 16px 16px 12px;
}
.tl-card {
  padding: 10px 2px 4px;
}
.tl-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tl-line {
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  font-size: 13px;
}
</style>
