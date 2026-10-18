<template>
  <div
      class="mimic-node"
      :class="{ 'is-runtime': isRuntime, 'is-selected': selected }"
      :style="nodeStyle"
      ref="baseNodeDiv"
      @click="editNode">
    <div class="mimic-icon" aria-hidden="true">
      <svg v-if="props.type === 'service'" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <path :d="GEAR_PATH"/>
      </svg>
      <svg v-else-if="isEvent" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <path :d="BOLT_PATH"/>
      </svg>
      <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="9" r="3.1" stroke="currentColor" stroke-width="1.7"/>
        <path d="M6.6 18.2c.9-2.5 2.8-3.8 5.4-3.8s4.5 1.3 5.4 3.8" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
      </svg>
    </div>
    <div class="mimic-meta">
      <div class="mimic-title" v-click-outside="handleLeave">
        <span v-show="showSpan" class="mimic-title-text" @click.stop="editNodeName">{{ nodeName }}</span>
        <input
            v-show="editingNodeName"
            ref="nodeNameInput"
            class="mimic-title-input"
            v-model="nodeName"
            @click.stop
            @keyup.enter="saveNodeName"
            @blur="saveNodeName"/>
      </div>
      <div v-if="isEvent" class="mimic-who" :title="eventSummary">
        <span class="mimic-handler">{{ eventSummary }}</span>
        <span class="mimic-badge">{{ eventLabel }}</span>
      </div>
      <div v-else-if="showHandler && props.type !== 'service'" class="mimic-who" :title="handler">
        <span class="mimic-av">{{ handlerChar }}</span>
        <span class="mimic-handler">{{ handler }}</span>
        <span v-if="collaborativeBadge" class="mimic-badge">{{ collaborativeBadge }}</span>
      </div>
      <div v-else-if="props.type === 'service'" class="mimic-who" :title="serviceTarget">
        <span class="mimic-handler">{{ serviceTarget || '未配置处理器' }}</span>
        <span class="mimic-badge">{{ serviceTypeLabel }}</span>
      </div>
    </div>
    <span class="mimic-go" aria-hidden="true">›</span>
    <span
        v-show="(props.type === 'between' || props.type === 'service') && !isRuntime"
        class="mimic-del"
        @click.stop="deleteNode">×</span>
  </div>
</template>

<script setup name="BaseInfo">
import {computed, nextTick, ref} from 'vue';
import {handlerFeedback} from "@/api/flow/definition.js";

// 齿轮图标路径（Material settings，viewBox 0 0 24 24），服务任务节点图标
const GEAR_PATH = 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z'

// 闪电图标路径（Material bolt，viewBox 0 0 24 24），事件节点图标
const BOLT_PATH = 'M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z'

const props = defineProps({
  text: {
    type: String,
    default () {
      return ''
    }
  },
  permissionFlag: {
    type: String,
    default () {
      return ''
    }
  },
  // 协作方式：'2' 票签 / '3' 会签 时卡片显示徽标（'1' 或签为默认不显示）；
  // 缺失时按 nodeRatio 推导，与 propertySetting 的规则一致
  collaborativeWay: {
    type: String,
    default () {
      return ''
    }
  },
  nodeRatio: {
    type: String,
    default () {
      return ''
    }
  },
  // 服务任务节点配置（JSON 字符串），仅 service 节点使用
  nodeConfig: {
    type: String,
    default () {
      return ''
    }
  },
  // 事件类型（后端 EventType 值）：非空表示该中间节点是独立事件节点
  eventType: {
    type: String,
    default () {
      return ''
    }
  },
  // 消息事件的名称
  eventName: {
    type: String,
    default () {
      return ''
    }
  },
  // 选中态：卡片显示光圈
  selected: {
    type: Boolean,
    default () {
      return false
    }
  },
  chartStatusColor: {
    type: Array,
    default () {
      return []
    }
  },
  status: {
    type: Number,
    default () {
      return null
    }
  },
  type: {
    type: String,
    default () {
      return ''
    }
  },
  fill: {
    type: String,
    default () {
      return ''
    }
  },
  stroke: {
    type: String,
    default () {
      return ''
    }
  },
});

const showSpan = ref(true);
const baseNodeDiv = ref(null);
const nodeName = ref('发起人');
const handler = ref('所有人');
const nodeNameInput = ref(null);
const editingNodeName = ref(false);
const emit = defineEmits(['updateNodeName', 'deleteNode', 'editNode']);

// 运行态优先以节点状态判断；状态字段缺失时，流程图的三原色配置仍可识别运行态。
// 运行态只展示已完成节点（status=2）的办理人，未完成节点不泄露办理人信息。
const isRuntime = computed(() => (props.status !== null && props.status !== undefined)
  || (Array.isArray(props.chartStatusColor) && props.chartStatusColor.length === 3));
