<template>
  <div v-if="initError" class="iframe-init-error">
    <el-alert
      :title="initError"
      type="error"
      show-icon
      :closable="false"
    />
  </div>
  <component v-else v-bind:is="component"></component>
</template>


<script setup>
import Design from './views/flow-design/index.vue';
import FlowChart from './views/flow-design/flowChart.vue';
import Form from './views/form-design/index.vue';
import FormCreate from './views/form-design/formCreate.vue';
import useAppStore from "@/store/app";

const appStore = useAppStore();
const appParams = computed(() => appStore.appParams);
const component = shallowRef(null);
const initError = ref('');
onMounted(async () => {
  if (!appParams.value) await appStore.fetchTokenName();
  let pathObj = {
    form: Form,
    FlowChart: FlowChart,
    formCreate: FormCreate
  };
  const pageType = appParams.value.type;
  if (pageType && !pathObj[pageType]) {
    initError.value = `不支持的页面类型：${pageType}`;
    return;
  }
  if (['FlowChart', 'form'].includes(pageType) && !appParams.value.id) {
    initError.value = '缺少必需的流程定义或实例 ID 参数：id';
    return;
  }
  component.value = pathObj[pageType] || Design;
});
</script>

<style scoped>
.iframe-init-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 24px;
  background: var(--wf-bg-color, #f5f7fb);
}

.iframe-init-error :deep(.el-alert) {
  width: min(480px, 100%);
}
</style>
