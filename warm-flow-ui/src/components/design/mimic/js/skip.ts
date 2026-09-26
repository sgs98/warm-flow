import {setCommonStyle} from "../../common/js/tool";
import {CurvedEdge, CurvedEdgeModel, getCurvedEdgePath} from "@logicflow/extension";
import {h} from "@logicflow/core";

class SkipModel extends CurvedEdgeModel  {

  setAttributes() {
    this.isHitable = false // 细粒度控制边是否对用户操作进行反应
  }

  getEdgeStyle() {
    const style = setCommonStyle(super.getEdgeStyle(), this.properties, "skip", "mimic");
    const inDesigner = typeof window !== 'undefined' && (window as any).__WF_FLOW_DESIGN_MODE__;
    const isRuntime = this.properties.chartStatusColor && this.properties.chartStatusColor.length > 0;
    if (inDesigner && !isRuntime) {
      style.stroke = 'rgba(64, 158, 255, 0.55)';
      style.strokeWidth = 2;
    }
    return style;
  }

  // getTextPosition() {
  //   const position = super.getTextPosition();
  //
  //   const currentPositionList = this.points.split(' ');
  //
  //   // 取最后两个点，用于判断终点方向
  //   const lastTwoPoints = currentPositionList.slice(-2);
  //   const [x2, y2] = lastTwoPoints[1].split(',').map(Number);
  //
  //   // 设置文本位置
  //   position.x = x2;
  //   position.y = y2 - 30;
  //
  //   return position;
  // }

  getTextStyle() {
    const style = super.getTextStyle();
    style.display = 'none';
    style.background = {fill: "transparent"};
    return style;
  }

  /**
   * 重写此方法，使保存数据是能带上锚点数据。
   */
  getData() {
    const data = super.getData();
    data.sourceAnchorId = this.sourceAnchorId;
    data.targetAnchorId = this.targetAnchorId;
    return data;
  }

}

class SkipView extends CurvedEdge {

  getEdge(): h.JSX.Element {
    const { model } = this.props;
    const { points: pointsStr, isAnimation, arrowConfig, radius = 0 } = model;
    const style = model.getEdgeStyle();
    const animationStyle = model.getEdgeAnimationStyle();
    // 跳转条件图标底色：设计态用主蓝 chroma，运行态保留状态语义色（chartStatusColor）
    const condColor = model.properties.chartStatusColor ? style.stroke : '#409eff';
    let points = this.pointFilter(pointsStr.split(' ').map((p) => p.split(',').map((a) => +a)));

    // 主路径
    let mainPath = getCurvedEdgePath(points, radius as number);
    let plusElements: h.JSX.Element[] = [];

    const offsetY = 30

    // 跳转线是直线
    if (points.length === 2) {
      let nextEdge = model.graphModel.edges.filter(edge => edge.sourceNodeId ===  model.sourceNode.id);
      // 如果上一个节点是互斥网关，并且网关后节点大于1个，也就是说是互斥网关结束节点时
      if (['serial', 'inclusive'].includes(model.sourceNode.type as string) && nextEdge.length > 1) {
        const midPoint = [points[0][0], points[0][1] + offsetY - 10];
        plusElements = this.getForeignObject(midPoint, condColor, model.text.value);
      } else if (!model.properties.chartStatusColor) {
        const midPoint = [points[0][0], points[0][1] + offsetY];
        plusElements = this.getPlusElements(midPoint);
      }
    } else {
      const p0 = points[0];
      const p1 = points[1];
      const p2 = points[2];

      // 判断是否由竖线变为横线
      if (p0[0] === p1[0] && p0[1] !== p1[1] && !model.properties.chartStatusColor) {
        const midPoint = [p0[0] , p0[1] + offsetY];
        plusElements = this.getPlusElements(midPoint);
      }

      // 判断是否由横线变为竖线，并且是互斥网关
      if (model.sourceNode && ['serial', 'inclusive'].includes(model.sourceNode.type as string) && p0[1] === p1[1] && p0[0] !== p1[0]) {
        const midPoint = [p2[0], p1[1] + offsetY];
        plusElements = this.getForeignObject(midPoint, condColor, model.text.value);
      }
    }

    // 绘制主路径
    let mainPathElement: h.JSX.Element[] = [];
    mainPathElement.push(h('path', {
      d: mainPath,
      style: isAnimation ? animationStyle : {},
      ...style,
      ...arrowConfig,
      fill: 'none',
    }));

    // 返回所有路径元素
    return h('g', {}, [...mainPathElement, ...plusElements]);
  }

