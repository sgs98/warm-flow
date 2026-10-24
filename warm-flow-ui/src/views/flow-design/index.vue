<template>
  <div class="design-root" :class="{ 'is-panel-collapsed': panelCollapsed }" :style="headerDiv">
    <!-- 顶部导航栏 -->
    <div class="design-header" v-if="!onlyDesignShow">
      <!-- 左侧：流程名称（点击把右侧常驻面板切到流程配置） -->
      <div class="header-left">
        <div class="flow-name-wrapper">
          <el-tooltip content="点击编辑流程编码、名称、类别等配置" placement="bottom" :show-after="500">
            <button type="button" class="flow-name flow-name--editable" @click="openBaseInfo()" v-if="!onlyDesignShow">
                <svg-icon icon-class="flowName"/>
                <span class="flow-name-text">{{ logicJson.flowName || '未命名流程' }}</span>
                <svg class="flow-name-edit-icon" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                  <path d="M11.3 1.7a1.6 1.6 0 0 1 2.3 0l.7.7a1.6 1.6 0 0 1 0 2.3l-8.3 8.3-3.5.9a.5.5 0 0 1-.6-.6l.9-3.5 8.3-8.3z"
                        fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
                  <path d="M10.2 2.9l2.9 2.9" stroke="currentColor" stroke-width="1.4"/>
                </svg>
            </button>
            <div class="flow-name" v-else>
                <svg-icon icon-class="flowName"/>
                <span class="flow-name-text">{{ logicJson.flowName || '未命名流程' }}</span>
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- 中间：占位（原步骤切换已移除，流程配置收进右侧常驻面板） -->
      <div class="header-center"></div>

      <!-- 右侧：保存按钮（有未保存改动时显示小圆点） -->
      <div class="header-right">
        <el-tooltip :content="dirty ? '有未保存的改动' : '保存流程'" placement="bottom">
          <el-button class="save-btn" size="default" @click="saveJsonModel" v-if="!disabled">
            <svg-icon icon-class="save" class="save-icon"/>
            <span>保存</span>
            <i v-if="dirty" class="save-dot" aria-hidden="true"></i>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <el-header :style="headerStyle">
      <div class="design-toolbar">
        <div>
          <span class="toolbar-group">
            <el-tooltip content="缩小" placement="bottom"><el-button size="small" icon="ZoomOut" aria-label="缩小" @click="zoomViewport(false)"></el-button></el-tooltip>
            <!-- 自适应：所有端均 fitView 显示全部节点 -->
            <el-tooltip content="自适应" placement="bottom"><el-button size="small" icon="Rank" aria-label="自适应画布" @click="zoomViewport('fit')"></el-button></el-tooltip>
            <el-tooltip content="放大" placement="bottom"><el-button size="small" icon="ZoomIn" aria-label="放大" @click="zoomViewport(true)"></el-button></el-tooltip>
          </span>
          <span class="toolbar-group">
            <el-tooltip content="上一步（⌘ / Ctrl + Z）" placement="bottom"><el-button size="small" icon="DArrowLeft" aria-label="撤销上一步" :disabled="!canUndo" @click="undoOrRedo(true)"></el-button></el-tooltip>
            <el-tooltip content="下一步（⌘ / Ctrl + Shift + Z）" placement="bottom"><el-button size="small" icon="DArrowRight" aria-label="重做下一步" :disabled="!canRedo" @click="undoOrRedo(false)"></el-button></el-tooltip>
            <el-tooltip content="清空画布" placement="bottom"><el-button size="small" icon="Delete" aria-label="清空画布" @click="clear()"></el-button></el-tooltip>
          </span>
          <span class="toolbar-group">
            <el-tooltip content="下载流程图" placement="bottom"><el-button size="small" icon="Picture" aria-label="下载流程图" @click="downLoad"></el-button></el-tooltip>
            <el-tooltip content="下载JSON" placement="bottom"><el-button size="small" icon="Download" aria-label="下载 JSON" @click="downJson"></el-button></el-tooltip>
          </span>
          <!-- 画布辅助：框选多选 / 小地图导航 / 一键整理（经典模式） -->
          <span class="toolbar-group">
            <el-tooltip :content="selectionMode ? '退出框选' : '框选：拖拽拉框多选'" placement="bottom">
              <el-button size="small" icon="Crop" :class="{ 'is-active': selectionMode }" :disabled="disabled"
                         aria-label="框选" :aria-pressed="selectionMode" @click="toggleSelectionMode"></el-button>
            </el-tooltip>
            <el-tooltip :content="minimapVisible ? '关闭小地图' : '小地图：整体导航'" placement="bottom">
              <el-button size="small" icon="MapLocation" :class="{ 'is-active': minimapVisible }"
                         aria-label="小地图" :aria-pressed="minimapVisible" @click="toggleMiniMap"></el-button>
            </el-tooltip>
            <el-tooltip v-if="isClassicsModel && !disabled" content="一键整理：按分层重新排版" placement="bottom">
              <el-button size="small" icon="MagicStick" aria-label="一键整理" @click="autoLayoutCanvas"></el-button>
            </el-tooltip>
          </span>
          <!-- 设计器模型切换已统一收进「流程配置」面板的单选（已有流程切换前会确认），工具栏不再保留入口 -->
          <!-- 流程配置：点画布空白同样会切到它；嵌入模式的定义元数据由宿主管理，不提供入口 -->
          <span class="toolbar-group" v-if="!onlyDesignShow && !disabled">
            <el-tooltip content="流程配置" placement="bottom">
              <el-button size="small" icon="Setting" aria-label="流程配置" @click="openBaseInfo()"></el-button>
            </el-tooltip>
          </span>
          <span class="toolbar-group" v-if="onlyDesignShow && !disabled">
            <el-tooltip content="保存" placement="bottom"><el-button size="small" class="toolbar-save-btn" aria-label="保存" @click="saveJsonModel">
              <svg-icon icon-class="save" style="width: 14px; height: 14px;"/>
            </el-button></el-tooltip>
          </span>
          <!-- 操作帮助：右键菜单与快捷键在界面上没有其它入口，这里统一兜底说明 -->
          <span class="toolbar-group">
            <el-popover placement="bottom-start" :width="320" trigger="click" popper-class="design-help-popover">
              <template #reference>
                <el-button size="small" icon="QuestionFilled" aria-label="操作帮助"></el-button>
              </template>
              <div class="design-help">
                <div class="design-help-title">快捷键</div>
                <ul class="design-help-list">
                  <li><span>保存流程</span><kbd>{{ modKey }} + S</kbd></li>
                  <li><span>撤销 / 重做</span><kbd>{{ modKey }} + Z / ⇧ + {{ modKey }} + Z</kbd></li>
                  <li><span>删除选中元素</span><kbd>Delete / Backspace</kbd></li>
                </ul>
                <div class="design-help-title">鼠标操作</div>
                <ul class="design-help-list">
                  <li><span>节点 / 边属性</span><kbd>单击节点或连线</kbd></li>
                  <li><span>重命名、复制、删除、设为退回线…</span><kbd>右键节点或连线</kbd></li>
                  <li><span>流程配置（编码 / 名称 / 模型）</span><kbd>单击画布空白</kbd></li>
                  <li><span>平移画布 / 缩放</span><kbd>空白拖拽 · 滚轮 · ⌘ / Ctrl + 滚轮</kbd></li>
                </ul>
                <div class="design-help-title">画布辅助</div>
                <ul class="design-help-list">
                  <li><span>框选多个节点</span><kbd>工具栏框选按钮 → 空白拖拽拉框</kbd></li>
                  <li><span>整体导航、拖动取景框</span><kbd>工具栏小地图按钮</kbd></li>
                  <li><span>一键整理（按分层自动排版）</span><kbd>工具栏整理按钮，⌘ / Ctrl + Z 可撤销</kbd></li>
                </ul>
              </div>
            </el-popover>
          </span>
        </div>
      </div>

      <!-- 自定义拖拽侧边栏：延迟显示避免与 LogicFlow DOM 冲突 -->
      <DiagramSidebar
        v-if="sidebarVisible"
        class="diagram-sidebar"
        @dragInNode="handleDragInNode"
      />

      <div class="container" ref="containerRef"></div>
      <!-- 画布空态引导：必须是 .container 的「兄弟节点」覆盖层！
           LogicFlow 初始化会接管 .container 的 DOM，若 v-if 元素放在其内部，
           节点计数变化引发的 patch 会在同一 flush 里撞上被销毁的锚点，
           导致 flush 崩溃、$refs 丢失、后续画布重建全部静默失效 -->
      <div class="canvas-empty-guide" v-if="canvasEmpty">
        <svg class="canvas-empty-guide-icon" viewBox="0 0 48 48" aria-hidden="true">
          <rect x="6" y="19" width="14" height="10" rx="2.5" fill="none" stroke="currentColor" stroke-width="2"/>
          <rect x="28" y="19" width="14" height="10" rx="2.5" fill="none" stroke="currentColor" stroke-width="2"/>
          <path d="M20 24h8M24 24v0" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M24 10v9M24 29v9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="2 3"/>
        </svg>
        <p class="canvas-empty-guide-title">画布是空的</p>
        <p class="canvas-empty-guide-desc">从左侧拖入节点开始设计 · 选中节点或连线可编辑属性</p>
      </div>
      <div class="logo-text">Warm-Flow</div>

      <!-- 侧栏开合把手：贴在侧栏左边缘的竖条，宽屏常驻时也保留。
           这是唯一开合入口（宽屏收起后画布吃满整屏；窄屏收起覆盖层） -->
      <el-tooltip :content="panelCollapsed ? '展开属性面板' : '收起属性面板'" placement="left">
        <button type="button" class="sidebar-handle" :class="{ 'is-collapsed': panelCollapsed }"
                :aria-expanded="!panelCollapsed" aria-controls="wf-design-sidebar"
                :aria-label="panelCollapsed ? '展开属性面板' : '收起属性面板'"
                @click="togglePanel">
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M9.8 3.6 5.6 8l4.2 4.4" fill="none" stroke="currentColor" stroke-width="1.6"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </el-tooltip>

      <!-- 常驻右侧栏：选中节点 / 连线时是属性面板，未选中时是流程配置。
           只是把原来的「点空白弹抽屉 / 点节点弹抽屉」换成常驻容器，内容切换规则不变。
           必须放在 .container 外面！LogicFlow 初始化会接管 .container 的内部 DOM，
           放在里面会被清掉锚点，之后 Vue 每次 patch Design 子树都会 insertBefore null 崩溃 -->
      <aside id="wf-design-sidebar" class="design-sidebar">
        <!-- 节点 / 边属性：未选中即卸载，下次选中重建（与原先抽屉的 destroy-on-close 等价） -->
        <PropertySetting v-if="nodeClick" ref="propertySettingRef" :node="nodeClick" :lf="lf" :disabled="disabled"
                         :skipConditionShow="skipConditionShow" :nodes="nodes" :skips="skips"
                         :form-path-list="formPathList" @edit-skip="handleEditSkip"></PropertySetting>

        <!-- 流程配置：未选中任何元素时的面板内容。必须常驻（v-show 而非 v-if）——
             用户填的内容只在 BaseInfo 内部，切到节点再切回来不能丢。
             嵌入模式的定义元数据由宿主管理，这里不渲染 -->
        <div class="panel-shell" v-if="!onlyDesignShow" v-show="!nodeClick">
          <div class="panel-header">
            <div class="panel-title">流程配置</div>
            <div class="panel-subtitle">编码、名称、模型等定义信息，随画布一起保存</div>
          </div>
          <div class="panel-body">
            <BaseInfo ref="baseInfoRef" :logic-json="logicJson" :category-list="categoryList"
                      :form-path-list="formPathList" :definition-id="definitionId" :disabled="disabled"
                      @update:flow-name="handleFlowNameUpdate" @update:model-value="handleModelValueUpdate"
                      @update:dirty="handleBaseInfoDirty"/>
          </div>
        </div>

        <!-- 嵌入模式：定义元数据由宿主管理，没有流程配置可填，未选中时给一句占位说明 -->
        <div class="panel-shell" v-if="onlyDesignShow && !nodeClick">
          <div class="panel-header">
            <div class="panel-title">节点属性</div>
          </div>
          <div class="panel-body">
            <p class="panel-empty-tip">选中画布上的节点或连线，在这里编辑属性</p>
          </div>
        </div>
      </aside>
    </el-header>

    <!-- 弹框组件 -->
    <EdgeTooltip
        v-if="tooltipVisible"
        :position="tooltipPosition"
        :tooltipEdge="tooltipEdge"
        @option-click="handleOptionClick"
        @close-tooltip="tooltipVisible = false"
    />

    <!-- 保存前检查未通过：列出问题，可点击定位到画布上的问题节点 -->
    <el-dialog
      v-model="checkDialogVisible"
      title="保存前检查未通过"
      width="440px"
      :append-to-body="true"
      class="flow-check-dialog"
    >
      <p class="flow-check-desc">以下问题会导致流程发布失败或运行中断，请先修改：</p>
      <ul class="flow-check-list">
        <li
          v-for="(item, index) in checkErrorList"
          :key="index"
          :class="{ 'is-locatable': item.id }"
          @click="locateCheckError(item)"
        >
          <span class="flow-check-name">{{ item.name }}</span>
          <span class="flow-check-message">{{ item.message }}</span>
          <span class="flow-check-action" v-if="item.id">定位</span>
        </li>
      </ul>
      <template #footer>
        <el-button @click="checkDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Design">
