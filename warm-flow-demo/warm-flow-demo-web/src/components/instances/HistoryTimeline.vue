<template>
  <el-timeline v-if="items.length">
    <el-timeline-item
      v-for="(h, idx) in items"
      :key="h.id"
      :timestamp="h.createTime || ''"
      placement="top"
      :type="idx === items.length - 1 ? 'primary' : ''"
    >
      <el-card shadow="never">
        <div class="tl-row">
          <b>{{ h.nodeName || h.nodeCode }}</b>
          <el-tag v-if="h.businessStatusName || h.flowStatusName" size="small">{{ h.businessStatusName || h.flowStatusName }}</el-tag>
        </div>
        <div v-if="h.targetNodeName" class="tl-line">→ {{ h.targetNodeName }}</div>
        <div class="tl-line">办理人：{{ h.approver || '-' }}</div>
        <div v-if="h.skipType" class="tl-line">动作：{{ h.skipType }}</div>
        <div v-if="h.message" class="tl-line">意见：{{ h.message }}</div>
      </el-card>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无历史记录" />
</template>

<script setup lang="ts">
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

defineProps<{ items: HistoryItem[] }>()
</script>

<style scoped>
.tl-row { display: flex; align-items: center; gap: 8px; }
.tl-line { color: var(--el-text-color-secondary); margin-top: 4px; font-size: 13px; }
</style>
