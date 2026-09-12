<template>
  <div
      class="mimic-terminal"
      :class="['is-' + variant, { 'is-runtime': isRuntime }]"
      :style="terminalStyle"
      @click="editNode">
    <span v-if="variant === 'start'" class="mimic-terminal-play" aria-hidden="true">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="8.2" stroke="currentColor" stroke-width="1.8"/>
        <path d="M10.4 9l5.2 3-5.2 3V9z" fill="currentColor"/>
      </svg>
    </span>
    <span v-else class="mimic-terminal-dot" aria-hidden="true"></span>
    <span class="mimic-terminal-text">{{ label }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  variant: {
    type: String,
    default: 'end'
  },
  text: {
    type: String,
    default: ''
  },
  chartStatusColor: {
    type: Array,
    default () {
      return []
    }
  },
  status: {
    type: Number,
    default: null
  },
  stroke: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['editNode']);

const isRuntime = computed(() => props.chartStatusColor && props.chartStatusColor.length > 0);
const label = computed(() => props.text || (props.variant === 'start' ? '开始' : '结束'));

const terminalStyle = computed(() => {
  const statusColor = props.stroke || 'rgb(107,114,128)';
  const style = { '--mimic-status': statusColor };
  if (isRuntime.value) {
    style.borderColor = statusColor;
    style.borderStyle = props.status === 1 ? 'dashed' : 'solid';
    style.borderWidth = props.status === 1 ? '2px' : '1px';
  }
  return style;
});

const editNode = () => {
  emit('editNode');
};
</script>

<style scoped>
.mimic-terminal {
  width: 100%;
  height: 40px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px 0 10px;
  border-radius: 999px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-light, #e4e7ed);
  box-shadow: 0 4px 12px rgba(29, 33, 41, 0.06);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  cursor: pointer;
}

.mimic-terminal-play {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  display: flex;
  color: var(--wf-primary, #409eff);
}

.mimic-terminal-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-sizing: border-box;
  border: 2px solid var(--wf-text-secondary, #86909c);
  position: relative;
  flex: 0 0 10px;
}

.mimic-terminal-dot::after {
  content: "";
  position: absolute;
  inset: 2px;
  border-radius: 50%;
  background: var(--wf-text-secondary, #86909c);
}

.mimic-terminal-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--wf-text-regular, #4e5969);
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mimic-terminal.is-runtime .mimic-terminal-play {
  color: var(--mimic-status, #409eff);
}

.mimic-terminal.is-runtime .mimic-terminal-dot {
  border-color: var(--mimic-status, #86909c);
}

.mimic-terminal.is-runtime .mimic-terminal-dot::after {
  background: var(--mimic-status, #86909c);
}

:global(html.dark) .mimic-terminal {
  background: var(--wf-bg-color, #1d1e1f);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
}

:global(html.dark) .mimic-terminal-text {
  color: var(--wf-text-regular, #a3a6ad);
}
</style>
