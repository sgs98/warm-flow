import {HtmlNodeModel} from "@logicflow/core";
import {setCommonStyle} from "@/components/design/common/js/tool";

export class BaseNodeModel extends HtmlNodeModel {

  setAttributes() {
    this.width = 260;
    this.height = 76;
    this.radius = 14;
    this.properties.width = 260;
    this.properties.height = 76;
    this.inputData = this.text.value

  }
  getNodeStyle() {
    const style = setCommonStyle(super.getNodeStyle(), this.properties, "node", "mimic");
    // 视觉由 Vue 卡片承担，避免 HtmlNode 再描一层灰框
    style.fill = 'transparent';
    style.stroke = 'transparent';
    return style;
  }

  getData () {
    const data = super.getData()
    data.text.value = this.inputData
    return data
  }

  getTextStyle() {
    const style = super.getTextStyle();
    style.display = 'none';
    return style;
  }

}
