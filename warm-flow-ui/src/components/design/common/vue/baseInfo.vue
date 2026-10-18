<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" class="dialogForm" :rules="rules" label-position="top" :disabled="disabled" size="small">
      <!-- 分区结构与节点属性面板（propertySetting/between）保持一致：可折叠 form-section + 收起摘要 -->
      <form-section ref="secBase" title="基础信息" :summary="baseInfoSummary">
        <el-form-item label="流程编码" prop="flowCode">
          <el-input ref="flowCodeInputRef" v-model="form.flowCode" placeholder="请输入流程编码" maxlength="40" :disabled="codeLocked" />
          <span class="form-tip form-tip--block" v-if="codeLocked">流程编码是流程的唯一标识，保存后不可修改</span>
        </el-form-item>

        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="请输入流程名称" maxlength="100" @input="nameChange" />
        </el-form-item>

        <el-form-item label="设计器模型" prop="modelValue">
          <el-radio-group v-model="form.modelValue" :disabled="isMobile" @change="handleModelRadioChange">
            <el-radio label="CLASSICS">经典模型</el-radio>
            <el-radio label="MIMIC">仿钉钉模型</el-radio>
          </el-radio-group>
          <div class="radio-card-warning">切换模型将按新布局重排节点，保存后生效并更新设计器模型</div>
          <div class="radio-card-warning radio-card-warning--mobile" v-if="isMobile">移动端不支持选择模型，如果新增默认仿钉钉</div>
        </el-form-item>

        <el-form-item label="流程类别" prop="category">
          <el-tree-select
              v-model="form.category"
              :data="categoryList"
              :props="{ value: 'id', label: 'name', children: 'children' }"
              value-key="id"
              placeholder="请选择流程类别"
              style="width: 100%"
              check-strictly/>
        </el-form-item>
      </form-section>

      <form-section ref="secForm" title="审批表单" :summary="formSummary">
        <el-form-item label="自定义表单" prop="formCustom">
          <el-switch
            v-model="form.formCustom"
            size="small"
            active-value="Y"
            inactive-value="N"
            active-text="是"
            inactive-text="否" />
          <span class="form-tip form-tip--block">{{ form.formCustom === 'Y' ? '选择已配置的自定义表单' : '填写审批页面路径' }}</span>
        </el-form-item>

        <el-form-item label="表单路径" prop="formPath" v-if="form.formCustom === 'N'">
          <el-input v-model="form.formPath" placeholder="请输入审批表单路径" maxlength="100"/>
        </el-form-item>

        <el-form-item label="表单唯一标识" prop="formPath" v-else-if="form.formCustom === 'Y'">
            <el-tree-select
                v-model="form.formPath"
                :data="formPathList"
                :props="{ value: 'id', label: 'name', children: 'children' }"
                value-key="id"
                placeholder="请选择表单唯一标识"
                check-strictly/>
        </el-form-item>
      </form-section>

      <form-section ref="secAuto" title="自动审批" :summary="autoPassSummary" :default-open="false">
        <el-form-item prop="autoPass">
          <el-switch v-model="form.autoPass" size="small" active-text="同一办理人自动通过" />
          <span class="form-tip form-tip--block">下一节点办理人包含当前办理人时，由引擎以该办理人身份自动通过该待办</span>
        </el-form-item>
      </form-section>
    </el-form>
  </div>
</template>

<script setup name="BaseInfo">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { ElMessageBox } from "element-plus";
import formSection from "./formSection";

const { proxy } = getCurrentInstance();

/** 引擎保留键：同一办理人自动审批，存放在流程定义配置 def_config，由开关独占 */
const AUTO_PASS_KEY = 'autoPass';

// 响应式屏幕检测
const isMobile = ref(false);

function checkMobile() {
  isMobile.value = window.innerWidth <= 768;
}