const showHandler = computed(() => !isRuntime.value || Number(props.status) === 2);

// 协作方式徽标：仅票签 / 会签显示，避免默认或签造成视觉噪音；
// collaborativeWay 缺失时按 nodeRatio 推导（0→或签、100→会签、其余→票签），
// 与 propertySetting 打开抽屉时的推导规则保持一致
const collaborativeBadge = computed(() => {
  let way = props.collaborativeWay;
  if (!way) {
    const ratio = props.nodeRatio || '0';
    way = parseFloat(ratio) === 0 ? '1' : parseFloat(ratio) === 100 ? '3' : (ratio ? '2' : '1');
  }
  if (way === '2') return '票签';
  if (way === '3') return '会签';
  return '';
});

// 服务任务配置解析：类型 + 处理器 / 表达式，用于卡片摘要展示
const serviceTaskConfig = computed(() => {
  if (props.type !== 'service') return {};
  try {
    const parsed = JSON.parse(props.nodeConfig || '{}');
    return (parsed && parsed.serviceTask) || {};
  } catch (e) {
    return {};
  }
});

const serviceTypeLabel = computed(() => {
  const labels = { class: '类Bean', delegateExpression: '委托', expression: '表达式' };
  return labels[serviceTaskConfig.value.type] || '未配置';
});

const serviceTarget = computed(() => {
  const cfg = serviceTaskConfig.value;
  return cfg.type === 'class' ? (cfg.handler || '') : (cfg.expression || '');
});

// 事件节点标识：定时 / 消息 / 终止等事件类型，用闪电图标 + 事件徽标替代办理人信息
const EVENT_TYPE_LABELS = {
  TIMER_START: '定时启动', MESSAGE_START: '消息启动',
  TIMER_CATCH: '定时捕获', MESSAGE_CATCH: '消息捕获', MESSAGE_THROW: '消息抛出',
  TIMER_BOUNDARY: '定时边界', MESSAGE_BOUNDARY: '消息边界',
  ERROR_CATCH: '错误捕获', ERROR_BOUNDARY: '错误边界',
  TERMINATE_END: '终止结束', MESSAGE_END: '消息结束',
};
const TIMER_TYPE_LABELS = { AFTER: '延迟时长', FIXED: '固定间隔', POINT_IN_TIME: '指定时间点' };
const TIMER_EVENT_TYPES = ['TIMER_START', 'TIMER_CATCH', 'TIMER_BOUNDARY'];

const isEvent = computed(() => !!props.eventType);
const eventLabel = computed(() => EVENT_TYPE_LABELS[props.eventType] || '事件');
const eventSummary = computed(() => {
  if (!isEvent.value) return '';
  if (TIMER_EVENT_TYPES.includes(props.eventType)) {
    try {
      const timer = (JSON.parse(props.nodeConfig || '{}').event) || {};
      const typeLabel = TIMER_TYPE_LABELS[timer.timerType] || '未配置定时';
      return timer.timerValue ? `${typeLabel} ${friendlyTimerValue(timer.timerValue)}` : typeLabel;
    } catch (e) {
      return '未配置定时';
    }
  }
  return props.eventName || (['ERROR_CATCH', 'ERROR_BOUNDARY'].includes(props.eventType) ? '通配' : '未配置名称');
});

/** 定时值人性化展示：单单位时长 / 时间点数组转中文，其余原样 */
function friendlyTimerValue(value) {
  const raw = String(value || '').trim();
  const match = raw.match(/^(?:P(\d+)D|PT(\d+)H|PT(\d+)M)$/);
  if (match) {
    const unitText = { M: '分钟', H: '小时', D: '天' };
    const amount = match[1] || match[2] || match[3];
    const unit = match[1] != null ? 'D' : (match[2] != null ? 'H' : 'M');
    return `${amount} ${unitText[unit]}`;
  }
  if (raw.startsWith('[')) {
    try {
      return JSON.parse(raw).join('、');
    } catch (e) {
      return raw;
    }
  }
  return raw;
}

const handlerChar = computed(() => {
  if (!handler.value || handler.value === '所有人') {
    return '全';
  }
  return handler.value.charAt(0);
});

const nodeStyle = computed(() => {
  const statusColor = props.stroke || 'rgb(107,114,128)';
  const style = { '--mimic-status': statusColor };
  if (isRuntime.value) {
    style.border = (props.status === 1 ? '2px dashed ' : '1px solid ') + statusColor;
  }
  return style;
});

const deleteNode = () => {
  emit('deleteNode');
};

watch(
    () => props.text,
    (newVal) => {
      if (newVal) {
        nodeName.value = newVal;
      }
    },
    { immediate: true }
);

