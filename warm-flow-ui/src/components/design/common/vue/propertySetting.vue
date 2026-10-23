<template>
  <div class="property-panel" ref="panelRef">
    <div class="property-panel-header">
      <div class="property-panel-title">{{ title }}</div>
      <div class="property-panel-subtitle">{{ subtitle }}</div>
    </div>
    <div class="property-panel-body">
      <component :ref="componentType.name" :is="componentType" v-model="form" :disabled="disabled" :skipConditionShow="skipConditionShow"
                 :nodes="nodes" :skips="skips" :form-path-list="formPathList" @edit-skip="emit('edit-skip', $event)">
        <template v-slot:[key]="data" v-for="(item, key) in $slots">
          <slot :name="key" v-bind="data || {}"></slot>
        </template>
      </component>
    </div>
  </div>
</template>

<script setup name="PropertySetting">
import start from '@/components/design/common/vue/start.vue'
import between from '@/components/design/common/vue/between.vue'
import serial from '@/components/design/common/vue/gateway.vue'
import parallel from '@/components/design/common/vue/gateway.vue'
import inclusive from '@/components/design/common/vue/gateway.vue'
import end from '@/components/design/common/vue/end.vue'
import skip from '@/components/design/common/vue/skip.vue'
import service from '@/components/design/common/vue/serviceTask.vue'
import BaseInfo from "@/components/design/common/vue/baseInfo.vue";

const { proxy } = getCurrentInstance();

// 网关面板里点某条出线的「编辑」时向上冒泡：由设计器把属性面板切到该边的属性
const emit = defineEmits(['edit-skip']);

const COMPONENT_LIST = {
  start,
  between,
  service,
  serial,
  parallel,
  inclusive,
  end,
  skip
}

const props = defineProps({
  value: {
    type: Object,
    default () {
      return {}
    }
  },
  node: {
    type: Object,
    default () {
      return {}
    }
  },
  lf: {
    type: Object,
    default () {
      return null
    }
  },
  disabled: { // 是否禁止
    type: Boolean,
    default: false
  },
  skipConditionShow: { // 是否显示跳转条件
    type: Boolean,
    default: true
  },
  nodes: {
    type: Array,
    default () {
      return []
    }
  },
  skips: {
    type: Array,
    default () {
      return []
    }
  },
  formPathList: {
      type: Array,
      default () {
          return []
      }
  },
});

const form = ref({});
const objId = ref(undefined);
const nodeCode = ref(null);
// 面板根节点：校验失败时用来滚动到第一个红框项
const panelRef = ref(null);
const title = computed(() => {
  let base;
  if (props.node && props.node.type === 'skip') {
    base = '设置边属性'
  } else if (props.node && props.node.type === 'serial') {
    base = '设置串行网关属性'
  } else if (props.node && props.node.type === 'parallel') {
    base = '设置并行网关属性'
  } else if (props.node && props.node.type === 'inclusive') {
      base = '设置包容网关属性'
  }  else if (props.node && props.node.type === 'start') {
    base = '设置开始属性'
  } else if (props.node && props.node.type === 'end') {
    base = '设置结束属性'
  } else if (props.node && props.node.type === 'service') {
    base = '设置服务任务属性'
  } else if (props.node && props.node.type === 'between' && props.node.properties?.eventType) {
    base = '设置事件属性'
  } else {
    base = '设置中间属性'
  }
  // 标题带上当前节点名：改名字不用展开「基础信息」也能确认在改哪个节点；随输入实时更新
  const nodeName = form.value?.nodeName;
  return nodeName ? `${base} · ${nodeName}` : base;
});

// 副标题：常驻侧栏需要一行「正在编辑什么」的定位信息（节点编码 / 连线）
const subtitle = computed(() => {
  if (props.node?.type === 'skip') {
    return '连线属性';
  }
  const code = form.value?.nodeCode;
  return code ? `节点编码：${code}` : '节点属性';
});

// 解析服务任务节点配置（nodeConfig 为 JSON 字符串，结构见后端 ServiceTaskConfig）
function parseServiceTaskConfig(nodeConfig) {
  if (!nodeConfig) {
    return {}
  }
  try {
    const parsed = JSON.parse(nodeConfig)
    return (parsed && parsed.serviceTask) || {}
  } catch (e) {
    return {}
  }
}

