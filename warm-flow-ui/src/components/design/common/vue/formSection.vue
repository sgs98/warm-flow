<template>
  <div class="form-section" :class="{ 'is-collapsed': collapsed }">
    <div class="form-section-header" @click="collapsed = !collapsed">
      <span class="form-section-title">{{ title }}</span>
      <span v-if="collapsed && summary" class="form-section-summary">{{ summary }}</span>
      <span class="form-section-arrow">
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z" fill="currentColor"/>
        </svg>
      </span>
    </div>
    <div class="form-section-body" v-show="!collapsed">
      <slot></slot>
    </div>
  </div>
</template>

<script setup name="FormSection">
const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  // 收起状态下的摘要，让"收起来"不至于完全看不见内容
  summary: {
    type: String,
    default: ''
  },
  defaultOpen: {
    type: Boolean,
    default: true
  }
});

const collapsed = ref(!props.defaultOpen);

// 分组收起时内部字段的校验红字是不可见的，父组件校验失败时要能把它展开
defineExpose({
  open: () => { collapsed.value = false; },
  close: () => { collapsed.value = true; },
});
</script>

<style scoped lang="scss">
/* 扁平分区：标题 + 1px 底边线，无卡片边框 / 阴影 / 灰底（对齐 warm-flow-ui/AGENTS.md 第 3 节） */
.form-section {
  margin-bottom: 2px;
}

.form-section-header {
  display: flex; align-items: center; gap: 10px;
  padding: 0 0 8px 0;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--wf-border-lighter, #ebeef5);
  cursor: pointer;
  user-select: none;
  html.dark & { border-bottom-color: var(--wf-border-color, #333333); }

  &:hover .form-section-title { color: var(--wf-primary, #409eff); }
}

.form-section-title {
  flex-shrink: 0;
  font-size: 14px; font-weight: 600; letter-spacing: .3px;
  color: var(--wf-text-primary, #303133);
  transition: color 0.2s ease;
  html.dark & { color: var(--wf-text-primary, #e5e5e5); }
}

.form-section-summary {
  flex: 1; min-width: 0;
  font-size: 11.5px;
  color: var(--wf-text-regular, #606266);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  html.dark & { color: #a8abb2; }
}

.form-section-arrow {
  margin-left: auto; flex-shrink: 0;
  width: 18px; height: 18px;
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--wf-text-secondary, #909399);
  transform: rotate(-90deg);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  svg { width: 16px; height: 16px; }
  html.dark & { color: #a8abb2; }
}

.form-section:not(.is-collapsed) .form-section-arrow {
  transform: rotate(0deg);
}

.form-section.is-collapsed {
  margin-bottom: 0;

  .form-section-header { margin-bottom: 18px; }
}
</style>
