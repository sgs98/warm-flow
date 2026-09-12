<template>
  <div
      class="mimic-node"
      :class="{ 'is-runtime': isRuntime }"
      :style="nodeStyle"
      ref="baseNodeDiv"
      @click="editNode">
    <div class="mimic-icon" aria-hidden="true">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
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
      <div class="mimic-who" :title="handler">
        <span class="mimic-av">{{ handlerChar }}</span>
        <span class="mimic-handler">{{ handler }}</span>
      </div>
    </div>
    <span class="mimic-go" aria-hidden="true">›</span>
    <span
        v-show="props.type === 'between' && !isRuntime"
        class="mimic-del"
        @click.stop="deleteNode">×</span>
  </div>
</template>

<script setup name="BaseInfo">
import {computed, nextTick, ref} from 'vue';
import {handlerFeedback} from "@/api/flow/definition.js";

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

const isRuntime = computed(() => props.chartStatusColor && props.chartStatusColor.length > 0);

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
    () => props.permissionFlag,
    (newVal) => {
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
</style>
