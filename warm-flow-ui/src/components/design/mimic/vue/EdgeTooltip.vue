<template>
    <div :style="tooltipStyle" @mouseenter="handleTooltipEnter" @mouseleave="handleTooltipLeave">
        <div class="tooltip-container">
            <div v-for="(item, index) in options" :key="index" @click="handleClick(item)" class="tooltip-item">
                <span class="tooltip-icon" :style="iconStyle(item)" v-html="item.svg"></span>
                <span>{{ item.label }}</span>
            </div>
        </div>
    </div>
</template>

<script setup name="EdgeTooltip">
import { computed } from 'vue';
import { useDark } from '@/composables/useDark';

const { isDark, themeColors } = useDark();

const props = defineProps({
  position: Object,
  tooltipEdge: Object,
});

// 节点类型语义色：与钉钉风格一致，插入菜单里一眼区分节点类型；
// 图标内联绘制（描边走 currentColor），sprite 图标写死 fill 无法跟随语义色
const GW_DIAMOND = '<rect x="6.3" y="6.3" width="11.4" height="11.4" rx="2" transform="rotate(45 12 12)"/>';
const options = [
  {
    icon: 'between', label: '审批', color: '#409eff',
    svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="5" width="16" height="14" rx="3"/><circle cx="12" cy="10.2" r="2.1"/><path d="M7.8 16.2c.8-1.9 2.3-2.8 4.2-2.8s3.4.9 4.2 2.8"/></svg>`,
  },
  {
    icon: 'serial', label: '互斥网关', color: '#ff9f43',
    svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<path d="M9.6 9.6l4.8 4.8M14.4 9.6l-4.8 4.8"/></svg>`,
  },
  {
    icon: 'parallel', label: '并行网关', color: '#8b5cf6',
    svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<path d="M12 9.4v5.2M9.4 12h5.2"/></svg>`,
  },
  {
    icon: 'inclusive', label: '包含网关', color: '#34c38f',
    svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<circle cx="12" cy="12" r="3.2"/><circle cx="12" cy="12" r="0.8" fill="currentColor" stroke="none"/></svg>`,
  },
]

const emit = defineEmits(['option-click', 'close-tooltip']);

/** 图标底色 = 语义色 12% 透明度，图标描边用语义色 */
function iconStyle(item) {
  return {
    color: item.color,
    backgroundColor: `color-mix(in srgb, ${item.color} 12%, transparent)`,
  };
}

/** 动态计算 tooltip 样式，根据暗黑模式切换颜色 */
const tooltipStyle = computed(() => ({
  top: `${props.position.y}px`,
  left: `${props.position.x + 20}px`,
  position: 'absolute',
  pointerEvents: 'auto',
  backgroundColor: themeColors.value.tooltipBg,
  border: `1px solid ${themeColors.value.tooltipBorder}`,
  borderRadius: '12px',
  boxShadow: themeColors.value.tooltipShadow,
  padding: '6px',
  fontSize: '13px',
  zIndex: 1000,
  color: themeColors.value.tooltipColor,
  display: 'flex',
  width: 'max-content'
}));

function handleTooltipEnter() {
  window.isTooltipHovered = true;
}

function handleTooltipLeave() {
  window.isTooltipHovered = false;
  emit('close-tooltip');
}

const handleClick = (item) => {
  item['tooltipEdge'] = props.tooltipEdge;
  emit('option-click', item);
};

</script>

<style scoped>
.tooltip-container {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 2px;
    width: 100%;
}

.tooltip-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 14px 7px 8px;
    cursor: pointer;
    border-radius: 8px;
    white-space: nowrap;
    transition: background-color 0.18s ease;

    &:hover {
      background-color: var(--wf-primary-lighter, #f0f7ff);
    }

    /* 暗黑模式 hover */
    html.dark &,
    :global(html.dark) & {
      &:hover {
        background-color: rgba(64, 158, 255, 0.12);
      }
    }
}

.tooltip-item span {
  font-size: 13px;
  white-space: nowrap;
}

.tooltip-icon {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  background: var(--wf-primary-light, #ecf5ff);
  border-radius: 50%;
  transition: all 0.18s ease;

  svg {
    width: 17px;
    height: 17px;
  }

  /* 暗黑模式适配 */
  html.dark &,
  :global(html.dark) & {
    background: rgba(64, 158, 255, 0.16);
  }
}

/* ========== 手机端响应式适配 ========== */
@media (max-width: 768px) {
  .tooltip-item {
    padding: 7px 12px 7px 7px;
  }

  .tooltip-icon {
    width: 28px;
    height: 28px;

    svg {
      width: 15px;
      height: 15px;
    }
  }
}

@media (max-width: 480px) {
  .tooltip-container {
    grid-template-columns: 1fr;
  }
}
</style>
