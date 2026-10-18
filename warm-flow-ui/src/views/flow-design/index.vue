<template>
  <div class="design-root" :style="headerDiv">
    <!-- 顶部导航栏 -->
    <div class="design-header" v-if="!onlyDesignShow">
      <!-- 左侧：流程名称（点击打开流程配置抽屉） -->
      <div class="header-left">
        <div class="flow-name-wrapper">
          <el-tooltip content="点击编辑流程编码、名称、类别等配置" placement="bottom" :show-after="500">
            <button type="button" class="flow-name flow-name--editable" @click="openBaseInfo" v-if="!onlyDesignShow">
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

      <!-- 中间：占位（原步骤切换已移除，流程配置收进画布空白点击弹出的抽屉） -->
      <div class="header-center"></div>

      <!-- 右侧：保存按钮 -->
      <div class="header-right">
        <el-button class="save-btn" size="default" @click="saveJsonModel" v-if="!disabled">
          <svg-icon icon-class="save" class="save-icon"/>
          <span>保存</span>
        </el-button>
      </div>
    </div>

    <el-header :style="headerStyle">
      <div class="design-toolbar">
        <div>
          <span class="toolbar-group">
            <el-tooltip content="缩小" placement="bottom"><el-button size="small" icon="ZoomOut" @click="zoomViewport(false)"></el-button></el-tooltip>
            <!-- 自适应：所有端均 fitView 显示全部节点 -->
            <el-tooltip content="自适应" placement="bottom"><el-button size="small" icon="Rank" @click="zoomViewport('fit')"></el-button></el-tooltip>
            <el-tooltip content="放大" placement="bottom"><el-button size="small" icon="ZoomIn" @click="zoomViewport(true)"></el-button></el-tooltip>
          </span>
          <span class="toolbar-group">
            <el-tooltip content="上一步" placement="bottom"><el-button size="small" icon="DArrowLeft" @click="undoOrRedo(true)"></el-button></el-tooltip>
            <el-tooltip content="下一步" placement="bottom"><el-button size="small" icon="DArrowRight" @click="undoOrRedo(false)"></el-button></el-tooltip>
            <el-tooltip content="清空" placement="bottom"><el-button size="small" icon="Delete" @click="clear()"></el-button></el-tooltip>
          </span>
          <span class="toolbar-group">
            <el-tooltip content="下载流程图" placement="bottom"><el-button size="small" icon="Picture" @click="downLoad"></el-button></el-tooltip>
            <el-tooltip content="下载JSON" placement="bottom"><el-button size="small" icon="Download" @click="downJson"></el-button></el-tooltip>
          </span>
          <!-- 设计器模型切换已统一收进「流程配置」抽屉的单选（已有流程切换前会确认），工具栏不再保留入口 -->
          <!-- 流程配置：点画布空白同样可打开；嵌入模式的定义元数据由宿主管理，不提供入口 -->
          <span class="toolbar-group" v-if="!onlyDesignShow && !disabled">
            <el-tooltip content="流程配置" placement="bottom">
              <el-button size="small" icon="Setting" @click="openBaseInfo"></el-button>
            </el-tooltip>
          </span>
          <span class="toolbar-group" v-if="onlyDesignShow && !disabled">
            <el-tooltip content="保存" placement="bottom"><el-button size="small" class="toolbar-save-btn" @click="saveJsonModel">
              <svg-icon icon-class="save" style="width: 14px; height: 14px;"/>
            </el-button></el-tooltip>
          </span>
        </div>
      </div>

      <!-- 流程配置抽屉：由点画布空白 / 顶部流程名称 / 工具栏按钮打开；嵌入模式的定义元数据由宿主管理，不渲染 -->
      <el-drawer
        v-if="!onlyDesignShow"
        v-model="baseInfoDrawer"
        direction="rtl"
        :size="baseInfoDrawerSize"
        :destroy-on-close="false"
        :close-on-click-modal="true"
        :before-close="attemptCloseBaseInfo"
        class="base-info-drawer"
      >
        <template #header>
          <div class="base-info-drawer-header">
            <div class="base-info-drawer-title">流程配置</div>
            <div class="base-info-drawer-subtitle">编码、名称、模型等定义信息，随画布一起保存</div>
          </div>
        </template>
        <BaseInfo ref="baseInfoRef" class="base-info-in-drawer"
                  :logic-json="logicJson" :category-list="categoryList" :form-path-list="formPathList"
                  :definition-id="definitionId" :disabled="disabled"
                  @update:flow-name="handleFlowNameUpdate" @update:model-value="handleModelValueUpdate"/>
        <template #footer>
          <div class="base-info-drawer-footer">
            <span class="base-info-drawer-footer-hint">配置随下次保存一起提交</span>
            <span class="base-info-drawer-footer-actions">
              <el-button @click="attemptCloseBaseInfo()">取 消</el-button>
              <el-button type="primary" @click="confirmBaseInfo">确 定</el-button>
            </span>
          </div>
        </template>
      </el-drawer>

      <!-- 自定义拖拽侧边栏：延迟显示避免与 LogicFlow DOM 冲突 -->
      <DiagramSidebar
        v-if="sidebarVisible"
        class="diagram-sidebar"
        @dragInNode="handleDragInNode"
      />

      <!-- 节点属性面板：抽屉 teleport 到 body，原地只留占位符。
           必须放在 .container 外面！LogicFlow 初始化会接管 .container 的内部 DOM，
           放在里面会被清掉锚点，之后 Vue 每次 patch Design 子树都会 insertBefore null 崩溃 -->
      <PropertySetting ref="propertySettingRef" :node="nodeClick" :lf="lf" :disabled="disabled"
                       :skipConditionShow="skipConditionShow" :nodes="nodes" :skips="skips"
                       :form-path-list="formPathList">
      </PropertySetting>

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
        <p class="canvas-empty-guide-desc">从左侧拖入节点开始设计 · 点击空白处可打开流程配置</p>
      </div>
      <div class="logo-text">Warm-Flow</div>
    </el-header>

    <!-- 弹框组件 -->
    <EdgeTooltip
        v-if="tooltipVisible"
        :position="tooltipPosition"
        :tooltipEdge="tooltipEdge"
        @option-click="handleOptionClick"
        @close-tooltip="tooltipVisible = false"
    />
  </div>
