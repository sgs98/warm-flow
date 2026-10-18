<template>
  <div class="diagram-sidebar">
    <div class="sidebar-content">
      <!-- 基础流程节点组（开始 / 中间 / 服务 / 结束） -->
      <div class="node-group">
        <el-tooltip
          v-for="item in flowNodes"
          :key="item.type"
          :content="item.label"
          placement="right"
          :show-after="250"
        >
          <div
            class="tool-item"
            @mousedown="handleDragInNode(item)"
          >
            <div class="item-icon-wrap" v-html="item.icon"></div>
          </div>
        </el-tooltip>
      </div>

      <!-- 类型分组（网关 / 事件）：单个入口，悬停 / 点击展开子菜单 -->
      <template v-for="group in groups" :key="group.key">
        <div class="sidebar-divider"></div>

        <div
          :ref="(el) => setEntryRef(group.key, el)"
          class="group-entry"
          @mouseenter="openMenu(group.key)"
          @mouseleave="scheduleCloseMenu"
        >
          <el-tooltip
            :content="group.label"
            placement="right"
            :show-after="250"
            :disabled="openMenuKey === group.key"
          >
            <div
              class="tool-item"
              :class="{ 'is-open': openMenuKey === group.key }"
              @click="openMenu(group.key)"
            >
              <div class="item-icon-wrap" v-html="group.icon"></div>
              <span class="tool-caret"></span>
            </div>
          </el-tooltip>
        </div>
      </template>

      <!-- 子菜单 teleport 到 body：避免被设计器 el-header 的 overflow 裁剪 -->
      <Teleport to="body">
        <transition name="group-pop">
          <div
            v-show="!!openMenuKey"
            ref="popupRef"
            class="group-popup"
            :style="popupStyle"
            @mouseenter="keepMenuOpen"
            @mouseleave="scheduleCloseMenu"
          >
            <div
              v-for="opt in activeOptions"
              :key="opt.value || opt.type"
              class="group-option"
              :title="`拖拽到画布添加${opt.label}`"
              @mousedown="handleDragInNode(opt)"
            >
              <span class="opt-icon" :class="`opt-${opt.tint}`" v-html="opt.icon"></span>
              <span class="opt-label">{{ opt.label }}</span>
            </div>
          </div>
        </transition>
      </Teleport>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  startIcon, betweenIcon, endIcon, serviceIcon,
  gatewayIcon, serialIcon, parallelIcon, inclusiveIcon,
  eventIcon, timerEventIcon, messageEventIcon, errorEventIcon, boundaryEventIcon,
} from '@/components/design/classics/js/sidebarIcons.js'

const emit = defineEmits(['dragInNode'])

// 基础节点（开始/中间/服务/结束）
const flowNodes = [
  { type: 'start', text: '开始', label: '开始', icon: startIcon },
  { type: 'between', text: '中间节点', label: '中间节点', icon: betweenIcon, properties: { collaborativeWay: '1' } },
  { type: 'service', text: '服务任务', label: '服务任务', icon: serviceIcon, properties: {} },
  { type: 'end', text: '结束', label: '结束', icon: endIcon },
]

// 网关 / 事件分组：单入口 + 子菜单；选项沿用现有拖拽契约（type / properties / text）
const groups = [
  {
    key: 'gateway',
    label: '网关',
    icon: gatewayIcon,
    options: [
      { type: 'serial', text: '', label: '互斥网关', icon: serialIcon, tint: 'serial', properties: {} },
      { type: 'parallel', text: '', label: '并行网关', icon: parallelIcon, tint: 'parallel', properties: {} },
      { type: 'inclusive', text: '', label: '包容网关', icon: inclusiveIcon, tint: 'inclusive', properties: {} },
    ],
  },
  {
    key: 'event',
    label: '事件',
    icon: eventIcon,
    // 事件统一由中间节点（between）承载，拖入即带 eventType，具体类型与触发配置在属性面板调整
    options: [
      {
        type: 'between', value: 'TIMER_CATCH', text: '定时事件', label: '定时事件', icon: timerEventIcon, tint: 'event',
        properties: { eventType: 'TIMER_CATCH', nodeConfig: '{"event":{"timerType":"AFTER","timerValue":"P1D"}}' },
      },
      {
        type: 'between', value: 'MESSAGE_CATCH', text: '消息事件', label: '消息事件', icon: messageEventIcon, tint: 'event',
        properties: { eventType: 'MESSAGE_CATCH' },
      },
      {
        type: 'between', value: 'ERROR_CATCH', text: '错误事件', label: '错误事件', icon: errorEventIcon, tint: 'event',
        properties: { eventType: 'ERROR_CATCH' },
      },
      {
        type: 'between', value: 'TIMER_BOUNDARY', text: '边界事件', label: '边界事件', icon: boundaryEventIcon, tint: 'event',
        properties: {
          eventType: 'TIMER_BOUNDARY', interrupting: true,
          nodeConfig: '{"event":{"timerType":"AFTER","timerValue":"P1D"}}',
        },
      },
    ],
  },
]

