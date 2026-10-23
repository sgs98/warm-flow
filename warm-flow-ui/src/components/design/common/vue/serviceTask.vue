<template>
  <div class="service-task">
    <!-- 现代化页签 -->
    <div class="modern-tabs-wrapper">
      <div class="modern-tabs">
        <div
          v-for="item in tabsList"
          :key="item.name"
          class="modern-tab-item"
          :class="{ 'is-active': tabsValue === item.name }"
          @click="tabsValue = item.name"
        >
          <svg class="tab-icon" viewBox="0 0 24 24"><path :d="item.iconPath" fill="currentColor"/></svg>
          <span class="tab-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 基础设置 -->
    <div v-show="tabsValue === '1'" class="tabPane">
      <el-form ref="formRef" class="serviceTaskForm" :model="form" :rules="rules" label-width="110px" :disabled="disabled" size="small">
        <div class="base-settings-section">
          <div class="base-settings-content">
            <el-form-item label="节点编码：" prop="nodeCode">
              <el-input v-model="form.nodeCode" :disabled="disabled"></el-input>
            </el-form-item>
            <el-form-item label="节点名称：" prop="nodeName">
              <el-input v-model="form.nodeName" :disabled="disabled"></el-input>
            </el-form-item>
            <el-form-item label="默认变量：" class="default-variables-item">
              <node-variables v-model="form.nodeConfig" :disabled="disabled"></node-variables>
            </el-form-item>
            <el-form-item label="执行方式：" prop="serviceType">
              <div class="radio-card-group">
                <label
                  v-for="way in serviceTypes"
                  :key="way.value"
                  class="radio-card-item"
                  :class="{ 'is-checked': form.serviceType === way.value }"
                  @click="!disabled && (form.serviceType = way.value)"
                >
                  <span class="radio-card-dot"></span>
                  <span class="radio-card-text">{{ way.label }}</span>
                  <el-tooltip effect="dark" placement="top" :content="way.tip">
                    <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
                  </el-tooltip>
                </label>
              </div>
            </el-form-item>
            <el-form-item v-if="form.serviceType === 'class'" label="处理类：" prop="serviceHandler">
              <el-input v-model="form.serviceHandler" :disabled="disabled"
                        placeholder="类全限定名，如 org.demo.DemoClassServiceTask（Bean 名可省略包路径）"></el-input>
            </el-form-item>
            <template v-else>
              <el-form-item label="执行表达式：" prop="serviceExpression">
                <el-input v-model="form.serviceExpression" :disabled="disabled"
                          :placeholder="form.serviceType === 'delegateExpression'
                            ? '需返回 ServiceTaskHandler，如 #{@demoDelegateServiceTask}'
                            : '如 #{@demoOrderService.createOrder(#execution)}'"></el-input>
              </el-form-item>
              <el-form-item v-if="form.serviceType === 'expression'" label="结果变量：" prop="resultVariable">
                <el-input v-model="form.resultVariable" :disabled="disabled"
                          placeholder="表达式返回值写入的流程变量名，可留空"></el-input>
              </el-form-item>
            </template>
            <div class="field-hint">
              <span>节点到达后自动执行，无需人工办理；表达式内可用 <code>#execution</code> 访问流程实例上下文。</span>
            </div>
          </div>
        </div>
      </el-form>
    </div>

    <!-- 监听器 -->
    <div v-show="tabsValue === '2'" class="tabPane tabPane-full">
      <div class="section-card section-purple">
        <div class="section-card-body">
          <slot name="form-item-task-listenerType" :model="form" field="listenerType">
            <!-- 服务任务节点无「分派」事件，选项裁剪为 开始/完成/创建 -->
            <listener-config :rows="form.listenerRows" :disabled="disabled" :types="serviceTaskListenerTypes" />
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="ServiceTask">
import { WarningFilled } from '@element-plus/icons-vue'
import nodeVariables from "./nodeVariables";
import listenerConfig from "./listenerConfig";

const props = defineProps({
  modelValue: {
    type: Object,
    default () {
      return {}
    }
  },
  disabled: { // 是否禁止
    type: Boolean,
    default: false
  },
});

const form = ref(props.modelValue);
// 无监听器时保证行数组存在，共享组件 listenerConfig 依赖它增删行
if (!Array.isArray(form.value.listenerRows)) {
  form.value.listenerRows = [];
}
const formRef = ref(null);
const isMobile = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768);

// 执行方式：与后端 ServiceTaskConfig 的 type 取值一一对应
const serviceTypes = [
  { value: 'class', label: '类Bean', tip: '按类全限定名 / Bean 名实例化，需实现 ServiceTaskHandler' },
  { value: 'delegateExpression', label: '委托表达式', tip: '表达式结果须是 ServiceTaskHandler 实例，如 #{@demoDelegateServiceTask}' },
  { value: 'expression', label: '方法表达式', tip: '执行方法表达式，返回值可写入结果变量，如 #{@bean.method(#execution)}' },
]

const rules = {
  serviceHandler: [{
    validator: (rule, value, callback) => {
      if (form.value.serviceType === 'class' && !String(value || '').trim()) {
        callback(new Error('请输入类路径或 Bean 名称'))
      } else callback()
    },
    trigger: 'blur'
  }],
  serviceExpression: [{
    validator: (rule, value, callback) => {
      if (form.value.serviceType !== 'class' && !String(value || '').trim()) {
        callback(new Error('请输入执行表达式'))
      } else callback()
    },
    trigger: 'blur'
  }],
}