  private getForeignObject(midPoint: number[], stroke: string, text: string) {
    const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
    let elements: h.JSX.Element[] = [
      // 条件图标：圆角底 + 白色分支符号，底色随条件语义色
      h('foreignObject', {
        x: midPoint[0] - 16,
        y: midPoint[1] - 20,
        width: 32,
        height: 32,
      }, [
        h('div', {
          style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '100%',
            height: '100%',
            filter: 'drop-shadow(0 2px 6px rgba(29, 33, 41, 0.18))',
          },
          innerHTML: `
<div style="width: 28px; height: 28px; margin: auto; border-radius: 8px; background: ${stroke}; display: flex; align-items: center; justify-content: center;">
  <svg width="16" height="16" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M16 8v4" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
    <path d="M16 12c0 4-6 2.5-6 8.5M16 12c0 4 6 2.5 6 8.5" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" fill="none"/>
    <circle cx="16" cy="7.5" r="2.1" fill="#FFFFFF"/>
    <circle cx="10" cy="23" r="2.1" fill="#FFFFFF"/>
    <circle cx="22" cy="23" r="2.1" fill="#FFFFFF"/>
  </svg>
</div>
`,
        })
      ])
    ];

    // 只有当文本有值时才添加背景矩形和文本
    if (text && text.trim().length > 0) {
      // 由于我们无法直接在当前环境中测量文本，我们使用一个估算方法
      // 通常每个字符大约占用 8-10 像素宽度（取决于字体）
      const charWidth = 8; // 每个字符的估计宽度
      const padding = 16; // 左右内边距
      const minWidth = 44; // 最小宽度
      const textWidth = Math.max(minWidth, text.length * charWidth + padding);

      // 添加背景胶囊：颜色跟随主题，暗黑模式下不再硬编码白底
      elements.push(
          h('rect', {
            x: midPoint[0] - textWidth / 2,
            y: midPoint[1] + 12,
            width: textWidth,
            height: 20,
            fill: isDark ? '#1d1e1f' : '#ffffff',
            stroke: isDark ? 'rgba(255, 255, 255, 0.14)' : 'rgba(64, 158, 255, 0.35)',
            'stroke-width': 1,
            rx: 10, // 圆角
            ry: 10
          })
      );

      // 添加文本
      elements.push(
          h('text', {
            x: midPoint[0],
            y: midPoint[1] + 26,
            fontSize: 12,
            fontWeight: 500,
            fill: isDark ? '#a3a6ad' : '#4e5969',
            style: {
              userSelect: 'none',
              textAnchor: 'middle' // 文本居中对齐
            }
          }, `${text}`)
      );
    }

    return this.getAddElements(midPoint, elements, true);
  }

  private getPlusElements(midPoint: number[]) {
    return this.getAddElements(midPoint, null);
  }

  private getAddElements(midPoint: number[], obj, isCondition: boolean = false) {
    if (!obj) {
      const x = midPoint[0]
      const y = midPoint[1]
      const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
      obj = [
        // 透明大圆：扩大可点击范围，视觉不变
        h('circle', {
          cx: x,
          cy: y,
          r: 16,
          fill: 'transparent',
        }),
        h('circle', {
          cx: x,
          cy: y,
          r: 11,
          fill: isDark ? '#1d1e1f' : '#ffffff',
          stroke: '#409eff',
          'stroke-width': 1.4,
          style: {
            filter: 'drop-shadow(0 2px 6px rgba(64, 158, 255, 0.35))',
          },
        }),
        h('line', {
          x1: x - 5,
          y1: y,
          x2: x + 5,
          y2: y,
          stroke: '#409eff',
          'stroke-width': '2',
          'stroke-linecap': 'round',
        }),
        h('line', {
          x1: x,
          y1: y - 5,
          x2: x,
          y2: y + 5,
          stroke: '#409eff',
          'stroke-width': '2',
          'stroke-linecap': 'round',
        })
      ]
    }
    let plusElements: h.JSX.Element[] = [];
    plusElements.push(
        h('g', {
          ...this.getEventHandlers(isCondition), // 根据 isCondition 动态选择事件处理器
          style: {
            pointerEvents: 'auto',
            cursor: 'pointer'
          }
        }, obj)
    );
    return plusElements;
  }

  // 新增方法：根据 isCondition 返回不同的事件处理器
  private getEventHandlers(isCondition: boolean): any {
    if (isCondition) {
      return {
        onClick: () => {
          this.props.graphModel.eventCenter.emit("show:EdgeSetting", { id: this.props.model.id });
        }
      };
    } else {
      return {
        onMouseEnter: (e) => {
          this.props.graphModel.eventCenter.emit("show:EdgeTooltip", { e: e, id: this.props.model.id });
        },
        onMouseLeave: (e) => {
          setTimeout(() => {
            if (!(window as any).isTooltipHovered) {
              this.props.graphModel.eventCenter.emit("hide:EdgeTooltip", e);
            }
          }, 100);
        }
      };
    }
  }

  private pointFilter(points: number[][]) {
    const all = points
    let i = 1
    while (i < all.length - 1) {
      const [x, y] = all[i - 1]
      const [x1, y1] = all[i]
      const [x2, y2] = all[i + 1]
      if ((x === x1 && x1 === x2) || (y === y1 && y1 === y2)) {
        all.splice(i, 1)
      } else {
        i++
      }
    }
    return all
  }
}

export default {
  type: "skip",
  view: SkipView,
  model: SkipModel,
};