// 解析节点配置（nodeConfig 为 JSON 字符串，容错转对象）：顶层键包含引擎配置、自定义扩展属性（KV）与说明元数据
function parseNodeConfig(nodeConfig) {
  if (!nodeConfig) {
    return {}
  }
  if (typeof nodeConfig === 'object') {
    return nodeConfig
  }
  try {
    const parsed = JSON.parse(nodeConfig)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (e) {
    return {}
  }
}

// 组件类型
const componentType = computed(() => {
  if (!props.node || !props.node.type) return
  return COMPONENT_LIST[props.node.type]
})


// 各面板子组件在 setup 里是 `const form = ref(props.modelValue)`，拿到的是「当前对象」的引用，
// 所以切换节点 / 连线时不能整对象替换 form（否则子组件还指着上一个元素的 form，
// 出现「标题变了、表单没变」的错位）。这里原地改写同一个对象：删掉旧键、覆盖新键。
function replaceForm(next) {
  const target = form.value;
  Object.keys(target).forEach((key) => {
    if (!(key in next)) delete target[key];
  });
  Object.assign(target, next);
}

// 面板按需挂载（选中元素才创建），所以必须 immediate：
// 首次创建时 props.node 已经是当前元素，不会再有「从 null 变成节点」的变化可监听
watch(() => props.node, n => {
  if (n) {
    objId.value = n.id
    if (n.type === 'skip') {
      let skipCondition = n.properties.skipCondition
      let condition, conditionType, conditionValue = ''
      if (skipCondition) {
        let conditionSpl = skipCondition.split('@@')
        if (skipCondition && (/^spel/.test(skipCondition) || /^default/.test(skipCondition))) {
          conditionType = conditionSpl && conditionSpl.length > 0 ? conditionSpl[0] : ''
          conditionValue = conditionSpl && conditionSpl.length > 1 ? conditionSpl[1] : ''
        } else if (skipCondition) {
          conditionType = conditionSpl && conditionSpl.length > 0 ? conditionSpl[0] : ''
          let conditionOneSpl = conditionSpl[1].split("|")
          condition = conditionOneSpl && conditionOneSpl.length > 0 ? conditionOneSpl[0] : ''
          conditionValue = conditionOneSpl && conditionOneSpl.length > 1 ? conditionOneSpl[1] : ''
        }
      }

      replaceForm({
        nodeType: n.type,
        skipType: n.properties.skipType,
        skipName: n.text instanceof Object ? n.text.value : n.text,
        skipCondition: skipCondition,
        condition: condition,
        conditionType: conditionType,
        conditionValue: conditionValue
      })
    } else if (n.type === 'service') {
      // 服务任务节点：仅暴露服务配置与监听器，不参与协作方式 / 节点比率 / 自定义表单推导
      const serviceTask = parseServiceTaskConfig(n.properties.nodeConfig);
      let listenerTypes = n.properties.listenerType ? n.properties.listenerType.split(",") : [];
      let listenerPaths = n.properties.listenerPath ? n.properties.listenerPath.split("@@") : [];
      // 无监听器时给空数组（不留占位空行：空行会被行内必填规则判为错误，离开校验永远过不去）
      n.properties.listenerRows = listenerTypes && listenerTypes.length > 0 ? listenerTypes.map((type, index) => ({
        listenerType: type,
        listenerPath: listenerPaths[index]
      })) : [];
      replaceForm({
        nodeType: n.type,
        nodeCode: n.id,
        ...n.properties,
        nodeName: n.text instanceof Object ? n.text.value : n.text,
        serviceType: serviceTask.type || 'class',
        serviceHandler: serviceTask.handler || '',
        serviceExpression: serviceTask.expression || '',
        resultVariable: serviceTask.resultVariable || '',
        // 节点配置的其余段（含默认变量）由 nodeVariables 等面板直接读写，服务配置 watch 会合并写回
        nodeConfig: parseNodeConfig(n.properties.nodeConfig)
      })
    } else {
      let nodeRatio = n.properties.nodeRatio || "0";
      if (!n.properties.collaborativeWay) {
        n.properties.collaborativeWay = parseFloat(nodeRatio) === 0 ? "1" : parseFloat(nodeRatio) === 100 ?
            "3" : nodeRatio ? "2" : "1";
      }
      if (n.properties.collaborativeWay === "2" && !n.properties.nodeRatio) n.properties.nodeRatio = "50";

      let nodeRatioType = 'passRatio', nodeRatioValue = ''
      if (nodeRatio) {
          if (/^passCount|rejectCount/.test(nodeRatio)) {
              const [type, value] = nodeRatio.split('=');
              nodeRatioType = type;
              nodeRatioValue = value;
          } else if (/^spel|default/.test(nodeRatio)) {
              const [type, value] = nodeRatio.split('@@');
              nodeRatioType = type;
              nodeRatioValue = value;
          } else {
              // 默认情况：直接使用整个字符串
              nodeRatioValue = nodeRatio;
          }
      }

      n.properties.formCustom = JSON.stringify(n.properties) === "{}" ? "N" : (n.properties.formCustom ?
          n.properties.formCustom : props.formPathList && props.formPathList.length > 0 ? "Y" :"N");
      let listenerTypes = n.properties.listenerType ? n.properties.listenerType.split(",") : [];
      let listenerPaths = n.properties.listenerPath ? n.properties.listenerPath.split("@@") : [];
      // 无监听器时给空数组（不留占位空行：空行会被行内必填规则判为错误，离开校验永远过不去）
      n.properties.listenerRows = listenerTypes && listenerTypes.length > 0 ? listenerTypes.map((type, index) => ({
        listenerType: type,
        listenerPath: listenerPaths[index]
      })) : [];
      replaceForm({
        nodeType: n.type,
        nodeCode: n.id,
        ext: n.properties.ext ? n.properties.ext : {},
        ...n.properties,
        nodeName: n.text instanceof Object ? n.text.value : n.text,
        nodeRatioType: nodeRatioType,
        nodeRatioValue: nodeRatioValue,
        nodeConfig: parseNodeConfig(n.properties.nodeConfig)
      })
    }
  }
}, { immediate: true });

watch(() => form.value.nodeCode, (n) => {
  nodeCode.value = n;
});

watch(() => form.value.skipType, (n) => {
  // 监听跳转属性变化并更新
  props.lf.setProperties(objId.value, {
    skipType: n
  })

});

watch(() => form.value.nodeName, (n) => {
  // 更新流程节点上的文本内容
  props.lf.updateText(objId.value, n)
  // 监听节点名称变化并更新
  props.lf.setProperties(objId.value, {
    nodeName: n
  })
});

watch(() => form.value.collaborativeWay, (n) => {
  // 监听节点属性变化并更新
  props.lf.setProperties(objId.value, {
    nodeRatio: n === "1" ? "0" : n === "3" ? "100" : "50"
  })
});

watch(() => form.value.nodeRatio, (n) => {
  // 监听节点属性变化并更新
  props.lf.setProperties(objId.value, {
    nodeRatio: n
  })
});

watch(() => form.value.permissionFlag, (n) => {
  // 监听节点属性变化并更新
  props.lf.setProperties(objId.value, {
    permissionFlag: Array.isArray(n) ? n.filter(e => e).join('@@') : n
  })
}, { deep: true });

watch(() => form.value.anyNodeSkip, (n) => {
  // 监听跳转属性变化并更新
  props.lf.setProperties(objId.value, {
    anyNodeSkip: n
  })
});

// 监听：监听器路类型数组
watch(() => form.value.listenerRows?.map(e => e.listenerType), (n) => {
  // 监听监听器类型变化并更新
  props.lf.setProperties(objId.value, {
    listenerType: Array.isArray(n) ? n.join(",") : n
  })
}, { deep: true });

// 监听：监听器路径数组
watch(() => form.value.listenerRows?.map(e => e.listenerPath), (n) => {
  // 监听监听器类型变化并更新
  props.lf.setProperties(objId.value, {
    listenerPath: Array.isArray(n) ? n.join("@@") : n
  })
}, { deep: true });

watch(() => form.value.formCustom, (n) => {
  props.lf.setProperties(objId.value, {
    formCustom: n || ""
  })
});

watch(() => form.value.formPath, (n) => {
  props.lf.setProperties(objId.value, {
    formPath: n
  })
});

// 服务任务配置：执行方式 / 处理类 / 表达式 / 结果变量变化时序列化回 nodeConfig。
// 未填写必填项时不下发，避免把无效配置写进节点（切换目标前的 validate 会拦截）。
watch(() => [form.value.serviceType, form.value.serviceHandler, form.value.serviceExpression, form.value.resultVariable], () => {
  syncServiceNodeConfig()
})

// 服务任务的默认变量及其说明变化同样要落回节点：只盯这两个段，避免与其它字段写回互相覆盖
watch(() => JSON.stringify({
  variables: form.value.nodeConfig?.variables || {},
  meta: form.value.nodeConfig?.__variableMeta || {}
}), () => {
  syncServiceNodeConfig()
})

/**
 * 序列化服务任务节点配置：serviceTask 段由专用字段拼装，其余段（默认变量等）从面板当前值透传。
 * 未填写必填项时不下发。
 */
function syncServiceNodeConfig() {
  if (props.node?.type !== 'service') {
    return
  }
  const type = form.value.serviceType
  const serviceTask = { type }
  if (type === 'class') {
    if (!String(form.value.serviceHandler || '').trim()) return
    serviceTask.handler = form.value.serviceHandler
  } else {
    if (!String(form.value.serviceExpression || '').trim()) return
    serviceTask.expression = form.value.serviceExpression
    if (type === 'expression' && form.value.resultVariable) {
      serviceTask.resultVariable = form.value.resultVariable
    }
  }
  // 保留面板上的其余段（默认变量 / 自定义扩展属性），避免保存服务配置时被整体覆盖
  const existing = parseNodeConfig(form.value.nodeConfig)
  const { serviceTask: _ignored, ...extConfig } = existing
  props.lf.setProperties(objId.value, {
    nodeConfig: JSON.stringify({ ...extConfig, serviceTask })
  })
}

watch(() => form.value.skipName, (n) => {
  if (['skip'].includes(props.node.type)) {
    // 监听跳转名称变化并更新
    props.lf.updateText(objId.value, n)
    // 监听跳转属性变化并更新
    props.lf.setProperties(objId.value, {
      skipName: n
    })
  }
});

watch(() => form.value.skipCondition, (n) => {
  // 监听跳转属性变化并更新
  props.lf.setProperties(objId.value, {
    skipCondition: n
  })

});

watch(() => form.value.ext, (n) => {
  // 监听节点属性变化并更新
  props.lf.setProperties(objId.value, {
    ext: n
  })
}, { deep: true });

// 自定义扩展属性（nodeConfig 顶层 KV）：编辑时序列化回画布节点，保存/导出由 tool.js 透传
watch(() => form.value.nodeConfig, (n) => {
  // 服务任务节点的 nodeConfig 由服务配置 watch 统一序列化，此处跳过避免互相覆盖
  if (props.node?.type === 'service') {
    return
  }
  props.lf.setProperties(objId.value, {
    nodeConfig: Object.keys(n || {}).length ? JSON.stringify(n) : ''
  })
}, { deep: true });

// 事件节点字段：事件类型 / 消息名称 / 附着节点 / 中断开关写回画布节点属性。
// 固定开始 / 结束节点不承载事件，事件节点统一使用中间节点（between）承载，故仅对 between 生效。
watch(() => [form.value.eventType, form.value.eventName, form.value.attachedToNodeCode, form.value.interrupting], () => {
  if (props.node?.type !== 'between') {
    return
  }
  props.lf.setProperties(objId.value, {
    eventType: form.value.eventType || null,
    eventName: form.value.eventName || null,
    attachedToNodeCode: form.value.attachedToNodeCode || null,
    interrupting: form.value.interrupting === undefined || form.value.interrupting === ''
      ? null : form.value.interrupting
  })
})

/**
 * 提交节点 / 连线编码变更：面板里改过编码时，把画布上的元素 id 换掉。
 * 常驻面板没有「关闭」这个时机，改在切换目标前与保存前调用。
 */
function commitIdChange () {
  if (!nodeCode.value || !objId.value || nodeCode.value === objId.value) return;
  if (props.node?.type === 'skip') {
    if (!props.lf.getEdgeModelById(nodeCode.value)) {
      props.lf.changeEdgeId(objId.value, nodeCode.value);
      objId.value = nodeCode.value;
    }
  } else if (!props.lf.getNodeModelById(nodeCode.value)) {
    props.lf.changeNodeId(objId.value, nodeCode.value);
    objId.value = nodeCode.value;
  }
}

/**
 * 切换目标前的离开校验：校验通过才提交编码变更并返回 true；
 * 不通过就留在当前面板（给一句明确反馈并滚到第一个红框项，避免「点了没反应」）。
 */
async function confirmLeave () {
  if (props.disabled) {
    commitIdChange();
    return true;
  }
  const vm = proxy.$refs[componentType.value?.name];
  if (typeof vm?.validate === 'function') {
    let ok = true;
    try {
      // 各面板 validate 约定不一致：有的 resolve(false) 表示失败，有的直接 reject
      ok = (await vm.validate()) !== false;
    } catch (e) {
      ok = false;
    }
    if (!ok) {
      proxy.$modal.msgWarning('有必填项未填写或格式不正确，请先完善标红项');
      proxy.$nextTick(() => scrollToFirstError());
      return false;
    }
  }
  commitIdChange();
  return true;
}

/** 滚动到面板内第一个校验失败的字段（错误项可能落在可视区之外） */
function scrollToFirstError () {
  const el = panelRef.value?.querySelector?.('.el-form-item.is-error');
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
}

defineExpose({
  confirmLeave,
  commitIdChange,
})
</script>

<style scoped lang="scss">
/* 常驻面板：标题区固定、正文区滚动（原先由 el-drawer 的 header/body 承担） */
.property-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.property-panel-body::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.property-panel-body::-webkit-scrollbar-thumb {
  background: rgba(144, 147, 153, 0.3);
  border-radius: 3px;
}

.property-panel-body::-webkit-scrollbar-thumb:hover {
  background: rgba(144, 147, 153, 0.5);
}
</style>

<!-- 全局样式：面板内控件 / 表格 / 下拉弹层（弹层 teleport 到 body，scoped 命不中） -->
<style lang="scss">
/* ========== 常驻属性面板 ========== */
.property-panel {
  /* 与基础信息页统一：系统字体栈 + 字形平滑 */
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;

  /* 面板头部：标题 + 定位信息（节点编码 / 连线），下面一条分隔线 */
  .property-panel-header {
    flex: none;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    min-height: 64px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--wf-border-lighter, #ebeef5);
  }

  .property-panel-title {
    font-size: 16px;
    font-weight: 600;
    line-height: 1.4;
    color: var(--wf-text-primary, #303133);
  }

  .property-panel-subtitle {
    font-size: 12px;
    line-height: 1.5;
    color: var(--wf-text-secondary, #909399);
  }

  /* 正文上内边距收紧，让 tab 紧跟头部 */
  .property-panel-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    /* 侧栏只有 360px：表格 / 长下拉允许横向滚动，不撑破面板 */
    overflow-x: auto;
    padding: 12px 16px 16px;
  }
  /* 含 Tab 的面板（开始/中间节点）：tab 栏紧贴面板标题，去掉上方间距，对齐更自然 */
  .property-panel-body:has(.modern-tabs-wrapper) {
    padding-top: 0;
  }

  /* 表单标签：与基础信息页统一（加粗；右对齐由 EP 默认 label-position 提供） */
  .el-form-item__label {
    font-weight: 600;
    color: var(--wf-text-primary, #303133);
  }

  .modern-tabs-wrapper {
    margin: 0 -2px 12px;
    overflow-x: auto;
    scrollbar-width: none;
    &::-webkit-scrollbar { display: none; }
  }

  .modern-tabs {
    min-width: max-content;
    gap: 4px !important;
  }

  .modern-tab-item {
    min-height: 40px !important;
    padding: 8px 12px !important;
    gap: 6px !important;
    font-size: 13px !important;
    white-space: nowrap;
  }

  /* 白底描边输入框：与基础信息表单统一（白底 + 浅边框 + 柔和 focus 光环） */
  .el-input__wrapper,
  .el-textarea__inner {
    border-radius: 10px;
    background-color: var(--wf-bg-white, #fff);
    box-shadow: none;
    border: 1px solid var(--wf-border-light, #dcdfe6);
    transition: background-color .2s ease, box-shadow .2s ease, border-color .2s ease;
    &:hover { border-color: var(--wf-primary, #409eff); }
    &.is-focus,
    &:focus {
      background-color: var(--wf-bg-white, #fff);
      border-color: var(--wf-primary, #409eff);
      box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.12);
    }
  }

  /* 校验失败态：红边 + 红光环，压过 focus 蓝色（与基础信息一致） */
  .el-form-item.is-error .el-input__wrapper,
  .el-form-item.is-error .el-input__wrapper.is-focus {
    border-color: var(--wf-danger, #f56c6c);
    box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.12);
  }

  /* 表格：与基础信息页统一（圆角边框 + 中性表头 + hover 蓝条 + 删除按钮红渐变） */
  .el-table {
    border-radius: var(--wf-radius-lg, 12px);
    overflow: hidden;
    border: 1px solid var(--wf-border-lighter, #ebeef5);

    .el-table__header-wrapper th {
      background: var(--wf-bg-white, #fff) !important;
      color: var(--wf-text-regular, #606266);
      font-weight: 600;
      font-size: 13px;
    }
    .el-table__row {
      transition: background-color 0.2s ease;
      & > td:first-child { position: relative; }
      & > td:first-child::before {
        content: '';
        position: absolute; left: 0; top: 50%; transform: translateY(-50%);
        width: 3px; height: 0;
        background: var(--wf-primary, #409eff);
        border-radius: 0 2px 2px 0;
        transition: height 0.25s ease;
      }
      &:hover > td:first-child::before { height: 60%; }
      &:hover > td { background-color: var(--wf-primary-light, #ecf5ff) !important; }
    }
    /* 删除按钮：无实底，仅红色图标，hover 浅红圆底 */
    .el-button--danger.is-link {
      color: var(--wf-danger, #f56c6c);
      padding: 5px;
      height: auto;
      border: none;
      transition: all 0.2s ease;
      &:hover {
        color: #e04848;
        background: rgba(245, 108, 108, 0.12) !important;
        border-radius: 8px;
      }
    }

    /* 表格内控件默认透明：整行读作干净白底，hover 轻填充、focus 才显白底+蓝环。
       避免「全是下拉/输入的行」被填充底染灰、与「含纯文本列的行」深浅不一致 */
    .el-input__wrapper,
    .el-select .el-input__wrapper {
      background-color: transparent;
      &:hover { background-color: rgba(118, 128, 150, 0.07); }
      &.is-focus { background-color: var(--wf-bg-white, #fff); }
    }
  }
}

/* ========== 下拉/树选择弹层：teleport 到 body，需全局统一圆角与投影 ========== */
.el-select__popper.el-popper,
.el-select-dropdown,
.el-tree-select__popper.el-popper {
  border-radius: 10px;
}
.el-select__popper .el-select-dropdown__item,
.el-tree-select__popper .el-tree-node__content {
  border-radius: 6px;
  margin: 0 4px;
  transition: background-color 0.15s ease;
}
.el-select__popper.el-popper,
.el-tree-select__popper.el-popper {
  .el-popper__arrow::before {
    border-radius: 2px;
  }
}

/* ========== 暗黑模式：常驻属性面板完整适配（全局生效） ========== */
html.dark {
  .property-panel {
    .property-panel-header {
      border-bottom-color: var(--wf-border-color, #2a2a2a) !important;
    }

    .property-panel-title {
      color: var(--wf-text-primary, #e0e0e0) !important;
    }

    .property-panel-subtitle {
      color: var(--wf-text-secondary, #888888) !important;
    }

    /* iOS 填充式输入框：暗黑覆盖 */
    .el-input__wrapper,
    .el-textarea__inner {
      background-color: rgba(255, 255, 255, 0.06) !important;
      box-shadow: none !important;
      border: 1px solid transparent !important;
    }
    .el-input__wrapper.is-focus {
      border-color: var(--wf-primary, #409eff) !important;
      box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.18) !important;
    }

    /* 表格暗黑：中性表头 + 蓝调 hover */
    .el-table {
      border-color: var(--wf-border-color, #333333);
      .el-table__header-wrapper th {
        background: rgba(255, 255, 255, 0.04) !important;
        color: var(--wf-text-regular, #b0b0b0);
      }
      .el-table__row:hover > td {
        background-color: rgba(64, 158, 255, 0.12) !important;
      }
    }
  }

  /* el-dialog 对话框暗黑适配（selectUser 等弹窗） */
  .el-dialog {
    --el-dialog-bg-color: var(--wf-bg-white, #1f1f1f);

    &__header {
      background: linear-gradient(135deg, rgba(64,158,255,.08), rgba(43,125,233,.04));
      border-bottom: 1px solid var(--wf-border-color, #333333);
    }

    &__title { color: var(--wf-text-primary, #e0e0e0); }

    &__body { color: var(--wf-text-regular, #b0b0b0); }

    &__headerbtn {
      color: var(--wf-text-secondary, #888888);
      &:hover { color: var(--wf-text-primary, #e0e0e0); }
    }
  }
}
</style>
