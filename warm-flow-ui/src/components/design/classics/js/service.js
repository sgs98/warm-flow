import {RectNode, RectNodeModel, h} from "@logicflow/core";
import {setCommonStyle, applyClassicDesignColor} from "@/components/design/common/js/tool.js";

// 服务任务节点设计态语义色（靛蓝），与开始/审批/网关/结束的语义色区分
const SERVICE_COLOR = '79,70,229';

// 齿轮图标路径（Material settings，viewBox 0 0 24 24），与图标徽章同色
const GEAR_PATH = 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z';

class ServiceModel extends RectNodeModel {

  initNodeData(data) {
    super.initNodeData(data);
    this.width = 100;
    this.height = 80;
    this.radius = 8;
  }

  getNodeStyle() {
    const style = setCommonStyle(super.getNodeStyle(), this.properties, "node");
    // 设计态语义色：服务任务节点用靛蓝
    return applyClassicDesignColor(style, this.properties, SERVICE_COLOR);
  }
}

class ServiceView extends RectNode {

  /** 齿轮图标徽章：与审批节点同构的柔和色块章 + 语义色符号 */
  getIconShape() {
    const {model} = this.props;
    const {x, y} = model;
    const style = model.getNodeStyle();
    const sc = style._statusColorRGB || SERVICE_COLOR;
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    const size = 24;
    const iconX = x - 40;
    const iconY = y - 32;

    return h(
        'g',
        {},
        [
          h('rect', {
            x: iconX,
            y: iconY,
            width: size,
            height: size,
            rx: 7,
            ry: 7,
            fill: `rgba(${sc}, ${isDark ? 0.16 : 0.1})`,
          }),
          h('path', {
            transform: `translate(${iconX + 3}, ${iconY + 3}) scale(0.75)`,
            d: GEAR_PATH,
            fill: `rgb(${sc})`,
          }),
        ]
    );
  }

  getShape() {
    const { model } = this.props;
    const { x, y, width, height, radius } = model;
    const style = model.getNodeStyle();
    const sc = style._statusColorRGB || SERVICE_COLOR;
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');

    return h('g', {style: {cursor: 'pointer'}}, [
      h('defs', {}, [
        h('linearGradient', { id: `svc-bg-${model.id}`, x1: '0%', y1: '0%', x2: '0%', y2: '100%' }, [
          h('stop', { offset: '0%', stopColor: isDark ? `rgba(${sc}, 0.06)` : '#ffffff' }),
          h('stop', { offset: '100%', stopColor: isDark ? `rgba(${sc}, 0.03)` : '#f8fafc' }),
        ]),
        h('filter', { id: `svc-shadow-${model.id}`, x: '-20%', y: '-20%', width: '140%', height: '140%' }, [
          h('feDropShadow', { dx: 0, dy: 3, stdDeviation: 6, floodColor: '#000', floodOpacity: isDark ? 0.4 : 0.05 }),
          h('feDropShadow', { dx: 0, dy: 1, stdDeviation: 2, floodColor: '#000', floodOpacity: isDark ? 0.2 : 0.025 }),
        ]),
        h('linearGradient', { id: `svc-top-${model.id}`, x1: '0%', y1: '0%', x2: '100%', y2: '0%' }, [
          h('stop', { offset: '0%', stopColor: `rgba(${sc}, ${isDark ? 0.06 : 0.12})` }),
          h('stop', { offset: '50%', stopColor: `rgba(${sc}, ${isDark ? 0.02 : 0.03})` }),
          h('stop', { offset: '100%', stopColor: `rgba(255,255,255,0)` }),
        ]),
      ]),

      h('rect', {
        x: x - width / 2,
        y: y - height / 2,
        rx: radius,
        ry: radius,
        width,
        height,
        fill: isDark ? `url(#svc-bg-${model.id})` : style.fill,
        stroke: style.stroke,
        strokeWidth: style.strokeWidth || 1.5,
        strokeLinejoin: 'round',
        filter: `url(#svc-shadow-${model.id})`,
      }),

      h('rect', {
        x: x - width / 2 + 1,
        y: y - height / 2 + 1,
        rx: radius,
        ry: radius,
        width: width - 2,
        height: height * 0.18,
        fill: `url(#svc-top-${model.id})`,
        clipPath: `inset(0 0 ${height * 0.82}px 0 round ${radius}px)`,
      }),

      this.getIconShape(),
    ]);
  }
}

export default {
  type: "service",
  model: ServiceModel,
  view: ServiceView,
};