import LogicFlow from "@logicflow/core";
import "@logicflow/core/lib/style/index.css";
import {InsertNodeInPolyline, Menu, MiniMap, SelectionSelect, Snapshot} from '@logicflow/extension';
import '@logicflow/extension/lib/style/index.css'
import { ElLoading } from 'element-plus'
import PropertySetting from '@/components/design/common/vue/propertySetting.vue'
import { queryDef, saveJson } from "@/api/flow/definition";
import {
    getPreviousNodes,
    isClassics, isGateWay,
    json2LogicFlowJson,
    logicFlowJsonToWarmFlow
} from "@/components/design/common/js/tool";
import { checkFlowNodes } from "@/components/design/common/js/flowCheck";
import { getMagnetPosition, SNAP_EPSILON } from "@/components/design/common/js/snapMagnet";
import {
  relayoutToMimic,
  clearEdgePoints,
  normalizeClassicNodes,
  resetEdgeTextPositions,
  resetHistory
} from "@/components/design/common/js/relayout";
import { applyLayeredLayout, computeLayeredLayout, computeEdgeRoutes } from "@/components/design/common/js/autoLayout";
import { updateEdges } from "@/components/design/mimic/js/mimic";
import { ElMessageBox } from 'element-plus';
import StartC from "@/components/design/classics/js/start";
import BetweenC from "@/components/design/classics/js/between";
import ServiceC from "@/components/design/classics/js/service";
import SerialC from "@/components/design/classics/js/serial";
import ParallelC from "@/components/design/classics/js/parallel";
import InclusiveC from "@/components/design/classics/js/inclusive";
import EndC from "@/components/design/classics/js/end";
import SkipC from "@/components/design/classics/js/skip";
import StartM from "@/components/design/mimic/js/start";
import BetweenM from "@/components/design/mimic/js/between";
import ServiceM from "@/components/design/mimic/js/service";
import SerialM from "@/components/design/mimic/js/serial";
import ParallelM from "@/components/design/mimic/js/parallel";
import InclusiveM from "@/components/design/mimic/js/inclusive";
import EndM from "@/components/design/mimic/js/end";
import SkipM from "@/components/design/mimic/js/skip";
import useAppStore from "@/store/app";
import {computed, onMounted, onUnmounted, ref, watch} from "vue";
import BaseInfo from "@/components/design/common/vue/baseInfo.vue";
import initClassicsData from "@/components/design/classics/initClassicsData.json";
import initMimicData from "@/components/design/mimic/initMimicData.json";
import {addBetweenNode, addServiceNode, addGatewayNode, addEventNode, gatewayAddNode, removeNode} from "@/components/design/mimic/js/mimic.js";
import EdgeTooltip from "@/components/design/mimic/vue/EdgeTooltip.vue";
import DiagramSidebar from "@/components/design/common/vue/DiagramSidebar.vue";
import { useDark } from '@/composables/useDark';

const appStore = useAppStore();
const appParams = computed(() => useAppStore().appParams);

const { proxy } = getCurrentInstance();

const lf = ref(null);
const definitionId = ref();
const nodeClick = ref(null);
const disabled = ref(false);
const propertySettingRef = ref({});
const logicJson = ref({});
const jsonString = ref('');
const skipConditionShow = ref(true);
const nodes = ref([]);
const skips = ref([]);
const categoryList = ref([]);
const formPathList = ref([]);
// 控制侧边栏显示：延迟到 initLogicFlow 完成后显示，避免与 LogicFlow DOM 初始化冲突
const sidebarVisible = ref(false);
// 使用统一的暗黑模式 composable
const { isDark, themeColors, initFromUrl, applyDarkTheme, setupMessageListener, cleanupMessageListener } = useDark();

/**
 * 判断当前是否为移动端/平板设备
 * 通过屏幕宽度 + 触摸能力综合判断（PC 端不生效自动 fitView）
 */
const isMobileDevice = () => {
  // 屏幕宽度 <= 1024px（覆盖平板横屏及以下）或支持触摸且有窄屏
  return window.innerWidth <= 1024 || ('ontouchstart' in window && window.innerWidth <= 1280);
};

/** 仅在移动端/平板执行 fitView（用于新增节点等增量操作，避免 PC 编辑时被打断） */
function fitViewIfMobile() {
  if (isMobileDevice() && lf.value?.fitView) {
    lf.value.fitView(40, 20);
  }
}

/** 自适应显示全部节点（所有端）：打开 / 进入流程设计 / 点击「自适应」按钮时使用 */
function fitViewAll() {
  const lfInst = lf.value;
  if (!lfInst?.fitView) return;
  lfInst.fitView(40, 20);
  // fitView 会把节点较少的流程放大到撑满画布，这里限制最大缩放 100%：
  // 流程大 -> 缩小铺满；流程小 -> 保持原始大小并居中，避免进来就糊脸
  const scale = lfInst.getTransform ? lfInst.getTransform().SCALE_X : 1;
  if (scale && scale > 1) {
    lfInst.resetZoom ? lfInst.resetZoom() : lfInst.zoom(1);
    if (lfInst.translateCenter) lfInst.translateCenter();
  }
}

const onlyDesignShow = ref(false);

/* ========== 画布辅助：框选 / 小地图 / 一键整理 ========== */

/** 框选模式：开启后画布空白处拖拽 = 拉框多选（期间画布平移让位给拉框） */
const selectionMode = ref(false);
/** 小地图开关：引擎插件懒创建，打开才渲染第二个 LogicFlow 实例 */
const minimapVisible = ref(false);

/**
 * 切换框选模式。
 *
 * 引擎的框选插件在开启期间会把 stopMoveGraph 置为 true、关闭时自动还原，所以它是一个
 * 显式模式而不是常驻交互：常驻的话画布空白拖拽就没法平移了（平移仍可用滚轮 / 触控板）。
 */
function toggleSelectionMode() {
  const selection = lf.value?.extension?.selectionSelect;
  if (!selection) return;
  if (selectionMode.value) {
    selection.closeSelectionSelect();
    selectionMode.value = false;
    return;
  }
  // 灵敏度：元素完全落在框内才算选中（引擎默认值，与 bpmn.js 的拉框一致）
  selection.setSelectionSense(true, true);
  selection.openSelectionSelect();
  selectionMode.value = true;
}

/** 切换小地图：位置与尺寸由 pluginsOptions.miniMap 决定，贴画布右下角 */
function toggleMiniMap() {
  const miniMap = lf.value?.extension?.miniMap;
  if (!miniMap) return;
  if (minimapVisible.value) {
    miniMap.hide();
    minimapVisible.value = false;
    return;
  }
  miniMap.show();
  minimapVisible.value = true;
}

/**
 * 按经典模式的分层布局重排画布（左 → 右），不弹提示、不做权限判断。
 *
 * 只改节点坐标，不动节点属性与连线定义。整批移动落在历史记录 100ms 的合并窗口内，
 * 因此这是一步可撤销的操作（⌘ / Ctrl + Z 退回整理前）。
 *
 * @returns {boolean} 是否真的移动了节点
 */
function applyAutoLayout() {
  if (!lf.value) return false;
  const { nodes: nodeModels, edges: edgeModels } = lf.value.graphModel;
  // 只取布局需要的字段，避免在 LogicFlow 的 mobx 模型上挂临时属性
  const nodes = nodeModels.map(node => ({
    id: node.id,
    type: node.type,
    x: node.x,
    y: node.y,
    width: node.width,
    height: node.height
  }));
  const edges = edgeModels.map(edge => ({
    id: edge.id,
    sourceNodeId: edge.sourceNodeId,
    targetNodeId: edge.targetNodeId
  }));
  const positions = computeLayeredLayout(nodes, edges);
  if (!applyLayeredLayout(lf.value, positions)) return false;

  // 为每条前进边指定清爽的正交折线，并把条件标签挪到起点右侧（避免默认路由绕远、标签飘到中段）
  const sizeById = new Map(nodes.map(node => [node.id, {
    width: Number.isFinite(node.width) && node.width > 0 ? node.width : 100,
    height: Number.isFinite(node.height) && node.height > 0 ? node.height : 80,
  }]));
  const routeMap = computeEdgeRoutes(positions, sizeById, edges);
  const graphData = lf.value.getGraphData();
  graphData.edges.forEach(edge => {
    const route = routeMap.get(edge.id);
    if (route) {
      edge.pointsList = route.points;
      if (edge.text) {
        edge.text.x = route.label.x;
        edge.text.y = route.label.y;
      }
    } else {
      // 非前进边：清掉旧折点，交给 LogicFlow 按新坐标重新路由
      delete edge.pointsList;
    }
  });
  lf.value.render(graphData);

  // 整屏重排后自动适配视图，让整理结果完整可见
  fitViewAll();
  return true;
}

/**
 * 一键整理：把自由摆放的经典模式流程重排为左 → 右分层的树状布局。
 *
 * 经典模式的节点方向由模型决定，与画布上现有的坐标无关——「仿钉钉 → 经典」切换后
 * 节点还是竖排坐标，若跟着旧坐标走就会整理成竖排，与经典模式不符。
 */
function autoLayoutCanvas() {
  if (!lf.value || disabled.value || !isClassics(logicJson.value.modelValue)) return;
  if (!applyAutoLayout()) proxy.$modal.msgWarning('画布上没有可整理的节点');
}

const headerStyle = computed(() => {
  return {
    position: "relative",
    zIndex: "2",
    // flex 布局：el-header 占满顶部栏以下的剩余高度，画布在内部 flex:1 自动铺满，
    // 不再用「视口减魔法数」算高度——iframe 嵌入 / 隐藏顶栏 / 移动端地址栏都自适应
    display: "flex",
    flexDirection: "column",
    flex: "1",
    minHeight: "0",
    overflowY: "auto",
    height: "auto",
    padding: "0",
    backgroundColor: "transparent",
    border: "none",
    margin: "0",
    color: "var(--wf-text-primary, #303133)",
  };
});
const headerDiv = computed(() => {
    return {
        backgroundColor: "var(--wf-bg-page, #eef1f6)",
        // 100dvh 精确等于 iframe / 视口高度；不支持 dvh 的老浏览器由 CSS 回退到 100vh
        display: "flex",
        flexDirection: "column",
        height: "100dvh",
        minHeight: "100vh",
        position: "relative",
    };
});

