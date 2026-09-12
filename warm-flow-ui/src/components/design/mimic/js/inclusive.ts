import {GatewayModel} from "./gatewayModel";
import {GatewayView} from "./gatewayView";
import {h} from "@logicflow/core";

class InclusiveModel extends GatewayModel {}

class InclusiveView extends GatewayView {

  getSvg(x: number, y: number, _width: number, _height: number, _textValue: string, style: { stroke: string }) {
    const color = style.stroke ? style.stroke : '#409eff';
    return h('circle', {
      cx: x,
      cy: y,
      r: 5,
      fill: 'none',
      stroke: color,
      'stroke-width': 1.8,
    });
  }
}

export default {
  type: 'inclusive',
  view: InclusiveView,
  model: InclusiveModel
};