</template>

<script setup name="Design">
import LogicFlow from "@logicflow/core";
import "@logicflow/core/lib/style/index.css";
import {InsertNodeInPolyline, Menu, Snapshot} from '@logicflow/extension';
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
import { getMagnetPosition, SNAP_EPSILON } from "@/components/design/common/js/snapMagnet";
import {
  relayoutToMimic,
  clearEdgePoints,
  normalizeClassicNodes,
  resetEdgeTextPositions,
  resetHistory
} from "@/components/design/common/js/relayout";
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

// 流程配置抽屉：画布是唯一视图，定义元数据（编码 / 名称 / 类别 / 表单 / 监听器等）收进抽屉。
// 打开入口：点画布空白、点顶部流程名称、工具栏「流程配置」按钮；新建流程时自动打开一次。
const baseInfoDrawer = ref(false);

// 抽屉宽度响应式：与节点属性面板（propertySetting）保持一致——手机（≤768px）100%，桌面 37%
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280);
const onViewportResize = () => { viewportWidth.value = window.innerWidth; };
window.addEventListener('resize', onViewportResize);
const baseInfoDrawerSize = computed(() => viewportWidth.value <= 768 ? '100%' : '37%');

// 画布空态引导：节点数为 0（如清空后）时中央显示引导文案
const canvasNodeCount = ref(0);
const canvasEmpty = computed(() => canvasNodeCount.value === 0);

function updateCanvasNodeCount() {
  if (lf.value?.getGraphData) {
    canvasNodeCount.value = (lf.value.getGraphData().nodes || []).length;
  }
}

/**
 * 全局键盘快捷键：
 * - Cmd/Ctrl + S：保存流程（替代伸手找按钮）
 * - Delete：删除选中元素（经典模式由 LogicFlow 内置键盘处理，这里补齐仿钉钉模式）
 */
function onGlobalKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && (e.key === 's' || e.key === 'S')) {
    e.preventDefault();
    if (!disabled.value) saveJsonModel();
    return;
  }
  if (e.key === 'Delete' && !disabled.value && lf.value && !isClassics(logicJson.value.modelValue)) {
    const target = e.target;
    // 焦点在输入控件里时不劫持（输入框内删除字符是正常行为）
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
    const elements = lf.value.getSelectElements(true);
    if (elements && (elements.nodes.length || elements.edges.length)) {
      e.preventDefault();
      lf.value.clearSelectElements();
      elements.edges.forEach((edge) => lf.value.deleteEdge(edge.id));
      elements.nodes.forEach((node) => lf.value.deleteNode(node.id));
    }
  }
}

function openBaseInfo() {
  baseInfoDrawer.value = true;
}

/**
 * 关闭流程配置抽屉（统一出口）：关闭按钮 / 遮罩 / Esc / 取消按钮都走这里。
 * 有未保存改动时先弹确认，防止误关丢配置；无改动直接收起。
 * @param done el-drawer before-close 回调，footer 取消按钮调用时不传（直接改 v-model）
 */
function attemptCloseBaseInfo(done) {
  const comp = proxy.$refs.baseInfoRef;
  const closeIt = () => {
    comp?.markClean?.();
    if (typeof done === 'function') {
      done();
    } else {
      baseInfoDrawer.value = false;
    }
  };
  if (!disabled.value && comp?.isDirty?.()) {
    ElMessageBox.confirm('流程配置尚未保存，配置会随画布保存一起提交，确定放弃本次修改？', '放弃修改', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
      type: 'warning'
    }).then(closeIt).catch(() => {});
    return;
  }
  closeIt();
}

/** 抽屉「确定」：校验通过才收起，失败留在抽屉内就近提示 */
async function confirmBaseInfo() {
  const comp = proxy.$refs.baseInfoRef;
  const valid = await comp?.validate();
  if (valid === false) return;
  // 校验通过即视为确认当前配置：刷新脏检测基准后直接收起，不再触发「放弃修改」确认
  comp?.markClean?.();
  baseInfoDrawer.value = false;
}

