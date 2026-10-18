<template>
  <div class="gateway-wrapper">
    <el-form ref="formRef" class="gatewayForm" :model="form" label-width="110px" :disabled="disabled" size="small">
      <div class="base-settings-section">
        <div class="base-settings-content">
          <el-form-item label="节点编码：" prop="nodeCode">
            <el-input v-model="form.nodeCode" :disabled="disabled"></el-input>
          </el-form-item>
          <el-form-item label="默认变量：">
            <node-variables v-model="form.nodeConfig" :disabled="disabled"></node-variables>
          </el-form-item>
        </div>
      </div>
    </el-form>

    <!-- 分支一览：网关的条件配在每条出线上，散在画布上得一条条点开才看得全。
         这里把出线集中列出，「编辑」直接切到该边的属性面板。 -->
    <form-section title="分支条件" :summary="branchSummary">
      <div class="gateway-branch-hint" v-if="isParallel">
        并行网关的出线条件不参与流转判断，分支会全部执行。
      </div>
      <div class="gateway-branch-hint" v-else-if="branchList.length === 0">
        还没有出线：从该网关拖出连线后，再回来配置分支条件。
      </div>
      <el-table v-else :data="branchList" class="gateway-branch-table">
        <el-table-column prop="targetName" label="目标节点" min-width="92" show-overflow-tooltip/>
        <el-table-column prop="skipName" label="跳转名称" min-width="76" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'is-empty': !row.skipName }">{{ row.skipName || '未填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="condition" label="跳转条件" min-width="96" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'is-empty': !row.condition }">{{ row.condition || '默认（无条件）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="56" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="disabled" @click="$emit('edit-skip', row.id)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </form-section>
  </div>
</template>

<script setup name="Gateway">
import nodeVariables from "./nodeVariables";
import formSection from "./formSection.vue";
import { formatSkipCondition } from "@/components/design/common/js/tool.js";

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
  // 画布上的全部节点 / 连线（由属性面板透传），用于把出线渲染成可读列表
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
});

defineEmits(['edit-skip']);

const form = ref(props.modelValue);

// 网关出线（属性面板里的 nodeCode 即画布节点 id）
const branchList = computed(() => {
  const selfCode = form.value?.nodeCode;
  if (!selfCode) {
    return [];
  }
  return (props.skips || [])
      .filter(skip => skip.sourceNodeId === selfCode)
      .map(skip => {
        const target = (props.nodes || []).find(node => node.id === skip.targetNodeId);
        return {
          id: skip.id,
          targetName: target?.text?.value || target?.properties?.nodeName || skip.targetNodeId || '未知节点',
          skipName: skip.text?.value || skip.properties?.skipName || '',
          condition: formatSkipCondition(skip.properties?.skipCondition)
        };
      });
});

// 并行网关所有分支都会执行，条件不参与判断
const isParallel = computed(() => form.value?.nodeType === 'parallel');

const branchSummary = computed(() => {
  if (isParallel.value) {
    return '并行执行，不判断条件';
  }
  if (branchList.value.length === 0) {
    return '未配置出线';
  }
  const withCondition = branchList.value.filter(item => item.condition).length;
  return `${branchList.value.length} 条出线，${withCondition} 条已配条件`;
});

</script>

<style scoped lang="scss">
@import '@/assets/styles/_common.scss';

.gateway-wrapper { width: 100%; html.dark & { background: var(--wf-bg-color, #141414); border-radius: var(--wf-radius-lg, 12px); } }
.gatewayForm { border-top: 0; width: 100%; }

/* 引入公共样式：基础配置卡片 */
@include base-settings-card;
@include responsive-adaption;

/* gateway 特有图标 */
.ext-icon-base {
  color: var(--wf-primary, #409eff);
  svg { width: 16px; height: 16px; }
}

.gateway-branch-section { width: 100%; }

.gateway-branch-hint {
  margin-bottom: 12px;
  color: var(--wf-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}

/* 未填写态：淡灰文字，和「有值」区分开 */
.gateway-branch-table .is-empty {
  color: var(--wf-text-secondary, #909399);
}
</style>