onMounted(() => {
  checkMobile();
  window.addEventListener('resize', checkMobile);
  // 移动端新增（无definitionId）时强制默认仿钉钉
  if (isMobile.value && !props.definitionId) {
    form.value.modelValue = 'MIMIC';
  }
  // 挂载完成后记录初始快照（脏检测基准），未编辑前 isDirty 恒为 false
  snapshotForm();
});

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile);
});
const props = defineProps({
  disabled: { // 是否禁止
    type: Boolean,
    default: false
  },
  logicJson: {
    type: Object,
    default () {
      return {}
    }
  },
  categoryList: {
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
  definitionId: {
    type: String,
    default () {
      return null
    }
  },
});

const form = ref({
  id: null,
  flowCode: "",
  flowName: "",
  modelValue: "",
  category: "",
  formCustom: "N",
  formPath: "",
  // 流程定义扩展属性（flow_definition.ext）：业务自定义 JSON，设计器只透传不写入
  ext: "",
  // 流程定义配置（flow_definition.def_config）：autoPass 等引擎保留键，由开关独占
  defConfig: "",
  autoPass: false
});

watch(() => props.logicJson, newValue => {
  if (newValue && Object.keys(newValue).length > 0) {
    Object.assign(form.value, newValue);
    setDefConfig(newValue.defConfig);
    // 移动端新增时强制默认仿钉钉（覆盖logicJson中可能为空的值）
    if (isMobile.value && !props.definitionId) {
      form.value.modelValue = 'MIMIC';
    }
  }
}, { immediate: true });

/** 流程编码输入框引用：新建流程时自动聚焦 */
const flowCodeInputRef = ref();

/**
 * 流程编码是否锁定：流程已有定义 id（已落库）即唯一标识已成事实，不允许再改，
 * 否则会破坏引擎按 flowCode 关联的实例 / 历史数据。新增流程（无 id）可自由填写。
 */
const codeLocked = computed(() => !!(props.definitionId || form.value.id));

/** 表单快照（脏检测基准）：载入完成 / 保存后刷新 */
const initialFormSnapshot = ref('');
// 初始载入（props 回填）阶段不上报「未保存」，markClean() 之后才开启
let dirtyTracking = false;

function snapshotForm() {
  initialFormSnapshot.value = JSON.stringify(form.value);
}

/** 表单是否有未保存改动：当前表单与最近一次快照比对 */
function isDirty() {
  if (!initialFormSnapshot.value) return false;
  return JSON.stringify(form.value) !== initialFormSnapshot.value;
}

/** 标记表单为干净状态（定义信息载入完成 / 保存完成后调用），并开启改动上报 */
function markClean() {
  snapshotForm();
  dirtyTracking = true;
}

/**
 * 定义字段改动：向设计器同步「有未保存改动」（原抽屉「确定」按钮承担的动作）。
 * 只盯用户能改的字段，不 deep watch 整个 form —— 里面还挂着画布的 nodes / edges。
 */
watch(() => [form.value.flowCode, form.value.flowName, form.value.modelValue, form.value.category,
  form.value.formCustom, form.value.formPath, form.value.autoPass], () => {
  if (!dirtyTracking) return;
  proxy.$emit('update:dirty', true);
});

/** 自动聚焦第一个必填字段（新建流程时引导键盘录入） */
function focusFirstField() {
  proxy.$nextTick(() => {
    flowCodeInputRef.value?.focus?.();
  });
}

/** 清除校验痕迹（需要从干净状态重新校验时调用，如保存成功后） */
function clearValidation() {
  formRef.value?.clearValidate();
}


const rules = {
  modelValue: [
    { required: true, message: "设计器模型不能为空", trigger: "blur" }
  ],
  flowCode: [
    { required: true, message: "流程编码不能为空", trigger: "blur" }
  ],
  flowName: [
    { required: true, message: "流程名称不能为空", trigger: "blur" }
  ],
  formCustom: [
    { required: true, message: "请选择审批表单是否自定义", trigger: "change" }
  ],
};

// 表单引用（用于校验）
const formRef = ref();

/* ===== 分区收起摘要：收起后也能一眼看到当前配置（与节点面板 form-section 摘要同语言） ===== */
const baseInfoSummary = computed(() => {
  const code = form.value.flowCode || '未设置编码';
  const name = form.value.flowName || '未命名流程';
  return `${code} · ${name}`;
});

const formSummary = computed(() => {
  if (form.value.formCustom === 'Y') {
    return form.value.formPath ? `自定义表单 · ${form.value.formPath}` : '自定义表单 · 未选择';
  }
  return form.value.formPath ? `内置表单 · ${form.value.formPath}` : '内置表单 · 未填写路径';
});

const autoPassSummary = computed(() => (form.value.autoPass ? '已开启' : '未开启'));

// 定义配置回显：autoPass 只归 def_config，不读业务扩展字段 ext
function setDefConfig(defConfig) {
  form.value.defConfig = defConfig || '';
  form.value.autoPass = readAutoPass(defConfig) === true;
}

/** 解析 JSON 中的 autoPass：声明为 true/'true' 返回 true，声明为 false/'false' 返回 false，未声明或非法返回 null */
function readAutoPass(json) {
  if (!json) {
    return null;
  }
  try {
    const parsed = JSON.parse(json);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null;
    }
    const autoPass = parsed[AUTO_PASS_KEY];
    if (autoPass === true || autoPass === 'true') {
      return true;
    }
    if (autoPass === false || autoPass === 'false') {
      return false;
    }
    return null;
  } catch (e) {
    return null;
  }
}

