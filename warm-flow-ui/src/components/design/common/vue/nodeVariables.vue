<template>
  <div class="node-variables">
    <div v-for="(row, index) in rows" :key="index" class="variable-row">
      <el-input v-model="row.name" placeholder="变量名" class="variable-name" :disabled="disabled"
        @change="writeVariables"></el-input>
      <el-input v-model="row.value" placeholder="值（数字 / true / 文本）" class="variable-value"
        :disabled="disabled" @change="writeVariables"></el-input>
      <el-button link type="danger" :disabled="disabled" @click="removeVariable(index)">
        <el-icon><Delete/></el-icon>
      </el-button>
    </div>
    <el-button link type="primary" :disabled="disabled" @click="addVariable">+ 添加变量</el-button>
    <div class="field-hint">变量缺失时的兜底默认值：流程走到本节点才补充，办理时传入的同名变量优先</div>
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

/** 最近一次写回的 variables 序列化结果，用于区分外部回显与自身写回，避免覆盖输入中的空行 */
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
  rows.value = variables
    ? Object.entries(variables).map(([name, value]) => ({ name, value: String(value) }))
    : [];
}

function writeVariables() {
  const variables = {};
  let has = false;
  rows.value.forEach(row => {
    const name = String(row.name || '').trim();
    if (name !== '') {
      variables[name] = parseVariableValue(row.value);
      has = true;
    }
  });
  const next = { ...(props.modelValue || {}) };
  if (has) {
    next.variables = variables;
    lastWritten = JSON.stringify(variables);
  } else {
    delete next.variables;
    lastWritten = '';
  }
  emit('update:modelValue', next);
}

function addVariable() {
  rows.value.push({ name: '', value: '' });
}

function removeVariable(index) {
  rows.value.splice(index, 1);
  writeVariables();
}

// 外部回显（节点切换 / 打开弹窗 / 后端返回）时同步变量行；自身写回不触发重建
watch(() => {
  const variables = props.modelValue && typeof props.modelValue.variables === 'object'
    ? props.modelValue.variables : null;
  return variables ? JSON.stringify(variables) : '';
}, (value) => {
  if (value !== lastWritten) {
    hydrate();
  }
}, { immediate: true });
</script>

<style scoped lang="scss">
.node-variables { width: 100%; }
.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  .variable-name { width: 130px; }
  .variable-value { flex: 1; }
}
</style>
