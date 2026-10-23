<template>
  <div class="node-variables">
    <div v-for="(row, index) in rows" :key="index" class="variable-row">
      <div class="variable-fields">
        <el-input v-model="row.name" placeholder="变量名" class="variable-name" :disabled="disabled"
          :label="`第 ${index + 1} 个变量名`" @change="writeVariables"></el-input>
        <el-input v-model="row.value" placeholder="值（自动识别）" class="variable-value"
          :disabled="disabled" :label="`第 ${index + 1} 个变量值`" @change="writeVariables"></el-input>
        <el-button link type="danger" class="variable-delete" :disabled="disabled"
                   :aria-label="`删除第 ${index + 1} 个默认变量`" title="删除默认变量"
                   @click="removeVariable(index)">
          <el-icon><Delete/></el-icon>
        </el-button>
      </div>
      <el-input v-model="row.description" class="variable-description" placeholder="说明（选填）"
        maxlength="200" :disabled="disabled" :label="`第 ${index + 1} 个变量说明`"
        @change="writeVariables"></el-input>
    </div>
    <div v-if="rows.length === 0" class="variable-empty">暂无默认变量</div>
    <el-button link type="primary" :disabled="disabled" @click="addVariable">+ 添加变量</el-button>
    <div class="field-hint">变量缺失时补充；同名入参优先。</div>
  </div>
</template>

<script setup name="NodeVariables">
import { ref, watch } from 'vue'

const props = defineProps({
  // nodeConfig 对象：默认变量落在它的 variables 段
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

const emit = defineEmits(['update:modelValue']);

const rows = ref([]);

/** 默认变量说明的元数据键：不改变 variables 的值类型，引擎仍按原结构读取 */
const VARIABLE_META_KEY = '__variableMeta';

/** 最近一次写回的变量与说明快照，用于区分外部回显与自身写回，避免覆盖输入中的空行 */
let lastWritten = '';

/** 数字 / 布尔做类型推断，其余按文本写入，保证条件表达式可直接比较 */
function parseVariableValue(raw) {
  const value = String(raw ?? '').trim();
  if (value === '') return '';
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  if (value === 'true') return true;
  if (value === 'false') return false;
  return value;
}

function hydrate() {
  const variables = props.modelValue && typeof props.modelValue.variables === 'object'
    && props.modelValue.variables !== null ? props.modelValue.variables : null;
  const meta = props.modelValue && props.modelValue[VARIABLE_META_KEY]
    && typeof props.modelValue[VARIABLE_META_KEY] === 'object'
    && !Array.isArray(props.modelValue[VARIABLE_META_KEY]) ? props.modelValue[VARIABLE_META_KEY] : {};
  rows.value = variables
    ? Object.entries(variables).map(([name, value]) => ({
      name,
      value: String(value),
      description: meta[name] === undefined || meta[name] === null ? '' : String(meta[name])
    }))
    : [];
  lastWritten = snapshot(variables, meta);
}

/** 变量与说明的统一快照，避免只监听 variables 时漏掉说明的外部变化 */
function snapshot(variables, meta) {
  return JSON.stringify({
    variables: variables || null,
    meta: meta && Object.keys(meta).length > 0 ? meta : null
  });
}

function writeVariables() {
  const variables = {};
  const meta = {};
  let has = false;
  rows.value.forEach(row => {
    const name = String(row.name || '').trim();
    if (name !== '') {
      variables[name] = parseVariableValue(row.value);
      const description = String(row.description || '').trim();
      if (description) {
        meta[name] = description;
      }
      has = true;
    }
  });
  const next = { ...(props.modelValue || {}) };
  if (has) {
    next.variables = variables;
  } else {
    delete next.variables;
  }
  if (Object.keys(meta).length > 0) {
    next[VARIABLE_META_KEY] = meta;
  } else {
    delete next[VARIABLE_META_KEY];
  }
  lastWritten = snapshot(has ? variables : null, meta);
  emit('update:modelValue', next);
}

function addVariable() {
  rows.value.push({ name: '', value: '', description: '' });
}

function removeVariable(index) {
  rows.value.splice(index, 1);
  writeVariables();
}

// 外部回显（节点切换 / 打开弹窗 / 后端返回）时同步变量行；自身写回不触发重建
watch(() => {
  const variables = props.modelValue && typeof props.modelValue.variables === 'object'
    ? props.modelValue.variables : null;
  const meta = props.modelValue && props.modelValue[VARIABLE_META_KEY]
    && typeof props.modelValue[VARIABLE_META_KEY] === 'object'
    && !Array.isArray(props.modelValue[VARIABLE_META_KEY]) ? props.modelValue[VARIABLE_META_KEY] : {};
  return snapshot(variables, meta);
}, (value) => {
  if (value !== lastWritten) {
    hydrate();
  }
}, { immediate: true });
</script>

<style scoped lang="scss">
.node-variables { width: 100%; }
.variable-row {
  margin-bottom: 8px;

  &:last-of-type {
    margin-bottom: 6px;
  }
}
.variable-fields {
  display: grid;
  grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr) 28px;
  align-items: center;
  gap: 8px;
  .variable-name,
  .variable-value {
    width: 100%;
    min-width: 0;
  }
  .variable-delete {
    width: 28px;
    height: 28px;
    padding: 0;
    justify-content: center;
  }
}
.variable-description {
  margin-top: 4px;
}
.variable-empty {
  margin-bottom: 2px;
  color: var(--wf-text-secondary, #909399);
  font-size: 12px;
  line-height: 20px;
}
.field-hint {
  margin-top: 4px;
  color: var(--wf-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}
</style>
