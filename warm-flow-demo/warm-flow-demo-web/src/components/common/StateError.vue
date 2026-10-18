<template>
  <div class="state-error" role="alert">
    <span>{{ message }}</span>
    <el-button size="small" type="primary" plain :loading="loading" @click="emit('retry')">
      重新加载
    </el-button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ message?: string; loading?: boolean }>(), {
  message: '数据加载失败',
  loading: false,
})

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>