const tooltipVisible = ref(false);
const tooltipPosition = ref({ x: 0, y: 0 });
const tooltipEdge = ref({});

// 常驻侧栏：宽屏固定宽度常驻显示；窄屏（≤1024px）默认收起，展开时覆盖画布（见 .design-sidebar 样式）
const isNarrowViewport = () => window.innerWidth <= 1024;
// 手动开合过的状态记在本地：用户把画布让大了，下次进来不必再收一次
const PANEL_COLLAPSED_KEY = 'warm-flow:design-panel-collapsed';
function readPanelCollapsed() {
  try {
    const saved = localStorage.getItem(PANEL_COLLAPSED_KEY);
    if (saved === '1') return true;
    if (saved === '0') return false;
  } catch (e) {
    // 隐私模式 / 禁用存储：退回按视口判断
  }
  return isNarrowViewport();
}
const panelCollapsed = ref(readPanelCollapsed());

// 侧栏开合 / 宽度变化后画布容器尺寸变了：过渡结束再让 LogicFlow 重算一次，
// 否则 SVG 的鼠标坐标映射还停在旧尺寸上（只 resize，不 fitView，避免缩放跳变）
let _panelResizeTimer = null;
function resizeCanvasAfterPanel() {
  clearTimeout(_panelResizeTimer);
  _panelResizeTimer = setTimeout(() => lf.value?.resize?.(), 260);
}

/** 开合侧栏：把手是唯一的手动入口 */
function togglePanel() {
  panelCollapsed.value = !panelCollapsed.value;
}

// 任何开合（手动 / 选中节点自动展开 / 窄屏点空白自动收起）都同步两件事：
// 记住状态 + 过渡结束后让画布重算尺寸
watch(panelCollapsed, (collapsed) => {
  try {
    localStorage.setItem(PANEL_COLLAPSED_KEY, collapsed ? '1' : '0');
  } catch (e) {
    // 存不下就算了，不影响开合
  }
  resizeCanvasAfterPanel();
});

// 只在「窄 → 宽」跨断点时强制展开：窄屏收起只是覆盖层让位。
// 宽屏下用户手动收起后，普通窗口缩放不应该又把面板弹开
let _wasNarrow = isNarrowViewport();
const onViewportResize = () => {
  const narrow = isNarrowViewport();
  if (_wasNarrow && !narrow) panelCollapsed.value = false;
  _wasNarrow = narrow;
};
window.addEventListener('resize', onViewportResize);


// 画布空态引导：节点数为 0（如清空后）时中央显示引导文案
const canvasNodeCount = ref(0);
const canvasEmpty = computed(() => canvasNodeCount.value === 0);

function updateCanvasNodeCount() {
  if (lf.value?.getGraphData) {
    canvasNodeCount.value = (lf.value.getGraphData().nodes || []).length;
  }
}

// ========== 未保存标记 / 撤销重做可用性 ==========
// 画布上的一切编辑都会进 LogicFlow 历史栈，用 history:change 统一驱动，无需逐处埋点
const dirty = ref(false);
const canUndo = ref(false);
const canRedo = ref(false);

// 写入 / 清除「校验错误标记」本身也会进历史栈（它不是用户的编辑内容），
// 用一个短窗口屏蔽，避免保存成功后「未保存」小圆点又亮起来
let _suppressDirtyUntil = 0;
function markDirty() {
  if (Date.now() < _suppressDirtyUntil) return;
  dirty.value = true;
}
function suppressDirty(duration = 400) {
  _suppressDirtyUntil = Date.now() + duration;
}

// ========== 保存前检查 ==========
const checkErrorList = ref([]);
const checkDialogVisible = ref(false);

// 快捷键提示里的修饰键：Mac 显示 ⌘，其它平台显示 Ctrl
const modKey = computed(() => (/mac/i.test(navigator.userAgent || '') ? '⌘' : 'Ctrl'));

/** 焦点是否落在可输入元素上：快捷键不应劫持正常输入 */
function isEditableTarget(target) {
  return !!target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);
}

/** 清除画布上的校验错误标记 */
function clearCheckErrors() {
  if (!lf.value?.getGraphData) return;
  lf.value.getGraphData().nodes.forEach(node => {
    if (node.properties?.__checkError) {
      lf.value.setProperties(node.id, { __checkError: '' });
    }
  });
}

/**
 * 跑一次保存前检查：把问题节点在画布上标红，并弹出清单。
 * @returns {Array} 问题列表（空数组表示通过）
 */
function runFlowCheck() {
  if (!lf.value?.getGraphData) return [];
  suppressDirty();
  clearCheckErrors();
  const errors = checkFlowNodes(lf.value.getGraphData());
  errors.forEach(item => {
    if (item.id) {
      lf.value.setProperties(item.id, { __checkError: item.message });
    }
  });
  checkErrorList.value = errors;
  if (errors.length === 0) {
    checkDialogVisible.value = false;
    return errors;
  }
  checkDialogVisible.value = true;
  // 自动定位到第一个能在画布上找到的问题节点，省得用户自己在大图里找
  const first = errors.find(item => item.id);
  if (first) proxy.$nextTick(() => locateCheckError(first, false));
  return errors;
}

/**
 * 打开某条边的属性：网关面板的「分支条件」列表点编辑时调用。
 * 就地切换右侧面板内容（不卸载重开），避免来回切换打断操作。
 * @param {string} edgeId 边 id
 */
function handleEditSkip(edgeId) {
  const edgeModel = lf.value?.getEdgeModelById(edgeId);
  if (!edgeModel) return;
  skipConditionShow.value = true;
  showProperty(edgeModel);
}

/**
 * 定位到问题节点：选中 + 视口居中 + 右侧属性面板切到该节点
 * @param {Object} item 检查结果项
 * @param {boolean} closeDialog 是否关闭检查清单弹窗
 */
function locateCheckError(item, closeDialog = true) {
  if (!item?.id || !lf.value) return;
  if (closeDialog) checkDialogVisible.value = false;
  const model = lf.value.getNodeModelById(item.id);
  if (!model) return;
  lf.value.clearSelectElements();
  lf.value.selectNodeById(item.id);
  if (typeof lf.value.focusOn === 'function') {
    lf.value.focusOn({ x: model.x, y: model.y });
  }
  showProperty(model);
}

/**
 * 全局键盘快捷键：
 * - Cmd/Ctrl + S：保存流程（替代伸手找按钮）
 * - Cmd/Ctrl + Z（按住 Shift 为重做）：撤销 / 重做
 * - Delete / Backspace：删除选中元素（经典模式由 LogicFlow 内置键盘处理，这里补齐仿钉钉模式）
 *
 * 焦点落在输入控件里时一律不劫持：输入框内的删除 / 撤销是正常编辑行为。
 */
function onGlobalKeydown(e) {
  const isMod = e.metaKey || e.ctrlKey;
  if (isMod && (e.key === 's' || e.key === 'S')) {
    e.preventDefault();
    if (!disabled.value) saveJsonModel();
    return;
  }
  if (isEditableTarget(e.target)) return;
  if (isMod && (e.key === 'z' || e.key === 'Z')) {
    e.preventDefault();
    if (e.shiftKey ? canRedo.value : canUndo.value) undoOrRedo(!e.shiftKey);
    return;
  }
  if ((e.key === 'Delete' || e.key === 'Backspace') && !disabled.value && lf.value && !isClassics(logicJson.value.modelValue)) {
    const elements = lf.value.getSelectElements(true);
    if (elements && (elements.nodes.length || elements.edges.length)) {
      e.preventDefault();
      lf.value.clearSelectElements();
      elements.edges.forEach((edge) => lf.value.deleteEdge(edge.id));
      elements.nodes.forEach((node) => lf.value.deleteNode(node.id));
    }
  }
}

/**
 * 关闭 / 刷新页面拦截：画布上有未保存改动时让浏览器弹确认，
 * 避免「改完直接关标签页」把配置丢掉（嵌入模式由宿主页面负责，这里不额外处理）。
 */
function onBeforeUnload(e) {
  if (!dirty.value) return;
  e.preventDefault();
  e.returnValue = '';
}

/**
 * 切到「流程配置」面板（点画布空白 / 点顶部流程名称 / 工具栏入口 / 新建流程时自动打开）。
 * 常驻侧栏没有开关，只有内容切换：切走前先对节点属性面板做一次离开校验，
 * 不通过就留在原面板，避免未填完的必填项被随手丢弃。
 * @param {boolean} keepOpen 窄屏下是否保持侧栏展开（显式入口为 true，点画布空白为 false）
 * @returns {Promise<boolean>} 是否完成了切换
 */
async function openBaseInfo(keepOpen = true) {
  if (nodeClick.value != null) {
    const ok = await propertySettingRef.value?.confirmLeave?.();
    if (ok === false) return false;
    nodeClick.value = null;
  }
  // 窄屏下侧栏是覆盖层：显式入口保持展开，点画布空白则顺手收起把画布让出来
  panelCollapsed.value = !keepOpen && isNarrowViewport();
  return true;
}

/** 流程配置有改动时点亮「未保存」（原抽屉「确定」按钮承担的动作，现在改动直接随保存提交） */
function handleBaseInfoDirty(isDirty) {
  if (isDirty) markDirty();
}

/**
 * 把右侧属性面板切到某个节点 / 连线（节点单击、边单击、检查清单定位、网关分支条件「编辑」都走这里）。
 * 切走前先对当前面板做一次离开校验，不通过则放弃切换。
 * @param {Object} model 节点 / 边 model
 */
async function showProperty(model) {
  if (!model || nodeClick.value === model) return;
  if (nodeClick.value && !(await propertySettingRef.value?.confirmLeave?.())) return;
  nodeClick.value = model;
  // 选中元素就是要配属性：窄屏下自动展开侧栏
  panelCollapsed.value = false;
  const graphData = lf.value?.getGraphData?.();
  if (graphData) {
    nodes.value = graphData.nodes;
    skips.value = graphData.edges;
  }
}

const handleOptionClick = (item) => {
  if (item.kind === "event") {
    addEventNode(lf.value, item.tooltipEdge, item.properties, item.label);
  } else if (item.kind === "service") {
    addServiceNode(lf.value, item.tooltipEdge);
  } else if (item.kind === "gateway") {
    addGatewayNode(lf.value, item.tooltipEdge, item.type);
  } else {
    addBetweenNode(lf.value, item.tooltipEdge);
  }
  tooltipVisible.value = false;
  // 移动端/平板端：新增节点后自适应画布，确保所有节点可见
  proxy.$nextTick(() => { fitViewIfMobile(); });
};