// ===== 分组子菜单开合 =====
const openMenuKey = ref(null)
const popupRef = ref(null)
const popupStyle = ref({})
// 各分组入口元素（key -> HTMLElement），用于把子菜单定位到入口右侧
const entryEls = {}
// 离开入口到子菜单之间留一个短延迟，避免鼠标穿过间隙时菜单闪退
let closeTimer = null

function setEntryRef(key, el) {
  if (el) {
    entryEls[key] = el
  } else {
    delete entryEls[key]
  }
}

function openMenu(key) {
  clearTimeout(closeTimer)
  openMenuKey.value = key
  nextTick(positionPopup)
}

function keepMenuOpen() {
  clearTimeout(closeTimer)
}

function scheduleCloseMenu() {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    openMenuKey.value = null
  }, 120)
}

/** 子菜单 teleport 到 body，需按当前入口位置做 fixed 定位 */
function positionPopup() {
  const entry = openMenuKey.value ? entryEls[openMenuKey.value] : null
  if (!entry) {
    return
  }
  const rect = entry.getBoundingClientRect()
  popupStyle.value = {
    position: 'fixed',
    left: `${rect.right + 10}px`,
    top: `${Math.max(8, rect.top - 6)}px`,
  }
}

const activeOptions = computed(() => {
  const group = groups.find((item) => item.key === openMenuKey.value)
  return group ? group.options : []
})

/** 点击入口与子菜单之外的区域时关闭（覆盖触摸端无法 hover 收起的场景） */
function onDocumentPointerDown(event) {
  if (!openMenuKey.value) {
    return
  }
  const target = event.target
  for (const el of Object.values(entryEls)) {
    if (el && el.contains(target)) {
      return
    }
  }
  if (popupRef.value && popupRef.value.contains(target)) {
    return
  }
  openMenuKey.value = null
}

function handleDragInNode(item) {
  openMenuKey.value = null
  emit('dragInNode', item.type, item.properties, item.text || {})
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown, true)
  window.addEventListener('resize', positionPopup)
  window.addEventListener('scroll', positionPopup, true)
})
onBeforeUnmount(() => {
  clearTimeout(closeTimer)
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  window.removeEventListener('resize', positionPopup)
  window.removeEventListener('scroll', positionPopup, true)
})
</script>

<style scoped>
.diagram-sidebar {
  position: absolute;
  left: 12px;
  top: 16px;
  width: 56px;
  border-radius: 14px;
  background: var(--wf-sidebar-bg, rgba(255, 255, 255, 0.95));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--wf-border-light, rgba(226, 232, 240, 0.8));
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.06),
    0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  z-index: 10;
  user-select: none;
  /* 子菜单 teleport 到 body，不依赖父级裁剪；此处保留纵向滚动以适配矮屏 */
  overflow-y: auto;
  overflow-x: hidden;
  max-height: calc(100vh - 100px);
  transition: box-shadow 0.25s ease;
}

/* 侧栏内容较长时使用细滚动条，避免底部节点被裁切 */
.diagram-sidebar::-webkit-scrollbar {
  width: 4px;
}

.diagram-sidebar::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.35);
  border-radius: 2px;
}

.diagram-sidebar::-webkit-scrollbar-track {
  background: transparent;
}

.diagram-sidebar:hover {
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.08),
    0 2px 8px rgba(0, 0, 0, 0.04);
}

/* ====== 内容区 ====== */
.sidebar-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}

