<template>
    <div :style="tooltipStyle" @mouseenter="handleTooltipEnter" @mouseleave="handleTooltipLeave">
        <div class="tooltip-container">
            <div v-for="section in optionSections" :key="section.label" class="tooltip-section">
                <div class="tooltip-section-title">{{ section.label }}</div>
                <div class="tooltip-grid">
                    <div v-for="item in section.items" :key="item.key" @click="handleClick(item)" class="tooltip-item">
                        <span class="tooltip-icon" :style="iconStyle(item)" v-html="item.svg"></span>
                        <span>{{ item.label }}</span>
                    </div>
                </div>
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
const GEAR = '<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>';

// 事件语义色（品红，与经典侧栏一致）与事件字形
const EVENT_COLOR = '#d946ef';
const eventSvg = (inner) =>
    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const EVENT_TIMER = eventSvg('<circle cx="12" cy="12" r="8"/><path d="M12 7.6v4.9l3.1 1.9"/>');
const EVENT_MESSAGE = eventSvg('<rect x="3.6" y="6" width="16.8" height="12" rx="2.2"/><path d="M4.6 7.6l7.4 5.4 7.4-5.4"/>');
const EVENT_ERROR = eventSvg('<circle cx="12" cy="12" r="8"/><path d="M9.3 9.3l5.4 5.4M14.7 9.3l-5.4 5.4"/>');
const EVENT_BOUNDARY = eventSvg('<circle cx="12" cy="12" r="8.4" stroke-dasharray="2.6 2"/><path d="M12.9 7.4l-3.1 4.8h2.5l-1 4.4 3.2-4.9h-2.5z" fill="currentColor" stroke="none"/>');

// 插入菜单按「节点 / 网关 / 事件」同层分组：一次点击即插入，不引入悬停嵌套（本浮层跟随光标且离开连线即关闭）
const optionSections = [
  {
    label: '节点',
    items: [
      {
        key: 'between', kind: 'between', label: '审批', color: '#409eff',
        svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="5" width="16" height="14" rx="3"/><circle cx="12" cy="10.2" r="2.1"/><path d="M7.8 16.2c.8-1.9 2.3-2.8 4.2-2.8s3.4.9 4.2 2.8"/></svg>`,
      },
      {
        key: 'service', kind: 'service', label: '服务任务', color: '#4f46e5',
        svg: `<svg viewBox="0 0 24 24" fill="currentColor">${GEAR}</svg>`,
      },
    ],
  },
  {
    label: '网关',
    items: [
      {
        key: 'serial', kind: 'gateway', type: 'serial', label: '互斥网关', color: '#ff9f43',
        svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<path d="M9.6 9.6l4.8 4.8M14.4 9.6l-4.8 4.8"/></svg>`,
      },
      {
        key: 'parallel', kind: 'gateway', type: 'parallel', label: '并行网关', color: '#8b5cf6',
        svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<path d="M12 9.4v5.2M9.4 12h5.2"/></svg>`,
      },
      {
        key: 'inclusive', kind: 'gateway', type: 'inclusive', label: '包容网关', color: '#34c38f',
        svg: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${GW_DIAMOND}<circle cx="12" cy="12" r="3.2"/><circle cx="12" cy="12" r="0.8" fill="currentColor" stroke="none"/></svg>`,
      },
    ],
  },
  {
    label: '事件',
    items: [
      {
        key: 'TIMER_CATCH', kind: 'event', eventType: 'TIMER_CATCH', label: '定时事件', color: EVENT_COLOR, svg: EVENT_TIMER,
        properties: { eventType: 'TIMER_CATCH', nodeConfig: '{"event":{"timerType":"AFTER","timerValue":"P1D"}}' },
      },
      {
        key: 'MESSAGE_CATCH', kind: 'event', eventType: 'MESSAGE_CATCH', label: '消息事件', color: EVENT_COLOR, svg: EVENT_MESSAGE,
        properties: { eventType: 'MESSAGE_CATCH' },
      },
      {
        key: 'ERROR_CATCH', kind: 'event', eventType: 'ERROR_CATCH', label: '错误事件', color: EVENT_COLOR, svg: EVENT_ERROR,
        properties: { eventType: 'ERROR_CATCH' },
      },
      {
        key: 'TIMER_BOUNDARY', kind: 'event', eventType: 'TIMER_BOUNDARY', label: '边界事件', color: EVENT_COLOR, svg: EVENT_BOUNDARY,
        properties: {
          eventType: 'TIMER_BOUNDARY', interrupting: true,
          nodeConfig: '{"event":{"timerType":"AFTER","timerValue":"P1D"}}',
        },
      },
    ],
  },
];

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
    display: flex;
    flex-direction: column;
    gap: 2px;
}

/* 分组之间用细分隔线区分「节点 / 网关 / 事件」 */
.tooltip-section + .tooltip-section {
    padding-top: 4px;
    border-top: 1px solid var(--wf-border-lighter, rgba(226, 232, 240, 0.7));
}

.tooltip-section-title {
    padding: 3px 8px 2px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.5px;
    color: var(--wf-text-secondary, #909399);
}

.tooltip-grid {
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
  .tooltip-grid {
    grid-template-columns: 1fr;
  }
}
</style>