// Tab 图标（单路径 SVG，viewBox 0 0 24 24，跟随 tab 文字色 currentColor）
const TAB_ICONS = {
  base: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  listener: 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
};
const tabsList = ref([
  { name: "1", label: "基础设置", iconPath: TAB_ICONS.base },
  { name: "2", label: "节点监听器", iconPath: TAB_ICONS.listener }
]);
const tabsValue = ref("1");

// 服务任务节点无「分派」事件，监听器类型选项裁剪
const serviceTaskListenerTypes = [
  { label: '开始', value: 'start' },
  { label: '完成', value: 'finish' },
  { label: '创建', value: 'create' }
];

// 增加行 / 删除行：由共享组件 listenerConfig 处理

/**
 * 表单校验：抽屉关闭前由 propertySetting 调用，任一必填项不通过则不关闭
 */
function validate() {
  return new Promise((resolve) => {
    formRef.value.validate((valid) => resolve(!!valid));
  });
}

defineExpose({
  validate
});
</script>

<style scoped lang="scss">
@import '@/assets/styles/_common.scss';

.service-task { width: 100%; html.dark & { background: var(--wf-bg-color, #141414); border-radius: var(--wf-radius-lg, 12px); } }
.serviceTaskForm { border-top: 0; width: 100%; }

/* 引入公共样式：现代化页签 + 基础配置卡片 + 监听器卡片 */
@include modern-tabs;
@include base-settings-card;
@include section-card;
@include responsive-adaption;

/* 紫色主题 - 监听器（与开始/中间节点面板一致） */
.section-purple {
  .section-purple-header {
    background: transparent;
    border-bottom-color: var(--wf-border-lighter, #ebeef5);
    html.dark & { background: transparent; border-bottom-color: var(--wf-border-color, #333333); }
  }
  .section-purple-icon { color: #8960dc; }
  .section-purple-title { color: #8960dc; }
}

/* 增加行按钮：虚线主色（与 baseInfo / between 统一） */
.action-buttons {
  margin-top: 12px;
}
.add-row-btn {
  width: 100%;
  border: 1.5px dashed var(--wf-primary, #409eff) !important;
  color: var(--wf-primary, #409eff) !important;
  background: transparent !important;
  border-radius: 8px;
  transition: all 0.3s ease;
  height: 24px;
  font-size: 12px;
  letter-spacing: 1px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: var(--wf-primary-light, #ecf5ff) !important;
    border-style: solid !important;
    border-color: var(--wf-primary, #409eff) !important;
    box-shadow: var(--wf-shadow-primary, 0 2px 8px rgba(64, 158, 255, 0.3));
  }
}

/* 执行方式说明（小字提示，非字段） */
.field-hint {
  padding: 0 0 4px 110px;
  font-size: 12px;
  line-height: 18px;
  color: var(--wf-text-placeholder, #909399);
  html.dark & { color: var(--wf-text-placeholder, #8d9095); }
  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: var(--wf-primary-light, #ecf5ff);
    color: var(--wf-primary, #409eff);
    font-size: 12px;
  }
}

/* 表格内表单左对齐 */
:deep(.el-table .el-form-item) {
  margin-bottom: 0;
  .el-form-item__label {
    display: none !important; width: 0 !important; min-width: 0 !important;
    padding-right: 0 !important; overflow: hidden !important; visibility: hidden !important;
    height: 0 !important;
  }
  .el-form-item__content {
    display: block !important; margin-left: 0 !important;
  }
}
:deep(.listenerItem) .el-table .el-form-item { margin-bottom: 0; }

/* 卡片式单选（与 between 面板一致） */
.radio-card-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
.radio-card-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 4px 9px;
  border-radius: var(--wf-radius, 8px);
  border: 1px solid var(--wf-border-color, #dcdfe6);
  background: var(--wf-bg-white, #fff);
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: var(--wf-text-regular, #606266);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  html.dark & {
    background: var(--wf-bg-color, #1a1a1a);
    border-color: var(--wf-border-color, #333333);
    color: var(--wf-text-regular, #b0b0b0);
  }
  &:hover {
    border-color: var(--wf-primary, #409eff);
    background: var(--wf-primary-lighter, #f0f7ff);
    color: var(--wf-primary, #409eff);
    html.dark & { background: rgba(64, 158, 255, 0.08); }
  }
  &.is-checked {
    border-color: var(--wf-primary, #409eff);
    background: var(--wf-primary-light, #ecf5ff);
    color: var(--wf-primary, #409eff);
    font-weight: 500;
    html.dark & { background: rgba(64, 158, 255, 0.12); }
    .radio-card-dot {
      border-color: var(--wf-primary, #409eff);
      background: var(--wf-primary, #409eff);
      box-shadow: inset 0 0 0 3px var(--wf-bg-white, #fff);
      html.dark & { box-shadow: inset 0 0 0 3px var(--wf-bg-color, #141414); }
    }
  }
  .radio-card-dot {
    width: 13px; height: 13px; min-width: 13px;
    border-radius: 50%; border: 1.5px solid var(--wf-border-color, #dcdfe6);
    transition: all 0.25s ease; background: var(--wf-bg-white, #fff);
    html.dark & { border-color: var(--wf-border-color, #444444); background: var(--wf-bg-color, #1a1a1a); }
  }
  .radio-card-text { line-height: 1; }
  .radio-card-tip { color: var(--wf-text-placeholder, #c0c4cc); transition: color 0.25s ease; }
}
</style>
