import { RectNode, h} from '@logicflow/core'

export abstract class GatewayView extends RectNode {

  abstract getSvg(x: number, y: number, width: number, height: number, textValue: string, style: any): any

  // 自定义节点外观：润蓝小菱形，内部符号由子类 getSvg 绘制
  getShape() {
    const { model } = this.props;
    const { x, y } = model;
    const style = model.getNodeStyle();
    const size = 24;
    // 渐变 id 需全局唯一，否则多个网关会共用同一份渐变定义
    const gradientId = `mimic-gateway-gradient-${model.id}`;
    const stroke = style.stroke || '#409eff';
    // 渐变两端颜色跟随主题：暗黑下用半透明蓝做玻璃质感，亮色下用淡蓝渐变
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    const gradientStart = isDark ? 'rgba(64, 158, 255, 0.30)' : '#dcecff';
    const gradientEnd = style.fill || '#ecf5ff';
    return h('g', {style: {
        cursor: 'pointer'
      }}, [
      h('defs', {}, [
        h('linearGradient', {
          id: gradientId,
          x1: '0%', y1: '0%', x2: '100%', y2: '100%',
        }, [
          h('stop', { offset: '0%', 'stop-color': gradientStart }),
          h('stop', { offset: '100%', 'stop-color': gradientEnd }),
        ]),
      ]),
      h('rect', {
        x: x - size / 2,
        y: y - size / 2,
        rx: 5,
        ry: 5,
        width: size,
        height: size,
        fill: `url(#${gradientId})`,
        stroke: stroke,
        'stroke-width': 1.2,
        transform: `rotate(45 ${x} ${y})`,
        style: {
          filter: 'drop-shadow(0 2px 4px rgba(64, 158, 255, 0.28))',
        },
      }),
      this.getSvg(x, y, model.width, model.height, model.text.value, style),
    ]);
  }
}
