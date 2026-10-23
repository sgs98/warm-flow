<template>
  <div class="listener-config">
    <el-table :data="rows" style="width: 100%" empty-text="暂无监听器，点击下方「增加行」添加">
      <el-table-column prop="listenerType" label="类型" :width="isMobile ? 60 : 130">
        <template #default="scope">
          <el-form-item :prop="`${prefix}.${scope.$index}.listenerType`" :rules="rowRules.listenerType">
            <el-select v-model="scope.row.listenerType" placeholder="请选择" :disabled="disabled">
              <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value"/>
            </el-select>
          </el-form-item>
        </template>
      </el-table-column>
      <el-table-column prop="listenerPath" label="监听器（可输入类路径）">
        <template #default="scope">
          <el-form-item :prop="`${prefix}.${scope.$index}.listenerPath`" :rules="rowRules.listenerPath">
            <!-- 业务方未实现 ListenerListService 时候选为空，下拉会误导用户，退化为输入框 -->
            <el-select
                v-if="registeredListeners.length > 0"
                v-model="scope.row.listenerPath"
                placeholder="请输入或选择"
                allow-create
                filterable
                clearable
                :disabled="disabled"
                style="width: 100%"
                @change="(value) => handlePathChange(value, scope.row)">
              <el-option
                  v-for="item in registeredListeners"
                  :key="item.path"
                  :label="item.description"
                  :value="item.path"/>
            </el-select>
            <el-input v-else v-model="scope.row.listenerPath" placeholder="请输入监听器全限定类路径" :disabled="disabled"/>
          </el-form-item>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="65" align="center" v-if="!disabled">
        <template #default="scope">
          <el-button link size="small" type="danger" :icon="Delete" @click="removeRow(scope.$index)"/>
        </template>
      </el-table-column>
    </el-table>
    <el-button v-if="!disabled" size="small" class="add-row-btn" @click="addRow">增加行</el-button>
    <div class="listener-scope-tip">
      <el-icon><InfoFilled /></el-icon>
      <span>节点监听器仅在该节点触发对应事件时执行；跨流程通用逻辑（统一办理人映射、通知、日志）请实现全局监听器 GlobalListener，并注册为容器 Bean</span>
    </div>
  </div>
</template>

<script setup name="ListenerConfig">
import { ref, computed } from 'vue'
import { Delete, InfoFilled } from '@element-plus/icons-vue'
import { listenerList } from '@/api/flow/definition'

const props = defineProps({
  /** 监听器行数组（宿主表单的 listenerRows，组件内原地增删改） */
  rows: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  /** 事件类型选项：服务任务节点无分派事件，由宿主传入裁剪后的选项 */
  types: {
    type: Array,
    default: () => ([
      { label: '开始', value: 'start' },
      { label: '分派', value: 'assignment' },
      { label: '完成', value: 'finish' },
      { label: '创建', value: 'create' }
    ])
  },
  /** 行校验在宿主 el-form 模型中的路径前缀 */
  prefix: { type: String, default: 'listenerRows' }
})

const isMobile = computed(() => {
  if (typeof window === 'undefined') return false;
  return window.innerWidth <= 768;
})

// 行内必填校验：与宿主 el-form 联动（el-form-item 跨组件注入祖先表单上下文）
const rowRules = {
  listenerType: [{ required: true, message: '监听器类型不能为空', trigger: 'change' }],
  listenerPath: [{ required: true, message: '监听器路径不能为空', trigger: ['change', 'blur'] }]
}

// 监听器注册列表（ListenerListService）：模块级缓存，多面板共用一次请求
let registryPromise = null
const registeredListeners = ref([])
function loadRegistry() {
  if (!registryPromise) {
    registryPromise = listenerList()
      .then(res => (res && res.code === 200 && res.data) ? res.data : [])
      .catch(() => [])
  }
  registryPromise.then(list => { registeredListeners.value = list || [] })
}
loadRegistry()

// 增加行
function addRow() {
  props.rows.push({ listenerType: '', listenerPath: '' })
}

// 删除行
function removeRow(index) {
  props.rows.splice(index, 1)
}

// 路径变化时按注册项级联类型；手动输入或候选未声明类型时保持已选类型
function handlePathChange(path, row) {
  if (!path) {
    row.listenerType = '';
    return;
  }
  const matched = registeredListeners.value.find(item => item.path === path);
  if (matched && matched.type) {
    row.listenerType = matched.type;
  }
}
</script>

<style scoped lang="scss">
.listener-config {
  width: 100%;

  :deep(.el-table .el-form-item) {
    margin-bottom: 0;
  }
}

/* 增加行按钮：虚线主色（与 baseInfo / 各面板统一） */
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

/* 层级说明：节点级 vs 全局级 */
.listener-scope-tip {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--wf-primary-lighter, #f0f7ff);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--wf-text-secondary, #606266);

  .el-icon {
    margin-top: 3px;
    color: var(--wf-primary, #409eff);
    flex-shrink: 0;
  }

  html.dark & {
    background: rgba(64, 158, 255, 0.08);
    color: var(--wf-text-secondary, #909399);
  }
}
</style>
