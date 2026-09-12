import { RectNodeModel } from '@logicflow/core'
import {setCommonStyle} from "../../common/js/tool";
import {lightColors} from "@/config/themeConfig";

function isMimicRuntime(properties) {
  return properties && properties.chartStatusColor && properties.chartStatusColor.length > 0;
}

export class GatewayModel extends RectNodeModel {

  initNodeData(data) {
    super.initNodeData(data);
    this.width = 36;
    this.height = 36;
    this.radius = 6;
    this.properties.width = 36;
    this.properties.height = 36;
  }
  getNodeStyle() {
    const style = setCommonStyle(super.getNodeStyle(), this.properties, "node", "mimic");
    const inDesigner = typeof window !== 'undefined' && (window as any).__WF_FLOW_DESIGN_MODE__;
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    if (inDesigner && !isMimicRuntime(this.properties)) {
      style.fill = isDark ? 'rgba(64, 158, 255, 0.16)' : (lightColors.primaryLight || '#ecf5ff');
      style.stroke = lightColors.primary || '#409eff';
      style._statusHex = style.stroke;
    } else if (!style.fill) {
      style.fill = isDark ? 'rgba(255, 255, 255, 0.06)' : '#ffffff';
    }
    return style;
  }

  getTextStyle() {
    const style = super.getTextStyle();
    style.display = 'none';
    return style;
  }
}