/** 组装流程定义配置 JSON：无论开关与否都显式写出 autoPass，定义配置声明后即优先生效 */
function buildDefConfig() {
  return JSON.stringify({ [AUTO_PASS_KEY]: form.value.autoPass === true });
}

// 表单必填校验：失败时展开所有分区，确保红字对用户可见（分区可能处于收起状态）
function validate() {
  return new Promise((resolve, reject) => {
    proxy.$nextTick(() => {
      proxy.$refs.formRef.validate(valid => {
        if (valid) {
          resolve(true);
        } else {
          ['secBase', 'secForm', 'secAuto'].forEach(key => {
            proxy.$refs[key]?.open?.();
          });
          resolve(false);
        }
      });
    });
  });
}

function nameChange(flowName) {
  // 可以在这里添加额外的逻辑，比如验证或格式化
  proxy.$emit('update:flow-name', flowName); // 如果需要通知父组件
}

function modelValueChange() {
  proxy.$emit('update:model-value'); // 如果需要通知父组件
}

/**
 * 设计器模型单选（工具栏切换入口已移除，这里成为唯一入口）：
 * 新流程直接生效；已有流程先确认（切换会重排节点布局），取消则回退到画布当前模型。
 */
async function handleModelRadioChange() {
  if (!props.definitionId) {
    modelValueChange();
    return;
  }
  try {
    await ElMessageBox.confirm(
        '切换为另一种模型后将按新布局重排全部节点，保存后生效并更新该流程的设计器模型。确定切换吗？',
        '切换设计器模型',
        { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' }
    );
    modelValueChange();
  } catch (e) {
    // 取消切换：回退单选到画布当前模型
    form.value.modelValue = props.logicJson?.modelValue || form.value.modelValue;
  }
}

function getFormData() {
  form.value.defConfig = buildDefConfig()
  return form.value;
}

defineExpose({ getFormData, validate, isDirty, markClean, focusFirstField, clearValidation });
</script>

<style scoped lang="scss">
/* ========== 1. 容器背景层 ========== */
.app-container {
  padding: 0;
  background-color: var(--wf-bg-white, #fff);
  min-height: 100%;
  /* iOS / 现代质感：系统字体栈 + 字形平滑 */
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  html.dark & {
    background-color: var(--wf-bg-color, #141414);
  }
}

.dialogForm {
  max-width: 960px;
  width: 100%;
  margin: 4px auto 16px;
  padding: 0;
  background: transparent;
  border-top: 0;
}

/* ========== 2. 分区改用 form-section 组件（与节点属性面板同语言），此处不再自定义分区样式 ========== */

/* ========== 3/4. 表单项增强（顶部标签：窄面板原生形态，标签在上、输入件满宽） ========== */
:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--wf-text-regular, #606266);
  font-size: 13px;
  line-height: 1.4;
  padding-bottom: 4px;
  margin-bottom: 0;
}

/* 错误提示增加上下间距 - PC端/平板端 */
:deep(.el-form-item__error) {
  padding-top: 6px;
  padding-bottom: 4px;
}

/* 右侧内容区：统一左对齐 */
::deep(.el-form-item__content) {
  text-align: left;
  justify-content: flex-start;
}

:deep(.el-input__wrapper),
:deep(.el-select .el-input__wrapper),
:deep(.el-textarea__inner) {
  /* 默认白底 + 浅边框：未填写也清爽，不再用发灰的填充底 */
  transition: background-color 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  border-radius: 10px;
  background-color: var(--wf-bg-white, #fff);
  box-shadow: none;
  border: 1px solid var(--wf-border-light, #dcdfe6);
  position: relative;

  &:hover {
    border-color: var(--wf-primary, #409eff);
  }

  /* focus：主色描边 + 柔和光环 */
  &.is-focus,
  &:focus {
    background-color: var(--wf-bg-white, #fff);
    border-color: var(--wf-primary, #409eff);
    box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.12);
  }
}

/* 校验失败态：统一红边 + 红光环，优先级压过 focus 的蓝色，避免红蓝混色 */
:deep(.el-form-item.is-error .el-input__wrapper),
:deep(.el-form-item.is-error .el-input__wrapper.is-focus),
:deep(.el-form-item.is-error .el-select .el-input__wrapper) {
  border-color: var(--wf-danger, #f56c6c);
  box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.12);
}

/* 禁用态暗黑模式覆盖（已发布/失效状态）— 完全全局选择器，避开 scoped 编译 */
:global(html.dark .dialogForm .el-form-item .el-input__wrapper.is-disabled),
:global(html.dark .dialogForm .el-form-item .el-textarea__inner.is-disabled),
:global(html.dark .dialogForm .el-form-item .el-select .el-input__wrapper.is-disabled) {
  background-color: #2a2d35 !important;
  box-shadow: none !important;
  border-color: #3a3e48 !important;
}

/* ========== 5. 设计器模型：普通单选按钮（卡片样式已按用户要求移除） ========== */

/* ========== 旧样式兼容覆盖 ========== */
:deep(.Tabs) {
  margin-top: -20px;
  box-shadow: none;
  border-bottom: 0;
  .el-tabs__content {
    display: none;
  }
  .el-tabs__item.is-active {
    margin-left: 0;
    border-top: 1px solid var(--el-border-color);
    margin-top: 0;
  }
}

/* 表单项警示提示样式（amber 级，非错误红） */
:deep(.el-form-item) {
  .radio-card-warning {
    margin-top: 8px;
    padding: 6px 10px;
    background: var(--wf-warning-lighter, #fdf6ec);
    border-radius: var(--wf-radius-sm, 4px);
    font-size: 12px;
    color: var(--wf-warning, #e6a23c);
    line-height: 1.5;
    width: 100%;
  }
}

/* 开关 / 字段旁的轻提示 */
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--wf-text-secondary, #909399);
  line-height: 1.5;
}

/* 开关下方的整行 helper 提示：不与开关抢一行，读起来更像说明而非标签 */
.form-tip--block {
  display: block;
  width: 100%;
  margin-left: 0;
  margin-top: 4px;
}

/* ========== 8. 暗黑模式增强 ========== */
:global(html.dark) {
  .app-container {
    background-color: var(--wf-bg-color, #141414) !important;
    background-image: none;
  }
}

/* iOS 填充式输入框：暗黑覆盖（优先级需高于全局 html.dark .el-input__wrapper） */
:global(html.dark) :deep(.el-input__wrapper),
:global(html.dark) :deep(.el-select .el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.06);
  box-shadow: none;
  border: 1px solid transparent;
}
:global(html.dark) :deep(.el-input__wrapper:hover),
:global(html.dark) :deep(.el-select .el-input__wrapper:hover) {
  background-color: rgba(255, 255, 255, 0.10);
}
:global(html.dark) :deep(.el-input__wrapper.is-focus),
:global(html.dark) :deep(.el-input__wrapper:focus) {
  background-color: rgba(255, 255, 255, 0.04);
  border-color: var(--wf-primary, #409eff);
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.18);
}

/* ========== 9. 滚动条美化 ========== */
.app-container {
  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(144, 147, 153, 0.3);
    border-radius: 3px;
    transition: all 0.3s ease;
  }

  &:hover::-webkit-scrollbar-thumb {
    background: rgba(144, 147, 153, 0.5);
  }

  &::-webkit-scrollbar-thumb:hover {
    background: rgba(144, 147, 153, 0.7);
    width: 8px;
  }
}

/* ========== 响应式适配：平板端 (<= 1024px) ========== */
@media (max-width: 1024px) {
  .dialogForm {
    margin: 12px auto;
  }
}

/* ========== 响应式适配：手机端 (<= 768px) ========== */
@media (max-width: 768px) {
  .dialogForm {
    margin: 8px 4px;
  }

  .app-container {
    overflow-x: hidden;
  }

  /* 手机端错误提示间距 */
  ::deep(.el-form-item__error) {
    padding-top: 10px;
    padding-bottom: 8px;
    line-height: 1.4;
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    font-size: 13px !important;
  }

  /* 确保表单项内容区不限制宽度 */
  :deep(.el-form-item__content) {
    max-width: 100% !important;
    overflow: visible !important;
  }

  :deep(.el-form-item) {
    .radio-card-warning {
      margin-top: 4px;
      padding: 5px 8px;
      font-size: 11px;
    }
    .radio-card-warning--mobile {
      background: var(--wf-primary-lighter, #f0f7ff);
      border-color: rgba(64, 158, 255, 0.3);
      color: var(--wf-primary, #409eff);
    }
  }
}

/* ========== 响应式适配：超小屏 (<= 480px) ========== */
@media (max-width: 480px) {
  .dialogForm {
    margin: 4px 2px;
  }

  /* 超小屏错误提示间距 */
  ::deep(.el-form-item__error) {
    padding-top: 6px;
    padding-bottom: 4px;
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    font-size: 12px !important;
  }
}
</style>
