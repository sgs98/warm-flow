import {RectNode, RectNodeModel, h} from "@logicflow/core";
import {setCommonStyle, applyClassicDesignColor} from "@/components/design/common/js/tool.js";
import {handlerFeedback} from "@/api/flow/definition.js";

class BetweenModel extends RectNodeModel {

  initNodeData(data) {
    super.initNodeData(data);
    this.width = 100;
    this.height = 80;
    this.radius = 8;
    this.fetchHandlerName();
  }

  // 运行态判定：流程图带运行态颜色配置，或节点已带运行状态字段
  isRuntime() {
    const {chartStatusColor, status} = this.properties;
    const hasColor = Array.isArray(chartStatusColor) && chartStatusColor.length > 0;
    return hasColor || (status !== undefined && status !== null && status !== '');
  }

  // 运行态下仅已完成（status=2）节点展示办理人，未完成节点隐藏
  shouldShowHandler() {
    return !this.isRuntime() || Number(this.properties.status) === 2;
  }

  // 办理人名称回显：与仿钉钉卡片共用 handler-feedback 接口，
  // 结果写入 properties 触发视图重绘，卡片副标题展示。
  // 运行态未完成节点不请求、不展示，避免暴露未办理节点的办理人。
  fetchHandlerName() {
    const flag = this.properties.permissionFlag;
    if (!flag || this.properties._handlerName || !this.shouldShowHandler()) {
      return;
    }
    handlerFeedback({storageIds: flag.split("@@")}).then(response => {
      if (response.code === 200 && response.data) {
        this.setProperties({_handlerName: response.data.map(item => item.handlerName).join('、')});
      }
    }).catch(() => {});
  }

  getNodeStyle() {
    const style = setCommonStyle(super.getNodeStyle(), this.properties, "node");
    // 设计态语义色：中间 / 审批节点用品牌蓝
    return applyClassicDesignColor(style, this.properties, '64,158,255');
  }
}

class BetweenView extends RectNode {

  /** 现代化审批/任务图标：柔和色块章 + 语义色符号 */
  getIconShape() {
    const {model} = this.props;
    const {x, y} = model;
    const style = model.getNodeStyle();
    const sc = style._statusColorRGB || '166,178,189';
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    const size = 24;
    const iconX = x - 40; // 左上角
    const iconY = y - 32; // 底边 y-8，避开居中标题的纵向区间

    return h(
        'g',
        {},
        [
          // 柔和色块章（语义色低饱和底）
          h('rect', {
            x: iconX,
            y: iconY,
            width: size,
            height: size,
            rx: 7,
            ry: 7,
            fill: `rgba(${sc}, ${isDark ? 0.16 : 0.1})`,
          }),
          h(
              'svg',
              {
                x: iconX + 3,
                y: iconY + 3,
                width: size - 6,
                height: size - 6,
                viewBox: '0 0 24 24',
              },
              [
                // 用户 + 对勾标识
                h('circle', {
                  cx: 10.2, cy: 9.2, r: 3.4,
                  fill: 'none',
                  stroke: `rgb(${sc})`,
                  strokeWidth: 1.7,
                }),
                h('path', {
                  d: 'M4.6 18.4c1-2.8 3-4.2 5.6-4.2 1 0 1.9.2 2.7.6',
                  fill: 'none',
                  stroke: `rgb(${sc})`,
                  strokeWidth: 1.7,
                  strokeLinecap: 'round',
                }),
                h('path', {
                  d: 'M14.6 15.6l2 2 3.6-4',
                  fill: 'none',
                  stroke: `rgb(${sc})`,
                  strokeWidth: 1.8,
                  strokeLinecap: 'round',
                  strokeLinejoin: 'round',
                }),
              ]
          ),
        ]
    );
  }

  /** 底部办理人副标题（运行态未完成节点不展示） */
  getSubtitleShape() {
    const {model} = this.props;
    const {x, y, height} = model;
    const style = model.getNodeStyle();
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    const name = model.properties._handlerName;
    if (!name || !model.shouldShowHandler()) {
      return null;
    }
    return h('text', {
      x,
      y: y + (height || 80) / 2 - 13,
      fontSize: 10,
      fill: isDark ? '#a3a6ad' : '#86909c',
      style: {
        userSelect: 'none',
        textAnchor: 'middle',
        dominantBaseline: 'middle',
      },
    }, name.length > 12 ? name.slice(0, 12) + '…' : name);
  }

  // 自定义节点外观
  getShape() {
    const { model } = this.props;
    const { x, y, width, height, radius } = model;
    const style = model.getNodeStyle();
    const sc = style._statusColorRGB || '166,178,189';
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');

    return h('g', {style: {cursor: 'pointer'}}, [
      // 定义渐变和滤镜
      h('defs', {}, [
        // 卡片背景填充（暗黑模式适配）
        h('linearGradient', { id: `card-bg-${model.id}`, x1: '0%', y1: '0%', x2: '0%', y2: '100%' }, [
          h('stop', { offset: '0%', stopColor: isDark ? `rgba(${sc}, 0.06)` : '#ffffff' }),
          h('stop', { offset: '100%', stopColor: isDark ? `rgba(${sc}, 0.03)` : '#f8fafc' }),
        ]),
        // 卡片阴影（暗黑模式加深）
        h('filter', { id: `card-shadow-${model.id}`, x: '-20%', y: '-20%', width: '140%', height: '140%' }, [
          h('feDropShadow', { dx: 0, dy: 3, stdDeviation: 6, floodColor: '#000', floodOpacity: isDark ? 0.4 : 0.05 }),
          h('feDropShadow', { dx: 0, dy: 1, stdDeviation: 2, floodColor: '#000', floodOpacity: isDark ? 0.2 : 0.025 }),
        ]),
        // 顶部光泽渐变（暗黑模式减弱）
        h('linearGradient', { id: `card-top-${model.id}`, x1: '0%', y1: '0%', x2: '100%', y2: '0%' }, [
          h('stop', { offset: '0%', stopColor: `rgba(${sc}, ${isDark ? 0.06 : 0.12})` }),
          h('stop', { offset: '50%', stopColor: `rgba(${sc}, ${isDark ? 0.02 : 0.03})` }),
          h('stop', { offset: '100%', stopColor: `rgba(255,255,255,0)` }),
        ]),
      ]),

      // 主卡片矩形（暗黑模式使用深色渐变填充）
      h('rect', {
        x: x - width / 2,
        y: y - height / 2,
        rx: radius,
        ry: radius,
        width,
        height,
        fill: isDark ? `url(#card-bg-${model.id})` : style.fill,
        stroke: style.stroke,
        strokeWidth: style.strokeWidth || 1.5,
        strokeLinejoin: 'round',
        filter: `url(#card-shadow-${model.id})`,
      }),

      // 顶部渐变光泽条
      h('rect', {
        x: x - width / 2 + 1,
        y: y - height / 2 + 1,
        rx: radius,
        ry: radius,
        width: width - 2,
        height: height * 0.18,
        fill: `url(#card-top-${model.id})`,
        clipPath: `inset(0 0 ${height * 0.82}px 0 round ${radius}px)`,
      }),


      // 图标
      this.getIconShape(),
      // 办理人副标题
      this.getSubtitleShape(),
    ]);
  }
}

export default {
  type: "between",
  model: BetweenModel,
  view: BetweenView,
};
