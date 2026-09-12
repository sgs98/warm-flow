import {GatewayModel} from "./gatewayModel";
import {GatewayView} from "./gatewayView";
import {h} from "@logicflow/core";

class ParallelModel extends GatewayModel {}

class ParallelView extends GatewayView {

  getSvg(x: number, y: number, _width: number, _height: number, _textValue: string, style: { stroke: string }) {
    const color = style.stroke ? style.stroke : '#409eff';
    return h('g', {}, [
      h('line', {
        x1: x - 6, y1: y, x2: x + 6, y2: y,
        stroke: color, 'stroke-width': 1.8, 'stroke-linecap': 'round',
      }),
      h('line', {
        x1: x, y1: y - 6, x2: x, y2: y + 6,
        stroke: color, 'stroke-width': 1.8, 'stroke-linecap': 'round',
      }),
    ]);
  }
}

export default {
  type: 'parallel',
  view: ParallelView,
  model: ParallelModel
};
