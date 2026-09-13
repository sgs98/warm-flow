<template>
  <section class="task-action-panel">
    <div class="action-panel-heading">
      <div>
        <span class="eyebrow">当前待办</span>
        <h3>{{ task?.nodeName || '请选择一条待办' }}</h3>
      </div>
      <el-tag v-if="task" type="warning" size="small">待办理</el-tag>
    </div>
    <div v-if="task" class="action-panel-meta">
      <span>{{ task.flowName || '未命名流程' }}</span>
      <span class="mono">{{ task.businessId || '-' }}</span>
    </div>
    <el-empty v-if="!task" description="从上方选择一条任务后即可办理" />
    <TaskActionForm
      v-else
      compact
      :task="task"
      :submit-only="submitOnly"
      @saved="emit('saved')"
    />
  </section>
</template>

<script setup lang="ts">
import type { WorkflowTask } from '../../types/workflow'
import TaskActionForm from './TaskActionForm.vue'

defineProps<{ task: WorkflowTask | null; submitOnly?: boolean }>()
const emit = defineEmits<{ (e: 'saved'): void }>()
</script>