.node-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* ====== 分隔线（整宽细线，清晰区分分组） ====== */
.sidebar-divider {
  width: calc(100% - 16px);
  height: 1px;
  margin: 10px 0;
  background: var(--wf-border-light, #e2e8f0);
}

/* ====== 工具项（图标按钮） ====== */
.tool-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  cursor: grab;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.tool-item:hover,
.tool-item.is-open {
  transform: translateY(-1px);

  .item-icon-wrap {
    background: var(--wf-primary-light, #ecf5ff);
    transform: scale(1.05);
  }
}

.tool-item:active {
  cursor: grabbing;
  transform: translateY(0) scale(0.97);

  .item-icon-wrap {
    background: var(--wf-primary-light, #ecf5ff);
  }
}

/* ====== 图标容器（工具条风：中性底，语义色只落在图标描边） ====== */
.item-icon-wrap {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #f8fafc;
  transition: background 0.2s ease, transform 0.2s ease;

  /* v-html 渲染的 SVG 自适应容器 */
  :deep(svg) {
    width: 100%;
    height: 100%;
    display: block;
  }
}

/* 分组入口右侧居中的「›」：默认隐藏，hover / 展开时显示 */
.tool-caret {
  position: absolute;
  right: 1px;
  top: 50%;
  width: 5px;
  height: 5px;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  color: var(--wf-text-placeholder, #c0c4cc);
  transform: translateY(-50%) rotate(-45deg);
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease;
}

.tool-item:hover .tool-caret,
.tool-item.is-open .tool-caret {
  opacity: 1;
  color: var(--wf-primary, #409eff);
}

/* ====== 分组子菜单 ====== */
.group-entry {
  position: relative;
  display: flex;
  justify-content: center;
}

.group-popup {
  /* position / left / top 由 positionPopup() 以内联 fixed 样式注入（teleport 到 body） */
  z-index: 2200;
  min-width: 148px;
  padding: 6px;
  border-radius: 12px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-light, rgba(226, 232, 240, 0.9));
  box-shadow:
    0 8px 28px rgba(15, 23, 42, 0.12),
    0 2px 8px rgba(15, 23, 42, 0.06);
}

.group-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: grab;
  color: var(--wf-text-primary, #303133);
  font-size: 13px;
  line-height: 1;
  transition: background 0.18s ease;
}

.group-option:hover {
  background: var(--wf-primary-light, #ecf5ff);
}

.group-option:active {
  cursor: grabbing;
  background: var(--wf-primary-lighter, #f0f7ff);
}

.group-option .opt-icon {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: #f8fafc;
  transition: background 0.18s ease;

  :deep(svg) {
    width: 100%;
    height: 100%;
    display: block;
  }
}

.group-option:hover .opt-icon {
  background: var(--wf-primary-light, #ecf5ff);
}

.group-option .opt-label {
  white-space: nowrap;
}

/* 子菜单进出场 */
.group-pop-enter-active,
.group-pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
  transform-origin: left center;
}

.group-pop-enter-from,
.group-pop-leave-to {
  opacity: 0;
  transform: translateX(-6px) scale(0.96);
}

/* ====== 暗黑模式（统一走 wf-* 变量） ====== */
html.dark .diagram-sidebar {
  background: var(--wf-sidebar-bg, rgba(30, 30, 35, 0.92));
  border-color: var(--wf-border-color, rgba(60, 60, 70, 0.6));
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.3),
    0 1px 4px rgba(0, 0, 0, 0.2);
}

html.dark .diagram-sidebar:hover {
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.4),
    0 2px 8px rgba(0, 0, 0, 0.2);
}

html.dark .sidebar-divider {
  background: linear-gradient(90deg, transparent, var(--wf-border-color, #333) 30%, var(--wf-border-color, #333) 70%, transparent);
}

html.dark .item-icon-wrap,
html.dark .group-option .opt-icon {
  background: var(--wf-bg-color, #222) !important;
}

/* 暗黑模式下 hover 的浅蓝底色（需压过上面的 !important 中性底） */
html.dark .tool-item:hover .item-icon-wrap,
html.dark .tool-item.is-open .item-icon-wrap,
html.dark .group-option:hover .opt-icon {
  background: rgba(64, 158, 255, 0.16) !important;
}

/* 暗黑模式下 SVG 图标内部背景色切换 */
html.dark .diagram-sidebar {
  --wf-icon-bg: #1e2028;
}

html.dark .group-popup {
  background: var(--wf-bg-color, #1d1e1f);
  border-color: var(--wf-border-color, #333);
  box-shadow:
    0 8px 28px rgba(0, 0, 0, 0.45),
    0 2px 8px rgba(0, 0, 0, 0.3);
}

html.dark .group-option {
  color: var(--wf-text-primary, #e0e0e0);
}

html.dark .group-option:hover {
  background: rgba(64, 158, 255, 0.12);
}

/* ====== 响应式：平板端 ====== */
@media (max-width: 1024px) {
  .diagram-sidebar {
    left: 8px;
    top: 14px;
    width: 52px;
    border-radius: 12px;
  }

  .tool-item {
    width: 38px;
    height: 38px;
  }

  .item-icon-wrap {
    width: 28px;
    height: 28px;
  }
}

/* ====== 响应式：手机端 ====== */
@media (max-width: 768px) {
  .diagram-sidebar {
    left: 6px;
    top: 12px;
    width: 46px;
    border-radius: 10px;
  }

  .sidebar-content {
    padding: 6px 0;
  }

  .tool-item {
    width: 34px;
    height: 34px;
    border-radius: 8px;
  }

  .item-icon-wrap {
    width: 26px;
    height: 26px;
  }

  .sidebar-divider {
    width: calc(100% - 14px);
    margin: 8px 0;
  }

  .group-popup {
    min-width: 132px;
    padding: 4px;
  }

  .group-option {
    padding: 7px 8px;
  }
}
</style>
