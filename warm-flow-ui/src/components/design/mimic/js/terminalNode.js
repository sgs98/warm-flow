import {HtmlNode, HtmlNodeModel} from "@logicflow/core";
import {setCommonStyle} from "@/components/design/common/js/tool.js";
import {createApp, h} from 'vue';
import terminalNode from '../vue/terminalNode.vue';

function createTerminal(type, variant, defaultText) {
  class TerminalModel extends HtmlNodeModel {
    setAttributes() {
      this.width = 76;
      this.height = 40;
      this.radius = 20;
      this.properties.width = 76;
      this.properties.height = 40;
    }

    getNodeStyle() {
      const style = setCommonStyle(super.getNodeStyle(), this.properties, "node", "mimic");
      style.fill = 'transparent';
      style.stroke = 'transparent';
      return style;
    }

    getTextStyle() {
      const style = super.getTextStyle();
      style.display = 'none';
      return style;
    }
  }

  class TerminalView extends HtmlNode {
    constructor(props) {
      super(props)
      this.isMounted = false
      const nodeStyle = props.model.getNodeStyle()
      this.r = h(terminalNode, {
        variant,
        text: props.model.text.value || defaultText,
        chartStatusColor: props.model.properties.chartStatusColor,
        status: props.model.properties.status,
        stroke: nodeStyle._statusHex || nodeStyle.stroke,
        onEditNode: () => {
          props.graphModel.eventCenter.emit("edit:node", {id: props.model.id, click: true});
        }
      })
      this.app = createApp({
        render: () => this.r
      })
    }

    shouldUpdate() {
      if (this.props.model.text.value !== this.preText) {
        this.preText = this.props.model.text.value
        return true
      }
      return false
    }

    setHtml(rootEl) {
      if (!this.isMounted) {
        this.isMounted = true
        const node = document.createElement('div')
        node.style.width = '100%'
        node.style.height = '100%'
        rootEl.appendChild(node)
        this.app.mount(node)
      } else {
        this.r.component.props.text = this.props.model.text.value
      }
    }
  }

  return {
    type,
    model: TerminalModel,
    view: TerminalView,
  };
}

export { createTerminal };