onMounted(() => {
    // 隐藏滚动条
    document.body.style.overflow = 'hidden';
    // 构建版本标记：控制台核对「加载的是哪次构建」，产物链排查用
    console.info('[warm-flow-designer] build:', typeof __BUILD_TIME__ !== 'undefined' ? __BUILD_TIME__ : 'dev');
    // 全局键盘快捷键（保存 / 撤销重做 / 删除选中）
    window.addEventListener('keydown', onGlobalKeydown);
    // 有未保存改动时拦截关闭 / 刷新（避免改完直接关标签页丢配置）
    window.addEventListener('beforeunload', onBeforeUnload);
    // 标记当前处于设计器页面：供经典节点设计态语义色判断（业务侧实例进度图不受影响）
    window.__WF_FLOW_DESIGN_MODE__ = true;
    if (!appParams.value) appStore.fetchTokenName();
    if (appParams.value.id) {
      definitionId.value = appParams.value.id;
    }
    onlyDesignShow.value = appParams.value.onlyDesignShow === 'true' ||
        appParams.value.onlyDesignShow === true;

    // 从 URL 参数初始化暗黑模式（统一使用 composable）
    initFromUrl();

    // 注册 postMessage 主题切换监听
    setupMessageListener();

    queryDef(definitionId.value).then(res => {
    jsonString.value = res.data;
    if (res.data.isPublish && res.data.isPublish !== 0) {
      disabled.value = true
    }
    if (appParams.value.disabled === 'true') {
        disabled.value = true
    }
    if (res.data.categoryList && res.data.categoryList.length > 0) {
      categoryList.value = res.data.categoryList;
    }
    if (res.data.formPathList && res.data.formPathList.length > 0) {
        formPathList.value = res.data.formPathList;
    }
    if (jsonString.value) {
      logicJson.value = json2LogicFlowJson(jsonString.value);
      if (!logicJson.value.nodes || logicJson.value.nodes.length === 0) {
        let initData = isClassics(logicJson.value.modelValue) ? initClassicsData: initMimicData
        // 读取本地文件/initClassicsData.json文件，并将数据转换json对象
        logicJson.value = {
          ...logicJson.value,
          ...initData
        };
      }
    }
    // 画布是唯一视图：初始加载即初始化（原由「切到流程设计」步骤触发，Tab 移除后在此补上）。
    // 放到 nextTick：流程配置表单常驻，要先让它吸收这次载入的定义信息，
    // 否则 handleModelValueUpdate 里的 getBaseInfo 会把空表单覆盖回 logicJson。
    nextTick(() => {
      handleModelValueUpdate();
      // 常驻面板：定义信息载入后刷新脏检测基准，否则一进页面就显示「未保存」
      nextTick(() => proxy.$refs.baseInfoRef?.markClean?.());
      // 新建流程：没有定义 id，侧栏停在流程配置并聚焦第一个必填字段
      if (!definitionId.value && !onlyDesignShow.value) {
        openBaseInfo();
        nextTick(() => proxy.$refs.baseInfoRef?.focusFirstField?.());
      }
    });
  });
})


// 提取初始化逻辑到单独的函数
function initLogicFlow() {
  if (proxy.$refs.containerRef) {
    use();
    lf.value = new LogicFlow({
      container: proxy.$refs.containerRef,
      textEdit: false,      // 是否开启文本编辑。
      // 关闭网格量化：历史数据与导入的坐标不落在 20 的倍数上（如初始数据 end 节点 x=597），
      // 量化会让新增节点落点被强行对齐到格点。精确对齐改由下面的 node:drop 磁吸保证。
      snapToGrid: false,
      // LogicFlow 默认只给 1px 容差，几乎拖不出对齐线
      snaplineEpsilon: SNAP_EPSILON,
      hideAnchors: !isClassics(logicJson.value.modelValue) || disabled.value,   // 是否隐藏节点的锚点，静默模式下默认隐藏。
      adjustNodePosition: isClassics(logicJson.value.modelValue) && !disabled.value,   // 是否允许拖动节点。
      hoverOutline: isClassics(logicJson.value.modelValue) && !disabled.value,   // 鼠标 hover 的时候是否显示节点的外框。
      nodeSelectedOutline: isClassics(logicJson.value.modelValue) && !disabled.value,    // 节点被选中时是否显示节点的外框。
      edgeSelectedOutline: isClassics(logicJson.value.modelValue) && !disabled.value,    //	边被选中时是否显示边的外框。
      grid: {
        size: 24,
        // 点阵网格默认关闭（宿主可传 showGrid=true 开启）：画布保持纯色干净，
        // 需要空间参照的场景由宿主自行决定是否打开
        visible: 'true' === appParams.value.showGrid,
        type: 'dot',
        config: {
          color: themeColors.value.gridColor,
          thickness: 1,
        },
        background: {
          backgroundColor: themeColors.value.bgPage,
        },
      },
      // 小地图：贴画布右下角（相对画布容器定位，侧栏开合后自动跟随画布宽度），显示连线便于看整体走向
      pluginsOptions: {
        miniMap: {
          width: 200,
          height: 150,
          showEdge: true,
          isShowHeader: false,
          isShowCloseIcon: false,
          rightPosition: 16,
          bottomPosition: 16,
        },
      },
      keyboard: isClassics(logicJson.value.modelValue) && !disabled.value ? {
        enabled: true,
        shortcuts: [
          {
            // Mac 主键盘的删除键是 Backspace，只绑 delete 会导致 Mac 上按删除键没反应
            keys: ["delete", "backspace"],
            callback: () => {
              const elements = lf.value.getSelectElements(true);
              lf.value.clearSelectElements();
              elements.edges.forEach((edge) => lf.value.deleteEdge(edge.id));
              elements.nodes.forEach((node) => lf.value.deleteNode(node.id));
            },
          },
        ],
      } : {},
    });
    lf.value.setTheme({
      snapline: {
        stroke: themeColors.value.snaplineStroke,
        strokeWidth: 2,
      },
      nodeText: {
        color: themeColors.value.nodeTextColor,
        fill: themeColors.value.nodeTextFill,
        fontSize: 13,
        fontWeight: 500,
      },
      edgeText: {
        fontSize: 13,
        strokeWidth: 1,
        background: {
          fill: themeColors.value.edgeTextBg,
        },
      },
      // 选中/悬浮的包围框：默认是黑色虚线，改成柔和蓝实线（贴近基础信息页 chroma 与 Mac 风格）
      outline: {
        stroke: 'rgba(64, 158, 255, 0.7)',
        strokeWidth: 1,
        strokeDasharray: '',
        hover: {
          stroke: 'rgba(64, 158, 255, 0.35)',
        },
      },
    });

    register();
    initMenu()
    initEvent();
    // 画布触摸事件桥接：LogicFlow v2 不支持原生 touch 事件，
    // 将触摸事件转换为鼠标事件以支持手机/平板端拖动画布
    initTouchEventBridge();
    if (logicJson.value) {
      if (isClassics(logicJson.value.modelValue)) {
        // 经典节点尺寸由注册元素决定，清掉模式转换残留的仿钉钉尺寸（否则审批卡会保持 260x76），
        // 并把仿钉钉居中文本的网关标签回正到菱形下方
        normalizeClassicNodes(logicJson.value.nodes);
      }
      lf.value.render(logicJson.value);
      // 标签坐标可能来自上一个布局（模式转换 / 保存回写），压在节点上时回退到连线默认位置
      resetEdgeTextPositions(lf.value);
      // 打开即自适应显示全部节点（所有端）
      fitViewAll();
      // 渲染后刷新画布节点计数（空态引导依赖）
      updateCanvasNodeCount();
    }
    // 重新初始化画布（切换设计器模型 / 重载）后复位画布辅助状态：插件实例是新建的，
    // 框选模式必须复位（否则空白拖拽会一直让给拉框），小地图按开关恢复
    selectionMode.value = false;
    if (minimapVisible.value && lf.value?.extension?.miniMap) {
      lf.value.extension.miniMap.show();
    }
    // 初始化完成后，如果当前是暗黑模式，显式应用一次主题（解决 URL 参数初始化时序问题）
    if (isDark.value && lf.value) {
      applyDarkTheme(lf.value, true);
    }
    // 真机修复：延迟触发一次 resize 确保 SVG 画布正确渲染尺寸
    // 移动端 v-show 切换后容器可能还未完成布局计算
    scheduleMobileResize();

    // LogicFlow 完全初始化后，再显示自定义拖拽侧边栏（避免 DOM 操作冲突）
    if (isClassics(logicJson.value.modelValue)) {
      sidebarVisible.value = true;
    }

    // 初始化期间的内部整理（标签位置回正、按新模型重算折点、适配视图）也会进历史栈，
    // 但它们不是用户的编辑内容：屏蔽掉并复位，避免刚打开就显示「未保存」
    suppressDirty();
    dirty.value = false;
  }
}

/**
 * 延迟触发 LogicFlow resize + fitView（所有端）
 * 覆盖移动端真机 / v-show 切换 / iframe 嵌入等容器尺寸延迟场景
 */
function scheduleMobileResize() {
  const doFit = () => {
    if (lf.value && lf.value.resize) {
      lf.value.resize();
      // resize 后自适应显示全部节点（所有端，覆盖布局/iframe 时序）
      requestAnimationFrame(fitViewAll);
    }
  };
  // 多重延迟策略覆盖各种场景：移动端/v-show切换/iframe嵌入
  setTimeout(doFit, 50);
  setTimeout(doFit, 150);
  setTimeout(doFit, 300);
  setTimeout(doFit, 600);
  // 最后一次保底（覆盖极端慢速场景如低端手机首次加载）
  setTimeout(doFit, 1000);
}

watch(isDark, (v) => {
  if (!lf.value) {
    return;
  }
  applyDarkTheme(lf.value, v);
});

/** 组件卸载时清理 message 监听 */
onUnmounted(() => {
  // 画布辅助插件的资源要显式释放：框选在 document 上挂了 pointermove/pointerup，
  // 小地图持有第二个 LogicFlow 实例
  lf.value?.extension?.selectionSelect?.closeSelectionSelect();
  lf.value?.extension?.miniMap?.hide();
  cleanupMessageListener();
  clearTimeout(_panelResizeTimer);
  window.removeEventListener('resize', handleMobileResize);
  window.removeEventListener('resize', onViewportResize);
  window.removeEventListener('keydown', onGlobalKeydown);
  window.removeEventListener('beforeunload', onBeforeUnload);
  // 离开设计器页面，复位设计态标记
  window.__WF_FLOW_DESIGN_MODE__ = false;
});

// 监听窗口变化，真机旋转屏幕时重绘（带防抖 + 尺寸变化过滤）
let _resizeTimer = null;
let _lastCanvasSize = { w: 0, h: 0 };
function handleMobileResize() {
  clearTimeout(_resizeTimer);
  _resizeTimer = setTimeout(() => {
    const container = proxy.$refs.containerRef;
    if (!container || !lf.value?.resize) return;
    const w = container.clientWidth;
    const h = container.clientHeight;
    // 容器尺寸无显著变化（<5px 容差）则跳过，过滤掉 overlay 等导致的微调
    if (Math.abs(w - _lastCanvasSize.w) < 5 && Math.abs(h - _lastCanvasSize.h) < 5 && _lastCanvasSize.w > 0) return;
    _lastCanvasSize = { w, h };
    lf.value.resize();
  }, 300); // 提高到 300ms 确保布局稳定后再判断
}
window.addEventListener('resize', handleMobileResize);

/**
 * 自定义拖拽面板：监听子组件 dragInNode 事件，调用 lf.dnd.startDrag
 */
function handleDragInNode(type, properties = {}, text) {
  if (lf.value) {
    lf.value.dnd.startDrag({
      text,
      type,
      properties,
    })
  }
}

function getBaseInfo() {
  if (onlyDesignShow.value) {
    return
  }
  // 非嵌入模式下流程配置表单常驻，直接取当前值即可
  const baseInfoComp = proxy.$refs.baseInfoRef;
  if (!baseInfoComp) {
    return
  }
  const baseInfoData = baseInfoComp.getFormData();
  logicJson.value = {
    ...logicJson.value,
    ...baseInfoData
  };
}

function handleFlowNameUpdate(newName) {
  logicJson.value.flowName = newName; // 更新父组件中的流程名称
  markDirty();
}