watch(
    () => [props.permissionFlag, props.status, props.chartStatusColor],
    ([newVal]) => {
      if (!showHandler.value) {
        handler.value = '';
        return;
      }
      if (newVal) {
        handlerFeedback({storageIds: newVal.split("@@")}).then(response => {
          if (response.code === 200 && response.data) {
            handler.value = response.data.map(item => item.handlerName).join('、');
          }
        });
      } else {
        handler.value = '所有人';
      }
    },
    { immediate: true }
);

const editNodeName = () => {
  if (isRuntime.value) {
    return
  }
  editingNodeName.value = true;
  showSpan.value = false;
  nextTick(() => {
    if (nodeNameInput.value) {
      nodeNameInput.value.focus();
      nodeNameInput.value.select();
    }
  });
};

const saveNodeName = () => {
  if (isRuntime.value) {
    return
  }
  editingNodeName.value = false;
  showSpan.value = true;
  emit('updateNodeName', nodeName.value);
};

const editNode = () => {
  emit('editNode');
};

function handleLeave() {
  editingNodeName.value = false;
  showSpan.value = true;
}

</script>

<style scoped>
.mimic-node {
  width: 100%;
  height: 76px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  position: relative;
  border-radius: 14px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid rgba(64, 158, 255, 0.18);
  box-shadow: 0 8px 24px rgba(29, 33, 41, 0.06), 0 2px 8px rgba(64, 158, 255, 0.08);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  color: var(--wf-text-primary, #303133);
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease;
}

.mimic-node:hover {
  border-color: rgba(64, 158, 255, 0.4);
  box-shadow: 0 10px 28px rgba(29, 33, 41, 0.08), 0 4px 12px rgba(64, 158, 255, 0.12);
}

/* 选中态：与经典模式 outline 主题呼应的柔和光圈 */
.mimic-node.is-selected {
  border-color: var(--wf-primary, #409eff);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15), 0 8px 24px rgba(29, 33, 41, 0.08);
}

.mimic-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex: 0 0 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(180deg, #5aa2ff, var(--wf-primary, #409eff));
  box-shadow: 0 6px 12px rgba(64, 158, 255, 0.28);
}

.mimic-node.is-runtime .mimic-icon {
  background: var(--mimic-status, #909399);
  box-shadow: none;
}

.mimic-meta {
  min-width: 0;
  flex: 1;
}

.mimic-title {
  min-width: 0;
}

.mimic-title-text {
  display: block;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mimic-title-input {
  width: 100%;
  height: 22px;
  margin: 0;
  padding: 0 4px;
  border: 1px solid var(--wf-primary, #409eff);
  border-radius: 6px;
  outline: none;
  font-size: 14px;
  font-weight: 600;
  color: var(--wf-text-primary, #303133);
  background: var(--wf-bg-white, #fff);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.mimic-who {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.mimic-av {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  border-radius: 50%;
  background: var(--wf-primary, #409eff);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.mimic-handler {
  min-width: 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--wf-text-regular, #4e5969);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 协作方式徽标：票签 / 会签 */
.mimic-badge {
  flex: 0 0 auto;
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--wf-primary-light, #ecf5ff);
  color: var(--wf-primary, #409eff);
  font-size: 10px;
  font-weight: 600;
  line-height: 14px;
}

.mimic-go {
  flex: 0 0 auto;
  color: var(--wf-primary, #409eff);
  opacity: 0.55;
  font-size: 20px;
  font-weight: 400;
  line-height: 1;
  transition: opacity .18s ease;
}

.mimic-node:hover .mimic-go {
  opacity: 1;
}

.mimic-del {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wf-text-secondary, #909399);
  font-size: 13px;
  line-height: 1;
  opacity: 0;
  transition: opacity .18s ease, color .18s ease, background-color .18s ease;
}

.mimic-node:hover .mimic-del { opacity: 1; }

.mimic-del:hover {
  color: var(--wf-danger, #f56c6c);
  background: rgba(245, 108, 108, 0.12);
}

:global(html.dark) .mimic-node {
  background: var(--wf-bg-color, #1d1e1f);
  border-color: rgba(64, 158, 255, 0.28);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
  color: var(--wf-text-primary, #e5eaf3);
}

:global(html.dark) .mimic-node:hover {
  border-color: rgba(64, 158, 255, 0.5);
}

:global(html.dark) .mimic-title-input {
  background: var(--wf-bg-color, #1d1e1f);
  color: var(--wf-text-primary, #e5eaf3);
}

:global(html.dark) .mimic-handler {
  color: var(--wf-text-regular, #a3a6ad);
}

:global(html.dark) .mimic-badge {
  background: rgba(64, 158, 255, 0.16);
}
</style>
