import { HtmlNode } from "@logicflow/core";
import baseNode from '../vue/baseNode.vue'
import { createApp, h } from 'vue';
import {ClickOutside} from "element-plus";

export class BaseNodeView extends HtmlNode {

  constructor(props) {
    super(props)
    this.isMounted = false
    const nodeStyle = props.model.getNodeStyle()
    this.r = h(baseNode, {
      text: props.model.inputData,
      permissionFlag: props.model.properties.permissionFlag,
      collaborativeWay: props.model.properties.collaborativeWay,
      nodeRatio: props.model.properties.nodeRatio,
      selected: props.model.isSelected,
      chartStatusColor: props.model.properties.chartStatusColor,
      status: props.model.properties.status,
      type: props.model.type,
      fill: nodeStyle.fill,
      stroke: nodeStyle._statusHex || nodeStyle.stroke,
      onUpdateNodeName: (nodeName) => {
        props.model.text.value = nodeName
        props.graphModel.eventCenter.emit("update:nodeName", {id: props.model.id, nodeName: nodeName});
      },
      onEditNode: () => {
        props.graphModel.eventCenter.emit("edit:node", {id: props.model.id, click: true});
      },
      onDeleteNode: () => {
        props.graphModel.eventCenter.emit("delete:node", {id: props.model.id});
      }
    })
    this.app = createApp({
      render: () => this.r
    })
    this.app.directive('click-outside', ClickOutside);
  }

  shouldUpdate() {
    const currentProperties = JSON.parse(this.currentProperties)
    if (this.preProperties) {
      const preProperties = JSON.parse(this.preProperties)
      let flag = false
      if (currentProperties.permissionFlag !== preProperties.permissionFlag
          || currentProperties.collaborativeWay !== preProperties.collaborativeWay
          || currentProperties.nodeRatio !== preProperties.nodeRatio) {
        this.preProperties = this.currentProperties;
        flag = true
      }
      if (this.props.model.text.value!== this.preText) {
        this.preText = this.props.model.text.value
        flag = true
      }
      // 选中态变化也要重渲染：卡片显示选中光圈
      if (this.props.model.isSelected !== this.preSelected) {
        this.preSelected = this.props.model.isSelected
        flag = true
      }
      return flag
    }

    this.preProperties = this.currentProperties;
    this.preText = this.props.model.text.value
    this.preSelected = this.props.model.isSelected
    return true;
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
      this.r.component.props.permissionFlag = this.props.model.properties.permissionFlag
      this.r.component.props.collaborativeWay = this.props.model.properties.collaborativeWay
      this.r.component.props.nodeRatio = this.props.model.properties.nodeRatio
      this.r.component.props.selected = this.props.model.isSelected
    }
  }
}