function handleModelValueUpdate() {
  // 原设计器模型
  const modeOrg = logicJson.value.modelValue;
  // 获取基础信息
  getBaseInfo();
  const modeNew = logicJson.value.modelValue;

  // 首次初始化（画布还没建）
  if (!lf.value) {
    sidebarVisible.value = false;
    nextTick(() => {
      try {
        if (!jsonString.value.nodeList || jsonString.value.nodeList.length === 0) {
          // 读取本地文件/initData.json文件，并将数据转换json对象
          let initData = isClassics(logicJson.value.modelValue) ? initClassicsData : initMimicData;
          logicJson.value = {
            ...logicJson.value,
            ...initData
          };
        }
        initLogicFlow();
      } catch (e) {
        console.error('[wf-designer] 首次初始化异常:', e);
      }
    });
    return;
  }

  // 模型没变（普通联动），无需重建
  if (modeOrg === modeNew) return;

  // 模型切换重建（原工具栏 switchModel 链路，现由流程配置面板的单选触发）：
  // 先取画布最新节点/连线（否则本次新增会被丢弃、拖过的坐标回退），
  // 丢弃旧模型折点，切仿钉钉时按上下结构重排，再按新模式注册元素重建画布
  try {
    const graphData = lf.value.getGraphData();
    logicJson.value = {
      ...logicJson.value,
      nodes: graphData['nodes'],
      edges: graphData['edges']
    };
    clearEdgePoints(logicJson.value.edges);
    if (!isClassics(modeNew)) {
      relayoutToMimic(logicJson.value.nodes, logicJson.value.edges);
    }
  } catch (e) {
    console.error('[model-switch] 同步段失败:', e);
    return;
  }
  sidebarVisible.value = false;
  nextTick(() => {
    try {
      initLogicFlow();
      if (lf.value) {
        // 重建后按「目标模型的节点尺寸」重算全部折点：只换 modelValue 会让旧折点脱开节点
        updateEdges(lf.value);
        // 连线已按新模型重建，标签坐标按新几何全部重算
        resetEdgeTextPositions(lf.value, true);
        // 切回经典：节点还带着仿钉钉的竖排坐标，按经典左 → 右重排一次（等价于点一次「一键整理」），
        // 否则会出现「经典模式却是上下结构」，之后的一键整理也会顺着竖排走。
        // 必须放在 updateEdges / resetEdgeTextPositions 之后：它们会重建全部连线折点并把标签
        // 重置回连线中段，若先整理会被覆盖，导致「节点已横排、连线标签却还是乱的」。
        if (isClassics(logicJson.value.modelValue)) {
          applyAutoLayout();
        }
        // 转换是整体重建，把转换后的状态设为新的撤销基准
        resetHistory(lf.value);
        // 整体重建后刷新节点计数（空态引导依赖）
        updateCanvasNodeCount();
        // 模型切换是画布内容的整体变更，标记为未保存（重建不走历史栈，不会自动点亮）
        markDirty();
      }
    } catch (e) {
      console.error('[model-switch] 重建失败:', e);
    }
  });
}

/** 当前是否经典模式（供其它逻辑判断模型方向） */
const isClassicsModel = computed(() => isClassics(logicJson.value?.modelValue));

/** 画布内是否切换过设计器模型：嵌入模式下保存时据此单独回写 model_value */
const modelValueDirty = ref(false);

/**
 * 嵌入模式下的最小定义载荷：只回写设计器模型，不携带宿主管理的其它定义字段。
 * flowCode 必须保留，否则 checkFlowLegal 会以「流程flowCode为空」拒绝保存。
 */
function modelValuePayload(json) {
  return {
    id: json.id,
    flowCode: json.flowCode,
    modelValue: json.modelValue,
    nodeList: json.nodeList
  };
}

async function saveJsonModel() {
  const loadingInstance = ElLoading.service(({fullscreen: true, text: "保存中，请稍等"}))
  // 常驻侧栏：面板里改过的节点编码要先落到画布（原先靠关闭抽屉时提交）
  propertySettingRef.value?.commitIdChange?.()
  if (!onlyDesignShow.value) {
    // 常驻面板：BaseInfo 已挂载即当前值，校验失败直接切回流程配置就近改错
    const baseInfoComp = proxy.$refs.baseInfoRef;
    if (baseInfoComp) {
      let validate = await baseInfoComp.validate();
      if (!validate) {
        loadingInstance.close();
        nodeClick.value = null;
        panelCollapsed.value = false;
        proxy.$modal.msgWarning("请先完善流程配置中标红项");
        return;
      }
    }
  }
  // 画布结构检查：把必然导致发布失败 / 流程跑不下去的问题先挑出来（标红 + 清单 + 定位）
  if (runFlowCheck().length > 0) {
    loadingInstance.close();
    return;
  }
  getBaseInfo();

  if (lf.value) {
    let graphData = lf.value.getGraphData()
    logicJson.value['nodes'] = graphData['nodes']
    logicJson.value['edges'] = graphData['edges']
  }
  logicJson.value['id'] = definitionId.value

  let jsonString = logicFlowJsonToWarmFlow(logicJson.value);
  // 嵌入模式下定义元数据由宿主管理，只有画布内切换过模型时才以最小载荷回写 model_value
  const writeModelValue = onlyDesignShow.value && modelValueDirty.value && !!logicJson.value['id'];
  if (writeModelValue) {
    jsonString = modelValuePayload(JSON.parse(jsonString));
  }
  saveJson(jsonString, onlyDesignShow.value && !writeModelValue).then(response => {
    if (response.code === 200) {
      proxy.$modal.msgSuccess("保存成功");
      // 保存成功即视为已落库：清掉画布上的校验标记并复位「未保存」状态
      // （清标记本身会进历史栈，用短窗口屏蔽，避免小圆点又被点亮）
      suppressDirty();
      clearCheckErrors();
      checkDialogVisible.value = false;
      dirty.value = false;
      // 延迟500ms后关闭页面
        setTimeout(() => {
            close()
        }, 500)
    }
  }).finally(() => {
    nextTick(() => {
      loadingInstance.close();
    });
  });
}

/**
 * 移动端触摸事件桥接：确保手机/平板上 LogicFlow 节点/边的点击、拖拽正常工作
 *
 * 核心问题：
 * LogicFlow v2 内部使用 mousedown/mousemove/mouseup + click/dblclick 事件，
 * 移动端浏览器不会自动将 touch 事件转换为 mouse 事件（除非设置了兼容模式）。
 *
 * 解决方案：
 * 使用统一的 Pointer Events API（pointerdown/pointermove/pointerup），
 * 它能同时覆盖鼠标、触摸、触控笔三种输入方式，
 * 并且自动映射为浏览器原生的 mousedown/mousemove/mouseup/click 事件链。
 */
function initTouchEventBridge() {
  const container = proxy.$refs.containerRef;
  if (!container) return;

  // 对容器及其所有后代元素启用 pointer-events 全局拦截
  // 这确保所有触摸输入都能正确转换为 mouse/click 事件
  setupPointerEventCapture(container);

  // 同时绑定到 canvas-overlay（确保画布拖拽也正常）
  const canvasOverlay = container.querySelector('.lf-canvas-overlay');
  if (canvasOverlay) {
    setupPointerEventCapture(canvasOverlay, { forDrag: true });
  } else {
    // 如果 CanvasOverlay 还未渲染，延迟绑定
    const observer = new MutationObserver(() => {
      const overlay = container.querySelector('.lf-canvas-overlay');
      if (overlay) {
        setupPointerEventCapture(overlay, { forDrag: true });
        observer.disconnect();
      }
    });
    observer.observe(container, { childList: true, subtree: true });
  }
}

/**
 * 使用 Pointer Events API 建立移动端触摸→鼠标事件的完整桥接
 *
 * Pointer Events 是 W3C 标准，现代浏览器均支持：
 * - pointerdown → 自动触发 mousedown（含 touches[0] 坐标）
 * - pointermove → 自动触发 mousemove
 * - pointerup   → 自动触发 mouseup + click
 * - pointercancel → 自动触发 mouseup（中断场景）
 *
 * 我们在此基础上额外补充：
 * 1. 阻止浏览器的默认手势行为（滚动/缩放）
 * 2. 长按模拟 contextmenu 右键菜单
 * 3. 双击检测并分发 dblclick
 *
 * @param {Element} el 目标 DOM 元素
 * @param {Object} options 配置项
 */
function setupPointerEventCapture(el) {
  // --- 双击检测状态 ---
  let tapCount = 0;
  let lastTapTime = 0;
  let doubleTapTimer = null;
  const DOUBLE_TAP_GAP = 350;

  // --- 长按右键菜单状态 ---
  let longPressTimer = null;
  let isLongPressFired = false;

  // --- 拖动检测 ---
  let startX = 0, startY = 0;
  let isDragging = false;
  let lastMoveX = 0, lastMoveY = 0;

  /**
   * 统一获取坐标参数（用于构造 MouseEvent）
   */
  function getEventOpts(pointerEvent) {
    return {
      clientX: pointerEvent.clientX,
      clientY: pointerEvent.clientY,
      button: 0,
      bubbles: true,
      cancelable: true,
      view: window,
    };
  }

  // ========== pointerdown ==========
  el.addEventListener('pointerdown', (e) => {
    // 只处理主指针（手指/鼠标左键/触控笔）
    if (e.pointerType === 'mouse' && e.button !== 0) return;

    startX = e.clientX;
    startY = e.clientY;
    lastMoveX = e.clientX;
    lastMoveY = e.clientY;
    isDragging = false;
    isLongPressFired = false;

    // 仅对触摸事件阻止浏览器默认手势（滚动/缩放/选择）
    // 鼠标事件不干预，让 LogicFlow 正常处理拖拽/双击
    if (e.pointerType === 'touch') {
      e.preventDefault();
      // 主动分发一次 mousedown 确保 LogicFlow 收到
      el.dispatchEvent(new MouseEvent('mousedown', getEventOpts(e)));
    }

    // 启动长按计时器（500ms 无显著移动则视为右键菜单，仅触摸有效）
    longPressTimer = setTimeout(() => {
      if (isDragging) return; // 已在拖动，取消长按
      isLongPressFired = true;
      el.dispatchEvent(new MouseEvent('contextmenu', getEventOpts(e)));
    }, 500);
  }, { passive: false });

  // ========== pointermove ==========
  el.addEventListener('pointermove', (e) => {
    const dx = Math.abs(e.clientX - startX);
    const dy = Math.abs(e.clientY - startY);

    if (!isDragging && (dx > 5 || dy > 5)) {
      // 首次超过阈值：进入拖动模式
      isDragging = true;
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
    }

    if (isDragging && e.pointerType === 'touch') {
      // 触摸拖动中：持续分发 mousemove 给 LogicFlow
      e.preventDefault();
      el.dispatchEvent(new MouseEvent('mousemove', getEventOpts(e)));
      lastMoveX = e.clientX;
      lastMoveY = e.clientY;
    } else if (e.pointerType === 'touch') {
      // 触摸微移动：阻止默认滚动行为
      e.preventDefault();
    }
  }, { passive: false });

  // ========== pointerup / pointercancel ==========
  function handlePointerEnd(e) {
    // 清理长按计时器
    if (longPressTimer) {
      clearTimeout(longPressTimer);
      longPressTimer = null;
    }

    // 拖动结束：分发 mouseup 给 LogicFlow 完成拖拽链路
    if (isDragging && e.pointerType === 'touch') {
      el.dispatchEvent(new MouseEvent('mouseup', getEventOpts(e)));
      resetTapState();
      isDragging = false;
      return;
    }

    // 长按已触发（右键菜单），不再处理 click/dblclick
    if (isLongPressFired) {
      resetTapState();
      return;
    }

    // ---- 点击/双击检测（仅非拖动） ----
    const now = Date.now();
    tapCount++;

    if (tapCount === 1) {
      // 第一次点击：立即触发 click
      lastTapTime = now;
      el.dispatchEvent(new MouseEvent('click', getEventOpts(e)));

      // 设置双击等待窗口
      doubleTapTimer = setTimeout(() => {
        tapCount = 0;
        doubleTapTimer = null;
      }, DOUBLE_TAP_GAP);

    } else if (tapCount >= 2 && (now - lastTapTime < DOUBLE_TAP_GAP)) {
      // 第二次快速点击：触发 dblclick
      if (doubleTapTimer) {
        clearTimeout(doubleTapTimer);
        doubleTapTimer = null;
      }
      tapCount = 0;
      el.dispatchEvent(new MouseEvent('dblclick', getEventOpts(e)));
    } else {
      // 超出双击间隔，重新开始计数
      tapCount = 1;
      lastTapTime = now;
      el.dispatchEvent(new MouseEvent('click', getEventOpts(e)));

      doubleTapTimer = setTimeout(() => {
        tapCount = 0;
        doubleTapTimer = null;
      }, DOUBLE_TAP_GAP);
    }
  }

  el.addEventListener('pointerup', handlePointerEnd);
  el.addEventListener('pointercancel', (e) => {
    // 中断：如果正在拖动需要补发 mouseup
    if (isDragging && e.pointerType === 'touch') {
      el.dispatchEvent(new MouseEvent('mouseup', getEventOpts(e)));
      isDragging = false;
    }
    if (longPressTimer) {
      clearTimeout(longPressTimer);
      longPressTimer = null;
    }
    resetTapState();
  });

  function resetTapState() {
    if (doubleTapTimer) {
      clearTimeout(doubleTapTimer);
      doubleTapTimer = null;
    }
    tapCount = 0;
  }
}

