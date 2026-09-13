<template>
  <div class="page-card">
    <div class="table-toolbar">
      <el-input v-model="businessId" placeholder="业务ID" clearable style="width: 180px" @keyup.enter="search" />
      <el-select v-model="definitionId" placeholder="流程" clearable filterable style="width: 200px">
        <el-option v-for="d in defs" :key="d.id" :label="`${d.flowName}(${d.flowCode})`" :value="d.id" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1" />
      <el-button type="primary" @click="openStartDialog">+ 发起实例</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="实例ID" width="160" />
      <el-table-column label="流程名称" min-width="140">
        <template #default="{ row }">{{ row.flowName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="businessId" label="业务ID" width="120" />
      <el-table-column prop="nodeName" label="当前节点" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.businessStatus, row.businessStatusName || row.flowStatusName)">
            {{ row.businessStatusName || row.flowStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="发起人" width="100" />
      <el-table-column prop="createTime" label="发起时间" width="170" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="canContinue(row)" link type="primary" @click="continueDraft(row)">
            {{ row.businessStatus === 'draft' ? '继续办理' : '重新提交' }}
          </el-button>
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
      :total="total" v-model:current-page="pageNum" @current-change="load" />

    <el-dialog v-model="dialogVisible" title="发起实例" width="560px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="流程" required>
          <el-select v-model="startDefId" filterable placeholder="请选择已发布流程" style="width: 100%">
            <el-option
              v-for="d in defs"
              :key="d.id"
              :label="`${d.flowName}(${d.flowCode})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model="startBusinessId" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="流程变量">
          <div class="variables-editor">
            <div v-for="(item, index) in variableRows" :key="item.id" class="variable-row">
              <el-input v-model="item.key" placeholder="变量名" />
              <el-input v-model="item.value" placeholder="变量值" />
              <el-button link type="danger" @click="removeVariable(index)">删除</el-button>
            </div>
            <el-button class="add-row-btn" type="primary" plain size="small" @click="addVariable">+ 添加变量</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="doStart">发起</el-button>
      </template>
    </el-dialog>

      <TaskActionDialog
      v-model="taskDialogVisible"
      :task="startTask"
      submit-only
      @saved="handleTaskSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import TaskActionDialog from '../../components/tasks/TaskActionDialog.vue'
import { httpDelete, httpGet, httpPost, type PageVo } from '../../api/http'
import { statusTagType } from '../../utils/status'

interface InstanceRow {
  /** 流程实例主键。 */
  id: string
  /** 流程名称。 */
  flowName: string
  /** 业务主键。 */
  businessId: string
  /** 流程实例变量。 */
  variables?: Record<string, unknown>
  /** 当前待办任务主键。 */
  taskId?: string
  /** 当前节点名称。 */
  nodeName: string
  /** 流程状态名称。 */
  flowStatusName: string
  /** 业务状态编码。 */
  businessStatus: string
  /** 业务状态名称。 */
  businessStatusName: string
  /** 发起人。 */
  createBy: string
  /** 发起时间。 */
  createTime: string
}

interface DefinitionOption {
  /** 流程定义主键。 */
  id: string
  /** 流程编码。 */
  flowCode: string
  /** 流程名称。 */
  flowName: string
}

interface StartInstanceResult {
  /** 流程实例主键。 */
  instanceId: string
  /** 发起后的首个待办任务主键。 */
  taskId?: string
  /** 发起后的首个待办节点名称。 */
  nodeName?: string
}

const rows = ref<InstanceRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const defs = ref<DefinitionOption[]>([])
const definitionId = ref<string | undefined>()
const businessId = ref('')
const dialogVisible = ref(false)
const startDefId = ref<string | undefined>()
const startBusinessId = ref('')
const variableRows = ref<{ id: number; key: string; value: string }[]>([])
const starting = ref(false)
const taskDialogVisible = ref(false)
const startTask = ref<{
  id: string
  instanceId: string
  nodeName: string
  variables?: Record<string, unknown>
} | null>(null)
const router = useRouter()

/** 按当前筛选条件加载流程实例分页数据。 */
async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<InstanceRow>>('/instances', {
      pageNum: pageNum.value,
      pageSize: 10,
      definitionId: definitionId.value || undefined,
      businessId: businessId.value || undefined,
    })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 加载可发起的已发布流程。 */
async function loadDefs() {
  const res = await httpGet<PageVo<DefinitionOption>>('/definitions', {
    pageNum: 1,
    pageSize: 100,
    isPublish: 1,
  })
  defs.value = res.list
}

/** 重置页码并发起查询。 */
function search() {
  pageNum.value = 1
  load()
}

/** 清空筛选条件并回到第一页。 */
function reset() {
  definitionId.value = undefined
  businessId.value = ''
  search()
}

/** 跳转到流程实例详情。 */
function goDetail(row: InstanceRow) {
  router.push(`/instances/${row.id}`)
}

/** 打开发起弹窗并清理上次未提交的输入。 */
function openStartDialog() {
  startDefId.value = undefined
  startBusinessId.value = ''
  variableRows.value = []
  dialogVisible.value = true
}

/** 新增一行流程变量。 */
function addVariable() {
  variableRows.value.push({ id: Date.now() + variableRows.value.length, key: '', value: '' })
}

/** 删除一行流程变量。 */
function removeVariable(index: number) {
  variableRows.value.splice(index, 1)
}

/** 从实例列表重新打开草稿的当前待办。 */
function continueDraft(row: InstanceRow) {
  if (!row.taskId) return
  startTask.value = {
    id: row.taskId,
    instanceId: row.id,
    nodeName: row.nodeName || '首节点',
    variables: row.variables,
  }
  taskDialogVisible.value = true
}

/** 退回或撤回到申请节点后允许发起人继续提交。 */
function canContinue(row: InstanceRow) {
  return Boolean(row.taskId && ['draft', 'back', 'cancel'].includes(row.businessStatus))
}

/** 将变量编辑行转换为引擎需要的 Map。 */
function collectVariables(): Record<string, string> | undefined {
  const rows = variableRows.value.filter(item => item.key.trim() || item.value.trim())
  const variables: Record<string, string> = {}
  for (const item of rows) {
    const key = item.key.trim()
    if (!key) {
      ElMessage.warning('流程变量名不能为空')
      return undefined
    }
    if (Object.prototype.hasOwnProperty.call(variables, key)) {
      ElMessage.warning(`流程变量【${key}】重复`)
      return undefined
    }
    variables[key] = item.value
  }
  return Object.keys(variables).length ? variables : undefined
}

/** 确认并物理删除流程实例及其全部相关流程数据。 */
async function remove(row: InstanceRow) {
  await ElMessageBox.confirm(
    `确认物理删除实例【${row.businessId}】？该操作会同时删除待办、历史任务和办理人数据，且不可恢复。`,
    '危险操作',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await httpDelete(`/instances/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}

/** 启动流程；若返回首任务，则打开仅提交模式的申请弹窗。 */
async function doStart() {
  const def = defs.value.find((d) => d.id === startDefId.value)
  if (!def) {
    ElMessage.warning('请选择流程')
    return
  }
  const variables = collectVariables()
  if (variableRows.value.some(item => item.key.trim() || item.value.trim()) && !variables) return
  starting.value = true
  try {
    const res = await httpPost<StartInstanceResult>('/instances', {
      flowCode: def.flowCode,
      businessId: startBusinessId.value.trim() || `BIZ-${Date.now()}`,
      variables,
    })
    dialogVisible.value = false
    startDefId.value = undefined
    startBusinessId.value = ''
    variableRows.value = []
    pageNum.value = 1
    load()
    if (res.taskId) {
      startTask.value = {
        id: res.taskId,
        instanceId: res.instanceId,
        nodeName: res.nodeName || '首节点',
        variables,
      }
      taskDialogVisible.value = true
      ElMessage.success('流程已启动，请办理首节点')
    } else {
      startTask.value = null
      ElMessage.success(`实例发起成功：${res.instanceId}`)
    }
  } catch (e) {
    // 错误提示由 http 层统一处理
  } finally {
    starting.value = false
  }
}

/** 首节点提交完成后清理临时任务并刷新实例列表。 */
function handleTaskSaved() {
  startTask.value = null
  pageNum.value = 1
  load()
}

onMounted(() => {
  loadDefs()
  load()
})
</script>

<style scoped>
.variables-editor {
  width: 100%;
}

.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.variable-row .el-input {
  min-width: 0;
}
</style>
