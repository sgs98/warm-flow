import {GatewayModel} from "./gatewayModel";
import {GatewayView} from "./gatewayView";
import {h} from "@logicflow/core";

class SerialModel extends GatewayModel {}

class SerialView extends GatewayView {

  getSvg(x: number, y: number, _width: number, _height: number, _textValue: string, style: { stroke: string }) {
    const color = style.stroke ? style.stroke : '#409eff';
    return h('g', {}, [
      h('line', {
        x1: x - 5, y1: y - 5, x2: x + 5, y2: y + 5,
        stroke: color, 'stroke-width': 1.8, 'stroke-linecap': 'round',
      }),
      h('line', {
        x1: x + 5, y1: y - 5, x2: x - 5, y2: y + 5,
        stroke: color, 'stroke-width': 1.8, 'stroke-linecap': 'round',
      }),
    ]);
  }
}

export default {
  type: 'serial',
  view: SerialView,
  model: SerialModel
};