/**
 * 初始化菜单
 */
function initMenu() {
  // 跳转类型（PASS 通过 / REJECT 退回）随画线方向自动推导（见 edge:add），用户极少手改，
  // 不值得为它单独开一个边属性面板，统一收进边的右键菜单；
  // 经典模式内置「删除 / 编辑文本」，用 addMenuConfig 追加；仿钉钉模式菜单为空，直接设置。
  const edgeTypeMenu = [
    { text: '设为退回线', callback: (edge) => setEdgeSkipType(edge, 'REJECT') },
    { text: '设为通过线', callback: (edge) => setEdgeSkipType(edge, 'PASS') },
  ];
  if (isClassics(logicJson.value.modelValue)) {
    lf.value.addMenuConfig({ edgeMenu: edgeTypeMenu });
  } else {
    // 为菜单追加选项（必须在 lf.render() 之前设置）
    lf.value.extension.menu.setMenuConfig({
      nodeMenu: [],
      edgeMenu: edgeTypeMenu,
    });
  }
}

/**
 * 设置边的跳转类型（PASS 通过 / REJECT 退回）
 *
 * <p>跳转类型决定引擎驳回时走哪条线，默认按画线方向自动推导，右键菜单只用于自动推导
 * 不符合预期时的手动纠正（如跨分支退回）。设计态连线不区分这两种类型，改完给一次提示，
 * 避免用户看不出是否生效。</p>
 *
 * @param {Object} edge     右键菜单回传的边数据
 * @param {string} skipType PASS / REJECT
 */
function setEdgeSkipType(edge, skipType) {
  const edgeModel = lf.value.getEdgeModelById(edge.id);
  if (!edgeModel || edgeModel.properties.skipType === skipType) {
    return;
  }
  lf.value.setProperties(edge.id, { skipType });
  proxy.$modal.msgSuccess(skipType === 'REJECT' ? '已设为退回线' : '已设为通过线');
}

/**
 * 注册自定义节点和边
 */
function register() {
  if (isClassics(logicJson.value.modelValue)) {
    lf.value.register(StartC);
    lf.value.register(BetweenC);
    lf.value.register(ServiceC);
    lf.value.register(SerialC);
    lf.value.register(ParallelC);
    lf.value.register(InclusiveC);
    lf.value.register(EndC);
    lf.value.register(SkipC);
  } else {
    lf.value.register(StartM);
    lf.value.register(BetweenM);
    lf.value.register(ServiceM);
    lf.value.register(SerialM);
    lf.value.register(ParallelM);
    lf.value.register(InclusiveM);
    lf.value.register(EndM);
    lf.value.register(SkipM);
  }
}

/**
 * 添加扩展
 */
function use() {
  // 只有经典模式才有拖拽面板（已使用自定义 DiagramSidebar 替代内置 DndPanel）
  if (isClassics(logicJson.value.modelValue)) {
    LogicFlow.use(InsertNodeInPolyline)
  }
  LogicFlow.use(Menu);
  LogicFlow.use(Snapshot);
  // 画布辅助扩展：只注册不激活——框选要显式 openSelectionSelect()，小地图要显式 show()
  LogicFlow.use(SelectionSelect);
  LogicFlow.use(MiniMap);
}
function initEvent() {
  const { eventCenter } = lf.value.graphModel

  // 历史栈变化：统一驱动「未保存」标记与撤销 / 重做按钮可用性。
  // 画布上的一切编辑（增删节点、连线、改属性）都会进历史栈，不必在各处逐一埋 dirty。
  eventCenter.on('history:change', (args) => {
    canUndo.value = !!args?.data?.undoAble
    canRedo.value = !!args?.data?.redoAble
    markDirty()
  })

  if (!isClassics(logicJson.value.modelValue)) {
    // 更新节点名称
    eventCenter.on('update:nodeName', (data) => {
      lf.value.updateText(data.id, data.nodeName)
      lf.value.setProperties(data.id, {
        nodeName: data.nodeName
      })
    })

    // 网关节点单击事件：仿钉钉模式点网关 = 打开「加节点」菜单，属性面板不跟着切（沿用原行为）
    eventCenter.on('node:click', (args) => {
      if (isGateWay(args.data.type)) {
        gatewayAddNode(lf.value, args.data);
      }
    })

    eventCenter.on('edit:node', (args) => {
      if (args.click) {
        showProperty(lf.value.getNodeModelById(args.id))
      }
    })

    // 单击边
    eventCenter.on('show:EdgeSetting', (args) => {
      const edgeModel = lf.value.getEdgeModelById(args.id)
      const nodeModel = lf.value.getNodeModelById(edgeModel.sourceNodeId);
      // 属性面板只留给网关出线：只有那里有「跳转名称 + 跳转条件」这类真配置量。
      // 非网关出线只剩「跳转类型」，且已按画线方向自动推导，改类型走边的右键菜单。
      if (!['serial', 'inclusive'].includes(nodeModel['type'])) {
        return;
      }
      skipConditionShow.value = true
      showProperty(edgeModel)
    });

    // 鼠标进入边
    eventCenter.on('show:EdgeTooltip', (args) => {
      tooltipVisible.value = true;
      tooltipPosition.value = { x: args.e.clientX, y: args.e.clientY };
      tooltipEdge.value = lf.value.getEdgeModelById(args.id)
    });
    // 鼠标离开边
    eventCenter.on('hide:EdgeTooltip', () => {
      tooltipVisible.value = false;
    });
    // 删除节点事件
    eventCenter.on('delete:node', (args) => {
      const nodeModel = lf.value.getNodeModelById(args.id)
      removeNode(lf.value, nodeModel)
    })
  } else {
    // 拖动结束后 LogicFlow 还会补发一次 node:click，会顺手把右侧属性面板切过去。
    // 用时间窗口而不是布尔标记：拖到画布外没补发 click 时，不会把下一次正常单击也吞掉。
    let lastDragEndAt = 0

    // 拖拽结束磁吸：LogicFlow 的对齐线只画线、不改坐标，松手时自己把节点精确吸到对齐线上。
    // 用 node:drop 而不是 node:mouseup —— 后者普通单击也会触发，会把节点无故挪动几像素。
    eventCenter.on('node:drop', (args) => {
      lastDragEndAt = Date.now()
      const magnet = getMagnetPosition(lf.value, args.data.id)
      if (magnet) {
        lf.value.graphModel.moveNode2Coordinate(args.data.id, magnet.x, magnet.y)
      }
    })

    // 中间节点单击事件
    eventCenter.on('node:click', (args) => {
      if (Date.now() - lastDragEndAt < 250) {
        return
      }
      showProperty(args.data)
    })

    // 边单击（事件名里的空格会被 EventCenter 自动 trim）
    eventCenter.on('edge:click', (args) => {
      const nodeModel = lf.value.getNodeModelById(args.data.sourceNodeId);
      // 同仿钉钉模式：属性面板只给网关出线，非网关出线的跳转类型走右键菜单
      if (!['serial', 'inclusive'].includes(nodeModel['type'])) {
        return;
      }
      skipConditionShow.value = true
      showProperty(args.data)
    })
  }

  eventCenter.on('edge:add', (args) => {
    let graphData = lf.value.getGraphData()
    const previousNodes = getPreviousNodes(graphData['nodes'], graphData['edges'], args.data.sourceNodeId);
    lf.value.changeEdgeType(args.data.id, 'skip')
    // 修改边类型
    lf.value.setProperties(args.data.id, {
      skipType: previousNodes.some(node => node.id === args.data.targetNodeId) ? 'REJECT' : 'PASS'
    })
  })

  // 画布空态引导：跟随节点增删刷新节点计数
  eventCenter.on('node:add,node:delete,node:deleted', () => {
    updateCanvasNodeCount()
  })

  eventCenter.on('blank:click', () => {
    // 常驻侧栏：点画布空白 = 回到「流程配置」（原先是弹流程配置抽屉）。
    // 若当前有节点属性在编，先做一次离开校验；窄屏下顺手收起侧栏把画布让出来。
    openBaseInfo(false)
  })
}

/** 关闭按钮 */
function close() {
  window.parent.postMessage({ method: "close" }, "*");
}

/**
 * 缩放视口：放大/缩小/自适应
 * @param {boolean|string|number} zoom - true=放大(内置刻度), false=缩小(内置刻度), 'fit'=fitView自适应全部节点, number=直接设置缩放比例
 */
const zoomViewport = async (zoom) => {
  if (zoom === true) {
    // 放大（使用 LogicFlow 内置刻度，每次按固定比例放大）
    lf.value.zoom(true);
  } else if (zoom === false) {
    // 缩小（使用 LogicFlow 内置刻度，每次按固定比例缩小）
    lf.value.zoom(false);
  } else if (zoom === 1) {
    // PC 端自适应：zoom(1) 重置缩放 + translateCenter 居中
    lf.value.zoom(1);
    if (lf.value.translateCenter) {
      lf.value.translateCenter();
    }
  } else if (zoom === 'fit') {
    // 自适应：显示全部节点并居中，最大缩放 100%（节点少不放大撑屏）
    if (lf.value.fitView) {
      fitViewAll();
    } else {
      // fallback
      if (lf.value.translateCenter) lf.value.translateCenter();
      lf.value.resetZoom ? lf.value.resetZoom() : lf.value.zoom(1);
    }
  } else if (typeof zoom === 'number') {
    // 直接设置缩放比例
    lf.value.zoom(zoom);
  }
};

const undoOrRedo = async (undo) => {
  if(undo){
    lf.value.undo(undo)
  }else{
    lf.value.redo(undo)
  }
  // 撤销/重做可能删掉节点，刷新空态引导
  nextTick(() => updateCanvasNodeCount());
};
//清空画布：破坏性操作，先确认再执行
const clear = async () => {
    if (disabled.value) return;
    try {
        await ElMessageBox.confirm('清空会删除画布上的全部节点与连线（不影响已保存的数据），确定继续？', '清空画布', {
            confirmButtonText: '清空',
            cancelButtonText: '取消',
            type: 'warning'
        });
    } catch (e) {
        return;
    }
    if (isClassics(logicJson.value.modelValue)) {
        lf.value.clearData()
    } else {
        logicJson.value = {
            ...logicJson.value,
            ...initMimicData
        };
        lf.value.render(logicJson.value);
    }
    markDirty();
    // 清空 / 重置后刷新节点计数（空态引导依赖）
    nextTick(() => updateCanvasNodeCount());
}

/**
 * 下载流程图
 */
