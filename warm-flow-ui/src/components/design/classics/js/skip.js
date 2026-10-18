import { CurvedEdge, CurvedEdgeModel } from "@logicflow/extension";
import { setCommonStyle } from "@/components/design/common/js/tool.js";

class SkipModel extends CurvedEdgeModel {
  setAttributes() {
    this.offset = 20;
    // 圆角折线：拐角处做弧形过渡（CurvedEdgeModel 继承自 PolylineEdgeModel，
    // pointsList / offset / 文本定位与旧 Polyline 数据完全兼容）
    this.radius = 10;
    // 注意：不要整体覆盖 this.arrowConfig —— LogicFlow 预置的 markerEnd/markerStart
    // 引用就在里面，覆盖后边路径丢失 marker-end，箭头将不再渲染
  }

  getEdgeStyle() {
    const style = setCommonStyle(super.getEdgeStyle(), this.properties, "skip");
    const inDesigner = typeof window !== 'undefined' && window.__WF_FLOW_DESIGN_MODE__;
    const isRuntime = this.properties.chartStatusColor && this.properties.chartStatusColor.length > 0;
    // 设计态用品牌蓝细线；运行态保留状态语义色（与仿钉钉模式一致）
    if (inDesigner && !isRuntime) {
      style.stroke = 'rgba(64, 158, 255, 0.55)';
      style.strokeWidth = 2;
    }
    return style;
  }

  getArrowStyle() {
    const style = super.getArrowStyle();
    const inDesigner = typeof window !== 'undefined' && window.__WF_FLOW_DESIGN_MODE__;
    const isRuntime = this.properties.chartStatusColor && this.properties.chartStatusColor.length > 0;
    // 实心大箭头（对齐钉钉风格）：默认 10×5 太小，放大到 16×8，颜色跟随连线
    style.offset = 16;
    style.verticalLength = 8;
    if (inDesigner && !isRuntime) {
      // 设计态箭头用实色品牌蓝：跟随连线的半透明样式会让小箭头几乎不可见
      style.stroke = '#409eff';
      style.fill = '#409eff';
    }
    return style;
  }

  getTextStyle() {
    const style = super.getTextStyle();
    // 动态适配亮/暗模式的文本标签背景色
    const isDark = document.documentElement.classList.contains('dark');
    style.background = { fill: isDark ? '#1e1e1e' : '#ffffff' };
    style.fill = isDark ? '#cbd5e1' : '#374151';
    style.fontSize = 12;
    style.fontWeight = 400;
    // 胶囊式圆角
    if (!style.style) {
      style.style = {};
    }
    style.style.padding = '2px 8px';
    return style;
  }

  /**
   * 重写此方法，使保存数据是能带上锚点数据。
   */
  getData() {
    const data = super.getData();
    data.sourceAnchorId = this.sourceAnchorId;
    data.targetAnchorId = this.targetAnchorId;
    return data;
  }

}

export default {
  type: "skip",
  view: CurvedEdge,
  model: SkipModel,
};