// 抽屉打开时：清除上次留下的校验红框（首屏不报错）、刷新脏检测基准、新建流程聚焦第一个必填字段。
// 例外：保存失败自动打开抽屉时保留红框（用户要就近改错），由 keepValidationOnNextOpen 控制。
const keepValidationOnNextOpen = ref(false);
watch(baseInfoDrawer, (open) => {
  if (!open) return;
  const keepValidate = keepValidationOnNextOpen.value;
  keepValidationOnNextOpen.value = false;
  // 抽屉首次打开时 BaseInfo 才懒渲染，nextTick 可能拿不到 ref，做短重试兜底
  const tryInit = (tries = 0) => {
    const comp = proxy.$refs.baseInfoRef;
    if (!comp && tries < 10) {
      setTimeout(() => tryInit(tries + 1), 50);
      return;
    }
    if (!keepValidate) comp?.clearValidation?.();
    comp?.markClean?.();
    if (!definitionId.value) comp?.focusFirstField?.();
  };
  proxy.$nextTick(() => tryInit());
});

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
    // 全局键盘快捷键（保存 / 删除选中）
    window.addEventListener('keydown', onGlobalKeydown);
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
    // 画布是唯一视图：初始加载即初始化（原由「切到流程设计」步骤触发，Tab 移除后在此补上）
    handleModelValueUpdate();
    // 新建流程：没有定义 id，直接弹出流程配置抽屉让用户先填编码 / 名称 / 模型
    if (!definitionId.value && !onlyDesignShow.value) {
      baseInfoDrawer.value = true;
    }
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
      keyboard: isClassics(logicJson.value.modelValue) && !disabled.value ? {
        enabled: true,
        shortcuts: [
          {
            keys: ["delete"],
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
  cleanupMessageListener();
  window.removeEventListener('resize', handleMobileResize);
  window.removeEventListener('resize', onViewportResize);
  window.removeEventListener('keydown', onGlobalKeydown);
  // 离开设计器页面，复位设计态标记
  window.__WF_FLOW_DESIGN_MODE__ = false;
});

// 监听窗口变化，真机旋转屏幕时重绘（带防抖 + 标志位屏蔽抽屉操作干扰）
let _resizeTimer = null;
let _drawerActive = false; // 抽屉操作期间禁止 resize 重绘画布 + 阻止触摸事件桥接
let _lastCanvasSize = { w: 0, h: 0 };
function handleMobileResize() {
  if (_drawerActive) return; // 抽屉操作期间跳过
  clearTimeout(_resizeTimer);
  _resizeTimer = setTimeout(() => {
    const container = proxy.$refs.containerRef;
    if (!container || !lf.value?.resize) return;
    const w = container.clientWidth;
    const h = container.clientHeight;
    // 容器尺寸无显著变化（<5px 容差）则跳过，过滤掉抽屉/overlay 导致的微调
    if (Math.abs(w - _lastCanvasSize.w) < 5 && Math.abs(h - _lastCanvasSize.h) < 5 && _lastCanvasSize.w > 0) return;
    _lastCanvasSize = { w, h };
    lf.value.resize();
  }, 300); // 提高到 300ms 确保布局稳定后再判断
}
window.addEventListener('resize', handleMobileResize);

// 抽屉打开时标记
window._markDrawerOpen = () => { _drawerActive = true; };
// 抽屉关闭动画完成后解除标记，并触发一次移动端画布适配
// 延迟 350ms：Element Plus drawer 关闭 transition 默认 300ms，加 50ms 缓冲
window._markDrawerClosed = () => {
  setTimeout(() => {
    _drawerActive = false;
    // 抽屉关闭后一次性适配画布（仅移动端/平板），替代 propertySetting 中分散的 fitView 调用
    if (isMobileDevice() && lf.value?.resize) {
      lf.value.resize();
      requestAnimationFrame(() => {
        if (lf.value?.fitView) lf.value.fitView(40, 20);
      });
    }
  }, 350);
};

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
  // 抽屉懒渲染：从未打开过配置抽屉时表单尚未挂载，此时用 logicJson 的初始值即可
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

  // 模型切换重建（原工具栏 switchModel 链路，现由抽屉单选触发）：
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
        // 转换是整体重建，把转换后的状态设为新的撤销基准
        resetHistory(lf.value);
        // 整体重建后刷新节点计数（空态引导依赖）
        updateCanvasNodeCount();
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
  if (!onlyDesignShow.value) {
    // 抽屉懒渲染：从未打开过配置抽屉时表单尚未挂载，此时不需要校验（初始值即当前值）
    const baseInfoComp = proxy.$refs.baseInfoRef;
    if (baseInfoComp) {
      let validate = await baseInfoComp.validate();
      if (!validate) {
        loadingInstance.close();
        // 就近改错：打开配置抽屉并定位到第一个错误项（保留红框，不让「打开即清校验」擦掉）
        keepValidationOnNextOpen.value = true;
        baseInfoDrawer.value = true;
        proxy.$modal.msgWarning("请先完善流程配置中标红项");
        return;
      }
    }
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
   * 检查抽屉是否处于打开状态（打开期间禁止向 LogicFlow 转发触摸事件）
   *
   * 三级判断：
   * 1. _drawerActive 标志位 — 代码显式标记的打开/关闭窗口期
   * 2. .el-drawer__open — Element Plus drawer 打开时的 DOM class
   * 3. 排除 .el-drawer__close — 抽屉正在执行关闭动画（此时不应拦截用户对画布的操作）
   */
  function isDrawerOpen() {
    if (_drawerActive) return true;
    const drawerEl = document.querySelector('.el-drawer');
    if (!drawerEl) return false;
    // 有 __open 且无 __close → 确实是打开状态
    if (drawerEl.classList.contains('el-drawer__open') &&
        !drawerEl.classList.contains('el-drawer__close')) {
      return true;
    }
    return false;
  }

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

    // 【修复】抽屉打开期间：阻止触摸事件转发给 LogicFlow，防止画布被意外拖拽
    if (isDrawerOpen() && e.pointerType === 'touch') {
      e.preventDefault();
      return;
    }

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
    // 【修复】抽屉打开期间：完全拦截触摸移动事件
    if (isDrawerOpen() && e.pointerType === 'touch') {
      e.preventDefault();
      return;
    }

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
    // 【修复】抽屉打开期间：拦截触摸释放事件
    if (isDrawerOpen() && e.pointerType === 'touch') {
      resetTapState();
      isDragging = false;
      return;
    }

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
  // 只有仿钉钉模式才初始化菜单
  if (!isClassics(logicJson.value.modelValue)) {
    // 为菜单追加选项（必须在 lf.render() 之前设置）
    lf.value.extension.menu.setMenuConfig({
      nodeMenu: [],
      edgeMenu: [],
    });
  }
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
}
function initEvent() {
  const { eventCenter } = lf.value.graphModel

  if (!isClassics(logicJson.value.modelValue)) {
    // 更新节点名称
    eventCenter.on('update:nodeName', (data) => {
      lf.value.updateText(data.id, data.nodeName)
      lf.value.setProperties(data.id, {
        nodeName: data.nodeName
      })
    })

    // 网关节点单击事件
    eventCenter.on('node:click', (args) => {
      nodeClick.value = args.data
      if (isGateWay(nodeClick.value.type)) {
        gatewayAddNode(lf.value, nodeClick.value);
      }
    })

    eventCenter.on('edit:node', (args) => {
      if (args.click) {
        nodeClick.value = lf.value.getNodeModelById(args.id)
        let graphData = lf.value.getGraphData()
        nodes.value = graphData['nodes']
        skips.value = graphData['edges']
        proxy.$nextTick(() => {
          propertySettingRef.value.show()
        })
      }
    })

    // 单击边
    eventCenter.on('show:EdgeSetting', (args) => {
      nodeClick.value = lf.value.getEdgeModelById(args.id)
      const nodeModel = lf.value.getNodeModelById(nodeClick.value.sourceNodeId);
      skipConditionShow.value = ['serial', 'inclusive'].includes(nodeModel['type'])
      let graphData = lf.value.getGraphData()
      nodes.value = graphData['nodes']
      skips.value = graphData['edges']
      proxy.$nextTick(() => {
        propertySettingRef.value.show(['serial', 'inclusive'].includes(nodeModel['type']))
      })
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
    // 拖动结束后 LogicFlow 还会补发一次 node:click，直接就把属性抽屉弹出来了。
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
      nodeClick.value = args.data
      let graphData = lf.value.getGraphData()
      nodes.value = graphData['nodes']
      skips.value = graphData['edges']
      proxy.$nextTick(() => {
        propertySettingRef.value.show()
      })
    })

    // 边双击事件
    eventCenter.on('edge:click  ', (args) => {
      nodeClick.value = args.data
      const nodeModel = lf.value.getNodeModelById(nodeClick.value.sourceNodeId);
      skipConditionShow.value = ['serial', 'inclusive'].includes(nodeModel['type'])
      let graphData = lf.value.getGraphData()
      nodes.value = graphData['nodes']
      skips.value = graphData['edges']
      proxy.$nextTick(() => {
        propertySettingRef.value.show(['serial', 'inclusive'].includes(nodeModel['type']))
      })
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
    // 手势区分意图：选中态下点空白 = 收起属性面板（只做取消选中）；
    // 空态下点空白 = 打开流程配置。两个意图不叠加，避免「取消选中」永远伴随抽屉弹出。
    if (nodeClick.value != null) {
      nodeClick.value = null
      proxy.$nextTick(() => {
        propertySettingRef.value.handleClose()
      })
      return
    }
    if (!onlyDesignShow.value) {
      baseInfoDrawer.value = true
    }
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
//清空
const clear = async () => {
    if (isClassics(logicJson.value.modelValue)) {
        lf.value.clearData()
    } else {
        logicJson.value = {
            ...logicJson.value,
            ...initMimicData
        };
        lf.value.render(logicJson.value);
    }
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


/* el-header 包着画布：不要自带白底，否则浅灰画布会被一圈白边框住 */
.el-header {
  --el-header-padding: 0;
  background: transparent !important;
  padding: 0 !important;
  height: auto !important;
}
.container {
  flex: 1;
  width: 100%;
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
  inset: 0;
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
  right: 50px;
  bottom: 10px;
  font-size: 14px;
  color: var(--wf-text-placeholder, #c0c4cc);
  opacity: 0.5;
  z-index: 1;
  letter-spacing: 1px;
  user-select: none;
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

/* 可点击的流程名称 = 流程配置抽屉入口：button 语义 + 编辑铅笔暗示，键盘可达 */
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

/* ========== 流程配置抽屉 ========== */
.base-info-drawer-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.base-info-drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--wf-text-primary, #303133);
  line-height: 1.4;
}

.base-info-drawer-subtitle {
  font-size: 12px;
  color: var(--wf-text-secondary, #909399);
  line-height: 1.5;
}

/* BaseInfo 根节点 .app-container 自带 padding:0，抽屉内由外层统一给呼吸感。
   el-drawer teleport 到 body、class 与 el-drawer 同元素，本文件 style 无 scoped，
   直接用 .el-drawer.base-info-drawer 全局选择器（:deep 与后代形式都无效） */
.base-info-in-drawer {
  padding: 4px 2px 16px;
}

/* 抽屉内表单密度收紧：BaseInfo 原按 960px 大对话框设计，放进 680px 抽屉按面板密度排，
   行距 / 标题 / 卡片整体收一档，避免「糊脸」的放大感 */
.el-drawer.base-info-drawer .dialogForm {
  max-width: 100%;
  margin: 8px auto 16px;
}

.el-drawer.base-info-drawer .form-section {
  margin-bottom: 22px;
}

.el-drawer.base-info-drawer .section-title {
  font-size: 14px;
  padding-bottom: 10px;
  margin-bottom: 16px;
}

.el-drawer.base-info-drawer .el-form-item {
  margin-bottom: 16px;
}

.el-drawer.base-info-drawer .el-input__wrapper,
.el-drawer.base-info-drawer .el-select .el-input__wrapper,
.el-drawer.base-info-drawer .el-textarea__inner {
  border-radius: 8px;
}

/* 设计器模型已是普通单选按钮，无需卡片覆盖；仅保留警告条收一档 */
.el-drawer.base-info-drawer .radio-card-warning {
  margin-top: 6px;
  padding: 5px 9px;
  font-size: 11px;
}

/* 流程配置抽屉：动画规格与节点属性面板（property-drawer-modern）一致，走 el-drawer 默认滑出 */

/* 亮色主题：头部/主体/页脚分界线 */
.el-drawer.base-info-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--wf-border-lighter, #ebeef5);
}

.el-drawer.base-info-drawer .el-drawer__body {
  padding: 16px 20px;
  overflow-y: auto;
}

.el-drawer.base-info-drawer .el-drawer__footer {
  padding: 12px 20px;
  border-top: 1px solid var(--wf-border-lighter, #ebeef5);
}

.base-info-drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.base-info-drawer-footer-hint {
  font-size: 12px;
  color: var(--wf-text-secondary, #909399);
}

.base-info-drawer-footer-actions {
  display: inline-flex;
  gap: 10px;
}

/* 小屏抽屉（100% 宽）空间紧：footer 提示让位，只留按钮 */
@media (max-width: 768px) {
  .base-info-drawer-footer-hint {
    display: none;
  }

  .base-info-drawer-footer {
    justify-content: flex-end;
  }
}

/* ========== 流程配置抽屉（暗黑模式） ========== */
html.dark .el-drawer.base-info-drawer {
  background: var(--wf-bg-white, #1f2937);
}

html.dark .base-info-drawer-title {
  color: var(--wf-text-primary);
}

html.dark .base-info-drawer-subtitle,
html.dark .base-info-drawer-footer-hint {
  color: var(--wf-text-secondary);
}

html.dark .el-drawer.base-info-drawer .el-drawer__header {
  border-bottom-color: var(--wf-border-color, #374151);
}

html.dark .el-drawer.base-info-drawer .el-drawer__footer {
  border-top-color: var(--wf-border-color, #374151);
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
  right: 16px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  padding: 4px;
  background: var(--wf-bg-white, #fff);
  border: 1px solid var(--wf-border-lighter, #ebeef5);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(29, 33, 41, 0.08);
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

/* ========== 响应式适配：平板端 (<= 1024px) ========== */
@media (max-width: 1024px) {
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
    right: 8px;
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