function downLoad() {
  lf.value.getSnapshot(logicJson.value.flowName, {
    fileType: 'png',        // 可选：'png'、'webp'、'jpeg'、'svg'
    backgroundColor: themeColors.value.snapshotBg,
  })
}

async function downJson() {
  try {
    getBaseInfo();
    if (lf.value) {
      let graphData = lf.value.getGraphData()
      logicJson.value['nodes'] = graphData['nodes']
      logicJson.value['edges'] = graphData['edges']
    }
    let jsonString = logicFlowJsonToWarmFlow(logicJson.value);

    // 创建 Blob 并触发下载
    const filename = `${logicJson.value.flowName}_${logicJson.value.version}.json`;
    // 格式化用于下载
    const jsonPretty = JSON.stringify(JSON.parse(jsonString), null, 2); // 先 parse 成对象再 stringify
    const blob = new Blob([jsonPretty], { type: 'application/json' });
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (error) {
    console.error('下载失败:', error);
  }
}

</script>

<style>


/* 常驻右侧栏：--wf-panel-w 是侧栏宽度；--wf-canvas-inset 是画布需要让开的宽度
   （宽屏两者相等；窄屏侧栏改为覆盖层，画布不再让位，inset 归零）
   宽度取 420px：实测 3 个页签（图标 + 13px 文字）需要 361px，叠加 32px 内边距 + 滚动条后
   400px 仍会把最后一个页签切掉；420px 是最窄的「3 页签完整可见」宽度 */
.design-root {
  --wf-panel-w: 420px;
  --wf-canvas-inset: var(--wf-panel-w);
  /* 开合过渡：画布让位、侧栏滑出、工具栏跟位共用一条缓动，避免各走各的节奏 */
  --wf-panel-ease: 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 收起后画布吃满整屏（窄屏本来就是覆盖层，inset 已为 0） */
.design-root.is-panel-collapsed {
  --wf-canvas-inset: 0px;
}

/* el-header 包着画布：不要自带白底，否则浅灰画布会被一圈白边框住 */
.el-header {
  --el-header-padding: 0;
  background: transparent !important;
  padding: 0 !important;
  height: auto !important;
}
.container {
  flex: 1;
  /* 常驻侧栏占住右侧：画布只铺满左侧剩余宽度（flex 交叉轴拉伸，不能用 width:100%） */
  width: auto;
  margin-right: var(--wf-canvas-inset, 0px);
  /* 开合侧栏时画布跟着呼吸，不要瞬移 */
  transition: margin-right var(--wf-panel-ease, 0.24s ease);
  /* 高度由 flex 自动撑满顶部栏以下的剩余空间（不再用视口减魔法数），
     iframe 嵌入 / onlyDesignShow 隐藏顶栏 / 移动端 dvh 全部自适应 */
  min-height: 400px;
  border-radius: 0;
  background: var(--wf-bg-page, #eef1f6);
  overflow: hidden;
  /* 关键：禁止浏览器默认触摸手势，让 LogicFlow 接管画布拖动 */
  touch-action: none;
  /* 确保容器有明确的定位上下文和尺寸 */
  position: relative;
}

/* ========== 常驻右侧栏：属性面板 / 流程配置共用同一个容器 ========== */
/* 注意：全局样式里有 `aside { padding: 8px 24px; line-height: 32px; ... }`（ruoyi 遗留的
   演示样式），这里逐项复位，否则面板会被撑出 48px 内边距、行高也会被拉成 32px */
.design-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: var(--wf-panel-w, 0px);
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin: 0;
  padding: 0;
  border-radius: 0;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  background: var(--wf-bg-white, #fff);
  border-left: 1px solid var(--wf-border-lighter, #ebeef5);
  box-shadow: -10px 0 28px rgba(29, 33, 41, 0.05);
  z-index: 12;
  /* 开合走 transform（不占布局、不触发重排），收起后 visibility 一起隐藏，
     避免隐藏面板里的输入框还能被 Tab 聚焦 */
  transition: transform var(--wf-panel-ease, 0.24s ease), visibility var(--wf-panel-ease, 0.24s ease);
}

.design-root.is-panel-collapsed .design-sidebar {
  transform: translateX(100%);
  visibility: hidden;
  pointer-events: none;
}


html.dark .design-sidebar {
  background: var(--wf-bg-color, #141414);
  border-left-color: var(--wf-border-color, #333333);
}

/* 面板外壳：标题区固定、正文区滚动（流程配置用；属性面板自带同款样式） */
.panel-shell {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.panel-header {
  flex: none;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-height: 64px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--wf-border-lighter, #ebeef5);
}

html.dark .panel-header {
  border-bottom-color: var(--wf-border-color, #333333);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--wf-text-primary, #303133);
}

.panel-subtitle {
  font-size: 12px;
  line-height: 1.5;
  color: var(--wf-text-secondary, #909399);
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* 侧栏只有 420px：窄表格 / 长下拉允许横向滚动，不撑破面板 */
  overflow-x: auto;
  padding: 4px 16px 16px;
}

.panel-empty-tip {
  margin: 24px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--wf-text-placeholder, #c0c4cc);
}

/* 流程配置表单：BaseInfo 原按 960px 大对话框设计，收进侧栏后按面板密度排。
   选择器带上 .panel-body 提高优先级，压过 baseInfo.vue 自己的 scoped 规则 */
.design-sidebar .panel-body .dialogForm {
  max-width: 100%;
  margin: 8px auto 0;
}

.design-sidebar .panel-body .el-form-item {
  margin-bottom: 16px;
}

.design-sidebar .panel-body .el-input__wrapper,
.design-sidebar .panel-body .el-select .el-input__wrapper,
.design-sidebar .panel-body .el-textarea__inner {
  border-radius: 8px;
}

.design-sidebar .panel-body .radio-card-warning {
  margin-top: 6px;
  padding: 5px 9px;
  font-size: 11px;
}

/* 开合把手：一条贴在侧栏左边缘的竖条，宽屏 / 窄屏都常驻。
   展开时压在侧栏边框上（右移 -14px），收起时贴到视口右边缘 */
.sidebar-handle {
  position: absolute;
  top: 50%;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 56px;
  margin: 0;
  padding: 0;
  transform: translateY(-50%);
  color: var(--wf-text-secondary, #909399);
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-lighter, #ebeef5);
  border-right: none;
  border-radius: 10px 0 0 10px;
  box-shadow: -4px 0 14px rgba(29, 33, 41, 0.08);
  cursor: pointer;
  z-index: 13;
  transition: right var(--wf-panel-ease, 0.24s ease), color 0.18s ease,
    background-color 0.18s ease, border-color 0.18s ease;
}

.sidebar-handle:not(.is-collapsed) {
  right: calc(var(--wf-canvas-inset, 0px) - 14px);
}

.sidebar-handle svg {
  width: 14px;
  height: 14px;
  /* 展开时箭头朝右（点一下收到右边）；收起时朝左（点一下拉出来） */
  transition: transform var(--wf-panel-ease, 0.24s ease);
}

.sidebar-handle:not(.is-collapsed) svg {
  transform: rotate(180deg);
}

.sidebar-handle:hover,
.sidebar-handle:focus-visible {
  color: var(--wf-primary, #409eff);
  background: var(--wf-primary-light, #ecf5ff);
  border-color: var(--wf-primary, #409eff);
  outline: none;
}

html.dark .sidebar-handle {
  color: var(--wf-text-secondary, #a3a6ad);
  background: var(--wf-bg-white, #1f2937);
  border-color: var(--wf-border-color, #333333);
}

html.dark .sidebar-handle:hover,
html.dark .sidebar-handle:focus-visible {
  color: var(--wf-primary, #409eff);
  background: rgba(64, 158, 255, 0.16);
  border-color: var(--wf-primary, #409eff);
}


/* 不支持 dvh 的老浏览器：根容器回退到 100vh（行内 100dvh 会被忽略） */
@supports not (height: 100dvh) {
  .design-root {
    height: 100vh;
  }
}

/* LogicFlow 内部 SVG 画布层 */
.container :deep(.lf-canvas-overlay),
.container :deep(.lf-graph) {
  touch-action: none;
}

/* 确保 SVG 画布在移动端可见且有正确尺寸（真机关键修复） */
.container :deep(svg) {
  display: block !important;
  width: 100% !important;
  height: 100% !important;
}

.container :deep(.lf-container-bg) {
  display: block !important;
  background-color: var(--wf-bg-page, #f8f9fb) !important;
}

html.dark .container {
  background-color: var(--wf-bg-color, #141414) !important;
}

html.dark .container :deep(.lf-container-bg) {
  background-color: var(--wf-bg-color, #141414) !important;
}

/* ========== 画布空态引导（.container 的兄弟覆盖层，见模板内注释） ========== */
.canvas-empty-guide {
  position: absolute;
  /* 只覆盖画布区：右侧留给常驻侧栏 */
  inset: 0 var(--wf-canvas-inset, 0px) 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  /* 纯引导展示：不挡画布的点击 / 拖拽 */
  pointer-events: none;
  user-select: none;
  z-index: 1;
  color: var(--wf-text-placeholder, #c0c4cc);
  transition: right var(--wf-panel-ease, 0.24s ease);
}

.canvas-empty-guide-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  color: var(--wf-text-placeholder, #c0c4cc);
  opacity: 0.8;
}

.canvas-empty-guide-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 500;
  color: var(--wf-text-secondary, #909399);
}

.canvas-empty-guide-desc {
  margin: 0;
  font-size: 13px;
  color: var(--wf-text-placeholder, #c0c4cc);
  line-height: 1.6;
}

/* ========== Logo 水印 ========== */
.logo-text {
  position: absolute;
  font-weight: bold;
  /* 右侧让开常驻侧栏 */
  right: calc(var(--wf-canvas-inset, 0px) + 50px);
  bottom: 10px;
  font-size: 14px;
  color: var(--wf-text-placeholder, #c0c4cc);
  opacity: 0.5;
  z-index: 1;
  letter-spacing: 1px;
  user-select: none;
  transition: right var(--wf-panel-ease, 0.24s ease);
}

/* ========== 顶部导航栏 ========== */
.design-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--wf-bg-white, #fff);
  border-bottom: 1px solid #e2e8f0;
  min-height: 56px;
}

.header-left {
  flex: 0 0 auto;
  min-width: 180px;
}

.header-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  justify-content: center;
}

.header-right {
  flex: 0 0 auto;
  min-width: 120px;
  display: flex;
  justify-content: flex-end;
}

/* ========== 流程名称 ========== */
.flow-name-wrapper {
  display: flex;
  align-items: center;
}

.flow-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 15px;
  max-width: 320px;
  color: #1e293b;
  padding: 8px 14px;
  background: var(--wf-bg-white, #fff);
  border-radius: 8px;
  border: 1px solid var(--wf-border-light, #e2e8f0);
  cursor: default;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  font-family: inherit;
}

html.dark .flow-name {
  color: var(--wf-text-primary) !important;
}

.flow-name-text {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flow-name:hover {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* 可点击的流程名称 = 流程配置面板入口：button 语义 + 编辑铅笔暗示，键盘可达 */
.flow-name--editable {
  cursor: pointer;
}

.flow-name--editable:focus-visible {
  outline: 2px solid var(--wf-primary, #409eff);
  outline-offset: 2px;
}

.flow-name--editable:active {
  transform: scale(0.98);
}

.flow-name-edit-icon {
  flex: none;
  color: #94a3b8;
  transition: color 0.2s ease;
}

.flow-name--editable:hover .flow-name-edit-icon,
.flow-name--editable:focus-visible .flow-name-edit-icon {
  color: var(--wf-primary, #409eff);
}

/* ========== 保存按钮 ========== */
.save-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--wf-primary, #409eff) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  font-size: 14px;
  padding: 9px 22px !important;
  border-radius: 10px !important;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.28);
  transition: all 0.2s ease !important;
}

.save-btn:hover {
  background: var(--wf-primary-dark, #2b7de9) !important;
}

/* Element Plus 会把按钮默认插槽内容包进一层 span，
   导致 .save-btn 上的 gap 作用不到「图标 + 文字」之间。
   这里让该包裹层成为 flex 容器，恢复图标与文字的间距与垂直居中。 */
.save-btn > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.save-btn .save-icon {
  width: 16px;
  height: 16px;
}

/* 未保存小圆点：白点 + 主色描边，在实心主色按钮上依然可辨 */
.save-btn .save-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.28);
}

/* ========== 工具栏操作帮助（popover 挂到 body，必须写全局样式） ========== */
.design-help {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}

.design-help-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--wf-text-secondary, #909399);
  margin: 2px 0 8px;
}

.design-help-title:not(:first-child) {
  margin-top: 14px;
}

.design-help-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.design-help-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--wf-text-primary, #303133);
}

.design-help-list kbd {
  flex: none;
  padding: 2px 6px;
  border-radius: var(--wf-radius-sm, 4px);
  border: 1px solid var(--wf-border-light, #dcdfe6);
  background: var(--wf-bg-page, #f5f7fa);
  color: var(--wf-text-regular, #606266);
  font-family: inherit;
  font-size: 12px;
}

html.dark .design-help-list kbd {
  border-color: var(--wf-border-color, #333333);
  background: rgba(255, 255, 255, 0.06);
  color: #e5e5e5;
}

/* ========== 保存前检查清单 ========== */
.flow-check-dialog .flow-check-desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--wf-text-regular, #606266);
}

.flow-check-dialog .flow-check-list {
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 320px;
  overflow-y: auto;
}

.flow-check-dialog .flow-check-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--wf-radius-sm, 4px);
  font-size: 13px;
  line-height: 1.5;
  border-left: 3px solid var(--wf-danger, #f56c6c);
  background: rgba(245, 108, 108, 0.06);
  margin-bottom: 8px;
}

/* 可定位项：点击选中并居中到画布节点 */
.flow-check-dialog .flow-check-list li.is-locatable {
  cursor: pointer;
}

.flow-check-dialog .flow-check-list li.is-locatable:hover {
  background: rgba(245, 108, 108, 0.12);
}

.flow-check-name {
  flex: none;
  font-weight: 600;
  color: var(--wf-text-primary, #303133);
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-check-message {
  flex: 1;
  min-width: 0;
  color: var(--wf-text-regular, #606266);
}

.flow-check-action {
  flex: none;
  font-size: 12px;
  color: var(--wf-primary, #409eff);
}

/* ========== 暗黑模式适配（CSS 变量驱动） ========== */
html.dark .design-header {
  background: var(--wf-bg-white);
  border-bottom-color: var(--wf-border-color);
}

/* 暗黑模式：保存按钮统一主色，扁平 */
html.dark .save-btn,
html.dark .toolbar-save-btn {
  background: var(--wf-primary, #409eff) !important;
  box-shadow: none !important;
}
html.dark .save-btn:hover,
html.dark .toolbar-save-btn:hover {
  background: var(--wf-primary-dark, #2b7de9) !important;
}

/* 工具栏区域（el-header 包着画布）保持透明，避免盖住浅灰画布 */
html.dark .el-header {
  --el-bg-color: transparent;
  background-color: transparent;
}

html.dark .design-toolbar {
  background: var(--wf-bg-white, #1d1e1f);
  border-color: var(--wf-border-color, #333);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
}

html.dark .toolbar-group {
  border-right-color: var(--wf-border-color, #333);

  .el-button {
    color: var(--wf-text-regular, #b0b0b0);
    background-color: transparent;
    border: none;

    &:hover,
    &:focus {
      color: var(--wf-primary);
      background-color: rgba(64, 158, 255, 0.12);
      border: none;
    }
  }
}

/* flow-name 的暗黑样式已在 L830-832 通过 CSS 变量 + !important 处理 */

html.dark .logo-text {
  color: var(--wf-text-secondary);
}

/* ========== 工具栏：浮在画布右上的图标组 ========== */
.design-toolbar {
  position: absolute;
  top: 12px;
  /* 右侧让开常驻侧栏 */
  right: calc(var(--wf-canvas-inset, 0px) + 16px);
  z-index: 10;
  display: inline-flex;
  align-items: center;
  padding: 4px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-lighter, #ebeef5);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(29, 33, 41, 0.08);
  transition: right var(--wf-panel-ease, 0.24s ease);
}

.toolbar-group {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 4px;
  margin: 0;
  border-right: 1px solid var(--wf-border-lighter, #ebeef5);
}

.toolbar-group .el-button {
  width: 32px;
  height: 32px;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  color: var(--wf-text-regular, #606266);
  border-radius: 8px;
}

.toolbar-group .el-button:hover,
.toolbar-group .el-button:focus {
  color: var(--wf-primary, #409eff);
  background: var(--wf-primary-light, #ecf5ff);
  border: none;
}

.toolbar-group:last-child {
  border-right: none;
  padding-right: 0;
}

/* ========== 工具栏内保存按钮 ========== */
.toolbar-save-btn {
  background: var(--wf-primary, #409eff) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  border-radius: 8px !important;
  box-shadow: none;
  transition: background-color 0.2s ease !important;
}

.toolbar-save-btn:hover {
  background: var(--wf-primary-dark, #2b7de9) !important;
}

/* ========== 工具栏开关态（框选 / 小地图）：常驻蓝，表达"当前已打开" ========== */
.toolbar-group .el-button.is-active {
  color: var(--wf-primary, #409eff);
  background: var(--wf-primary-light, #ecf5ff);
}

html.dark .toolbar-group .el-button.is-active {
  background: rgba(64, 158, 255, 0.18);
}

/* ========== 画布辅助：框选选区 ========== */
/* 引擎默认是橙色虚线（#ffab03），换成主色柔光矩形，与节点选中框同一套视觉 */
.container .lf-selection-select {
  border: 1.5px solid var(--wf-primary, #409eff);
  background: rgba(64, 158, 255, 0.08);
  border-radius: var(--wf-radius-sm, 4px);
}

html.dark .container .lf-selection-select {
  background: rgba(64, 158, 255, 0.16);
}

/* ========== 画布辅助：小地图 ========== */
.container .lf-mini-map {
  padding: 6px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-lighter, #ebeef5);
  border-radius: var(--wf-radius, 8px);
  box-shadow: 0 4px 16px rgba(29, 33, 41, 0.08);
  /* 画布内浮层：高于节点 / 连线与空态引导，低于常驻侧栏（12） */
  z-index: 8;
}

.container .lf-mini-map-graph {
  background: var(--wf-bg-page, #eef1f6);
  border-radius: var(--wf-radius-sm, 4px);
}

/* 缩略图内层是插件另起的 LogicFlow 实例，不跟随我们的主题，底色要单独盖住 */
.container .lf-mini-map .lf-graph {
  background: var(--wf-bg-page, #eef1f6);
}

/* 取景框：主色柔光，和设计器选中框同一套语义 */
.container .lf-minimap-viewport {
  background-color: rgba(64, 158, 255, 0.14);
  border: 1px solid rgba(64, 158, 255, 0.45);
  border-radius: var(--wf-radius-sm, 4px);
}

html.dark .container .lf-mini-map {
  background: var(--wf-bg-color, #141414);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

/* 暗黑下 bgColor / bgPage 是同一个值，缩略图会和容器糊成一片，用浮层底色 token 抬一档 */
html.dark .container .lf-mini-map-graph,
html.dark .container .lf-mini-map .lf-graph {
  background: var(--wf-tooltip-bg, #1f1f1f);
}

/* ========== 响应式适配：平板端 (<= 1024px) ========== */
@media (max-width: 1024px) {
  /* 侧栏改为覆盖画布：画布不再让位，面板宽度按视口收窄 */
  .design-root {
    --wf-panel-w: min(420px, 88vw);
    --wf-canvas-inset: 0px;
  }

  .design-sidebar {
    box-shadow: -12px 0 32px rgba(29, 33, 41, 0.16);
  }

  /* 覆盖层展开时把手跟着面板左边缘走（画布 inset 恒为 0，不能再用它算位置） */
  .sidebar-handle:not(.is-collapsed) {
    right: calc(var(--wf-panel-w, 0px) - 14px);
  }

  .design-header {
    padding: 10px 12px;
    min-height: 50px;
  }

  .header-left {
    min-width: 120px;
  }

  .header-right {
    min-width: 80px;
  }

  /* 流程名称缩短 */
  .flow-name {
    max-width: 180px;
    font-size: 14px;
    padding: 6px 12px;
  }

  /* 保存按钮缩小 */
  .save-btn {
    padding: 8px 16px !important;
    font-size: 13px;
  }

  .toolbar-group {
    gap: 2px;
    padding-right: 8px;
    margin-right: 8px;
  }

  /* 平板端工具栏仍浮在右上 */
  .design-toolbar {
    top: 8px;
    right: calc(var(--wf-canvas-inset, 0px) + 8px);
    padding: 3px;
  }

  /* Logo 水印隐藏，避免遮挡 */
  .logo-text {
    display: none;
  }
}

/* ========== 响应式适配：手机端 (<= 768px) ========== */
@media (max-width: 768px) {
  .design-header {
    padding: 6px 8px;
    min-height: 44px;
    /* 不换行，三栏强制一行 */
  }

  /* 步骤标签独占一行居中 */
  .header-left {
    min-width: auto;
    flex: 0 0 auto;
    overflow: hidden;
    max-width: 30%;
  }

  .header-center {
    position: static;
    left: auto;
    transform: none;
    flex: 1 1 auto; /* 占据中间剩余空间，不换行 */
    justify-content: center;
  }

  .header-right {
    min-width: auto;
    flex: 0 0 auto;
    justify-content: flex-end;
  }

  /* 流程名称进一步压缩 */
  .flow-name {
    max-width: 120px;
    font-size: 12px;
    padding: 4px 8px;
    border-radius: 6px;
  }

  /* 保存按钮更小 */
  .save-btn {
    padding: 6px 10px !important;
    font-size: 12px;
    border-radius: 8px !important;
    box-shadow: 0 2px 6px rgba(16, 185, 129, 0.2);
  }

  .save-btn .save-icon {
    width: 14px;
    height: 14px;
  }

  /* 工具栏：保持右上浮层，窄屏可横向滑 */
  .design-toolbar {
    top: 8px;
    right: 8px;
    max-width: calc(100% - 16px);
    white-space: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }
  .design-toolbar::-webkit-scrollbar {
    display: none;
  }

  .toolbar-group .el-button {
    width: 28px;
    height: 28px;
  }

  /* 画布容器高度调整 */
  .container {
    height: calc(100dvh - 90px);
    min-height: 300px;
  }
}

/* ========== 响应式适配：超小屏 (<= 480px) ========== */
@media (max-width: 480px) {
  .design-header {
    padding: 4px 6px;
    min-height: 36px;
  }

  /* 流程名称极短 */
  .flow-name {
    max-width: 70px;
    font-size: 11px;
    padding: 3px 5px;
    border-radius: 4px;
  }

  .flow-name svg {
    display: none; /* 超小屏隐藏图标省空间 */
  }

  /* 保存按钮极小 */
  .save-btn {
    padding: 4px 7px !important;
    font-size: 11px;
    gap: 2px;
    border-radius: 6px !important;
  }

  .save-btn .save-icon {
    width: 12px;
    height: 12px;
  }

  /* 隐藏工具栏分隔线，节省空间 */
  .toolbar-group {
    padding-right: 2px;
    margin-right: 2px;
    border-right: none;
    gap: 2px;
  }

  /* 画布容器更矮 */
  .container {
    height: calc(100dvh - 75px);
    min-height: 220px;
  }
}
</style>
