<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" class="dialogForm" :rules="rules" label-position="top" :disabled="disabled" size="small">
      <!-- 分区结构与节点属性面板（propertySetting/between）保持一致：可折叠 form-section + 收起摘要 -->
      <form-section ref="secBase" title="基础信息" :summary="baseInfoSummary">
        <el-form-item label="流程编码" prop="flowCode">
          <el-input ref="flowCodeInputRef" v-model="form.flowCode" placeholder="请输入流程编码" maxlength="40" />
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

      <form-section ref="secListener" title="监听器配置" :summary="listenerSummary" :default-open="false">
        <el-form-item prop="listenerRows" class="listenerItem">
          <el-table :data="form.listenerRows" style="width: 100%" empty-text="暂无监听器，点击下方「增加行」添加">
            <el-table-column prop="listenerType" :width="isMobile ? 60 : 160" label="类型">
              <template #default="scope">
                <el-form-item :prop="`listenerRows.${scope.$index}.listenerType`" :rules="rules.listenerType">
                  <el-select v-model="scope.row.listenerType" placeholder="请选择类型">
                    <el-option label="开始" value="start"></el-option>
                    <el-option label="分派" value="assignment"></el-option>
                    <el-option label="完成" value="finish"></el-option>
                    <el-option label="创建" value="create"></el-option>
                  </el-select>
                </el-form-item>
              </template>
            </el-table-column>

            <el-table-column prop="listenerPath" label="监听器（可输入类路径）">
              <template #default="scope">
                <el-form-item :prop="`listenerRows.${scope.$index}.listenerPath`" :rules="rules.listenerPath">
                    <el-select
                        v-model="scope.row.listenerPath"
                        placeholder="请输入或选择"
                        allow-create
                        filterable
                        clearable
                        style="width: 100%"
                        @change="(value) => handleListenerPathChange(value, scope.row)">
                        <el-option
                            v-for="item in ListenerVo"
                            :key="item.path"
                            :label="item.description"
                            :value="item.path"/>
                    </el-select>
                </el-form-item>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="65" align="center" v-if="!disabled">
              <template #default="scope">
                <el-button link size="small" type="danger" icon="Delete" @click="handleDeleteRow(scope.$index)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="!disabled" size="small" class="add-row-btn" @click="handleAddRow">增加行</el-button>
        </el-form-item>
      </form-section>
    </el-form>
  </div>
</template>

<script setup name="BaseInfo">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { ElMessageBox } from "element-plus";
import {listenerList} from "@/api/flow/definition.js";
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
  listenerType: "",
  listenerPath: "",
  listenerRows: [],
  // 流程定义扩展属性（flow_definition.ext）：业务自定义 JSON，设计器只透传不写入
  ext: "",
  // 流程定义配置（flow_definition.def_config）：autoPass 等引擎保留键，由开关独占
  defConfig: "",
  autoPass: false
});

watch(() => props.logicJson, newValue => {
  if (newValue && Object.keys(newValue).length > 0) {
    Object.assign(form.value, newValue);
    setListenerData();
    setDefConfig(newValue.defConfig);
    // 移动端新增时强制默认仿钉钉（覆盖logicJson中可能为空的值）
    if (isMobile.value && !props.definitionId) {
      form.value.modelValue = 'MIMIC';
    }
  }
}, { immediate: true });

const definitionList = ref([]);
const ListenerVo = ref([]); // 监听器列表

/** 流程编码输入框引用：新建流程打开抽屉时自动聚焦 */
const flowCodeInputRef = ref();

/** 表单快照（脏检测基准）：抽屉打开 / 确定保存时刷新 */
const initialFormSnapshot = ref('');

function snapshotForm() {
  initialFormSnapshot.value = JSON.stringify(form.value);
}

/** 抽屉是否有未保存改动：当前表单与最近一次快照比对 */
function isDirty() {
  if (!initialFormSnapshot.value) return false;
  return JSON.stringify(form.value) !== initialFormSnapshot.value;
}

/** 标记表单为干净状态（打开抽屉 / 确定保存后调用） */
function markClean() {
  snapshotForm();
}

/** 自动聚焦第一个必填字段（新建流程时引导键盘录入） */
function focusFirstField() {
  proxy.$nextTick(() => {
    flowCodeInputRef.value?.focus?.();
  });
}

