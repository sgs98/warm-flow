<template>
  <el-dialog
    class="task-action-dialog"
    :model-value="modelValue"
    :title="(submitOnly ? '提交申请 - ' : '任务办理 - ') + (task?.nodeName || '')"
    width="680px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <TaskActionForm
      v-if="modelValue"
      :task="task"
      :submit-only="submitOnly"
      @cancel="emit('update:modelValue', false)"
      @saved="onSaved"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import TaskActionForm, { type TodoTask } from './TaskActionForm.vue'

export type { TodoTask }

defineProps<{ modelValue: boolean; task: TodoTask | null; submitOnly?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()

function onSaved() {
  emit('update:modelValue', false)
  emit('saved')
}
</script>

<style scoped>
.task-action-dialog {
  max-width: calc(100vw - 32px);
}

.task-action-dialog :deep(.el-dialog__body) {
  padding: 12px 24px 8px;
}
</style>
