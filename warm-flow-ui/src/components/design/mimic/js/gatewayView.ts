import { RectNode, h} from '@logicflow/core'

export abstract class GatewayView extends RectNode {

  abstract getSvg(x: number, y: number, width: number, height: number, textValue: string, style: any): any

  // 自定义节点外观：润蓝小菱形，内部符号由子类 getSvg 绘制
  getShape() {
    const { model } = this.props;
    const { x, y } = model;
    const style = model.getNodeStyle();
    const size = 24;
    return h('g', {style: {
        cursor: 'pointer'
      }}, [
      h('rect', {
        x: x - size / 2,
        y: y - size / 2,
        rx: 5,
        ry: 5,
        width: size,
        height: size,
        fill: style.fill || '#ecf5ff',
        stroke: style.stroke || '#409eff',
        'stroke-width': 1.5,
        transform: `rotate(45 ${x} ${y})`,
        style: style.shadow ? { filter: style.shadow } : undefined,
      }),
      this.getSvg(x, y, model.width, model.height, model.text.value, style),
    ]);
  }
}