/** 清除校验痕迹：抽屉每次打开都从干净状态开始，未操作前不显示红框 */
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
  listenerType: [
    { required: true, message: '监听器不能为空', trigger: ['change', 'blur'] }
  ],
  listenerPath: [
    { required: true, message: '监听器不能为空', trigger: ['change', 'blur'] }
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

const listenerSummary = computed(() => {
  const rows = (form.value.listenerRows || []).filter(
      r => String(r.listenerType || '').trim() || String(r.listenerPath || '').trim()
  );
  return rows.length ? `${rows.length} 个监听器` : '未配置';
});

function setListenerData() {
  // 处理监听器数据
  if (form.value.listenerType) {
    const listenerTypes = form.value.listenerType.split(",");
    const listenerPaths = form.value.listenerPath.split("@@");
    form.value.listenerRows = listenerTypes.map((type, index) => ({
      listenerType: type,
      listenerPath: listenerPaths[index]
    }));
  } else {
    form.value.listenerRows = [];
  }
}

// 增加行
function handleAddRow() {
  form.value.listenerRows.push({ listenerType: "", listenerPath: "" });
  formRef.value?.clearValidate("listenerRows");
}

// 删除行
function handleDeleteRow(index) {
  form.value.listenerRows.splice(index, 1);
}

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
          ['secBase', 'secForm', 'secAuto', 'secListener'].forEach(key => {
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
  form.value.listenerType = form.value.listenerRows.map(row => row.listenerType).join(",")
  form.value.listenerPath = form.value.listenerRows.map(row => row.listenerPath).join("@@")
  form.value.defConfig = buildDefConfig()
  return form.value;
}

/** 获取监听器列表 */
function getListenerList() {
    listenerList().then(response => {
        if (response.code === 200 && response.data) {
            ListenerVo.value = response.data;
        }
    });
}

// 处理监听器路径变化，级联更新类型
function handleListenerPathChange(path, row) {
    if (!path) {
        // 清空时，也清空类型
        row.listenerType = '';
        return;
    }

    // 在下拉选项中查找匹配的项
    const matchedItem = ListenerVo.value.find(item => item.path === path);
    if (matchedItem && matchedItem.type) {
        // 如果找到了匹配项且有 type，则更新 listenerType
        row.listenerType = matchedItem.type;
    } else {
        // 如果是手动输入的，清空类型（或者保持原值，根据需求决定）
        row.listenerType = '';
    }
}

defineExpose({ getFormData, validate, isDirty, markClean, focusFirstField, clearValidation });

getListenerList()
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

/* ========== 6. 监听器表格 ========== */
:deep(.el-table) {
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
    position: relative;

    & > td:first-child {
      position: relative;
    }

    /* hover 左侧蓝色指示条 */
    & > td:first-child::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 0;
      background: var(--wf-primary, #409eff);
      border-radius: 0 2px 2px 0;
      transition: height 0.25s ease;
    }

    &:hover > td:first-child::before {
      height: 60%;
    }

    &:hover > td {
      background-color: var(--wf-primary-light, #ecf5ff) !important;
    }
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

  /* 表格内控件默认透明：整行读作干净白底，hover 轻填充、focus 才显白底+蓝环 */
  .el-input__wrapper,
  .el-select .el-input__wrapper {
    background-color: transparent;

    &:hover { background-color: rgba(118, 128, 150, 0.07); }
    &.is-focus { background-color: var(--wf-bg-white, #fff); }
  }
}

/* ========== 7. 增加行按钮 ========== */
.add-row-btn {
  margin-top: 12px;
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

:deep(.listenerItem) {
  margin-bottom: 0;

  .el-form-item__content {
    margin-left: 0 !important;
  }
  .el-table .el-form-item {
    margin-bottom: 0;
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

:global(html.dark) :deep(.el-table) {
  .el-table__header-wrapper th {
    background: linear-gradient(135deg, rgba(30, 40, 60, 0.95), rgba(25, 35, 55, 0.95)) !important;
  }
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

  /* 监听器表格横向滚动 */
  :deep(.listenerItem) {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;

    &::-webkit-scrollbar {
      height: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(144, 147, 153, 0.3);
      border-radius: 2px;
    }
  }

  :deep(.el-table) {
    /* 手机端去掉固定最小宽度，让表格自适应容器 */
    min-width: unset !important;

    .el-table__header-wrapper th {
      font-size: 12px;
    }

    /* 类型列：手机端紧凑 */
    .el-table__header-wrapper th:first-child,
    td:first-child {
      min-width: 60px !important;
      width: 60px !important;

      .cell {
        padding-left: 4px !important;
        padding-right: 4px !important;
      }

      .el-select,
      .el-select .el-input {
        width: 100% !important;
      }
    }

    /* 操作列确保可见 */
    .el-table__header-wrapper th:last-child:not(:first-child),
    td:last-child:not(:first-child) {
      min-width: 45px !important;
      width: 45px !important;
      padding: 0 !important;

      .cell {
        padding: 2px !important;
      }
    }

    /* 监听器列自适应占满剩余空间 */
    .el-table__header-wrapper th:nth-child(2),
    td:nth-child(2) {
      min-width: 100px;
      width: auto;
    }
  }

  .add-row-btn {
    height: 24px;
    font-size: 12px;
    letter-spacing: 1px;
    margin-top: 8px;
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

  :deep(.el-table) {
    min-width: 450px;
    font-size: 12px;

    th,
    td {
      padding: 4px 0 !important;
    }
  }

  .add-row-btn {
    height: 24px;
    font-size: 12px;
  }
}
</style>
