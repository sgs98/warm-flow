<template>
  <div class="task-action-form-wrap" :class="{ compact }">
    <el-form class="task-action-form" :label-position="compact ? 'top' : 'right'" :label-width="compact ? undefined : '120px'">
      <el-form-item v-if="!submitOnly" :label="compact ? '办理动作' : '动作'">
        <div class="action-pills">
          <button type="button" :class="{ active: action === 'pass' }" @click="action = 'pass'">通过</button>
          <button v-if="canBack" type="button" :class="{ active: action === 'reject' }" @click="action = 'reject'">退回</button>
          <button v-if="can('transfer')" type="button" :class="{ active: action === 'transfer' }" @click="action = 'transfer'">转办</button>
          <button v-if="can('trust')" type="button" :class="{ active: action === 'depute' }" @click="action = 'depute'">委派</button>
          <button v-if="can('addSign')" type="button" :class="{ active: action === 'add' }" @click="action = 'add'">加签</button>
          <button v-if="can('subSign')" type="button" :class="{ active: action === 'reduction' }" @click="action = 'reduction'">减签</button>
        </div>
      </el-form-item>

      <el-form-item v-if="needMessage" :label="submitOnly ? '提交意见' : '办理意见'">
        <el-input v-model="message" type="textarea" :rows="compact ? 4 : 3" :placeholder="submitOnly ? '请输入提交意见' : '请输入办理意见'" />
      </el-form-item>

      <el-form-item v-if="submitOnly" label="流程变量">
        <div class="variables-editor">
          <div v-for="(item, index) in variableRows" :key="item.id" class="variable-row">
            <el-input v-model="item.key" placeholder="变量名" />
            <el-input v-model="item.value" placeholder="变量值" />
            <el-button link type="danger" @click="removeVariable(index)">删除</el-button>
          </div>
          <el-button class="add-row-btn" type="primary" plain size="small" @click="addVariable">+ 添加变量</el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="needNextHandler" label="下一步办理人">
        <div v-for="node in nextNodes" :key="node.nodeCode" class="next-handler-item">
          <span class="next-handler-node">{{ node.nodeName }}</span>
          <el-select
            v-model="selectedNextHandlerMap[node.nodeCode]"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            style="flex: 1"
            :placeholder="node.selectableUsers.length ? '请选择办理人' : '该节点未配置可选办理人'"
          >
            <el-option
              v-for="u in node.selectableUsers"
              :key="u.userName"
              :label="`${u.realName}(${u.userName})`"
              :value="u.userName"
            />
          </el-select>
        </div>
      </el-form-item>

      <el-form-item v-if="needBackNode" label="退回节点">
        <el-select v-model="selectedBackNode" style="width: 100%" placeholder="请选择退回节点">
          <el-option v-for="node in backNodes" :key="node.nodeCode" :label="node.nodeName" :value="node.nodeCode" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="needHandler" :label="multipleHandler ? '办理人(可多选)' : '转给'">
        <el-select
          v-model="selected"
          :multiple="multipleHandler"
          :collapse-tags="multipleHandler"
          :collapse-tags-tooltip="multipleHandler"
          :max-collapse-tags="2"
          :loading="['add', 'reduction'].includes(action) && signatureHandlersLoading"
          style="width: 100%"
          :placeholder="action === 'add' ? '请选择加签办理人' : action === 'reduction' ? '请选择要减签的办理人' : '请选择 demo 用户'"
        >
          <el-option
            v-for="u in action === 'add' ? addHandlerOptions : action === 'reduction' ? reductionHandlerOptions : approvers"
            :key="u.userName"
            :label="`${u.realName}(${u.userName})`"
            :value="u.userName"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="needCopyUsers" label="抄送人">
        <el-select v-model="selectedCopyUsers" multiple style="width: 100%" placeholder="请选择抄送人，可不选">
          <el-option v-for="u in approvers" :key="u.userName" :label="`${u.realName}(${u.userName})`" :value="u.userName" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="task-action-footer" :class="{ compact }">
      <el-button v-if="!compact" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="submitting" :class="{ 'is-block': compact }" @click="submit">
        {{ submitOnly ? '提交申请' : submitLabel }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../../stores/session'
import { httpGet, httpPost, type DemoUser } from '../../api/http'

export interface TodoTask {
  /** 待办任务主键。 */
  id: string
  /** 流程实例主键。 */
  instanceId: string
  /** 待办节点名称。 */
  nodeName: string
  /** 流程变量。 */
  variables?: Record<string, unknown>
  /** 当前任务办理人用户名。 */
  assignees?: string[]
}

interface ButtonPermission {
  code: string
  label: string
  show: boolean
}

interface TaskNode {
  nodeCode: string
  nodeName: string
  nodeType: number
  permissionFlags: string[]
  selectableUsers: DemoUser[]
}

const props = defineProps<{ task: TodoTask | null; submitOnly?: boolean; compact?: boolean }>()
const emit = defineEmits<{ (e: 'saved'): void; (e: 'cancel'): void }>()
const session = useSessionStore()

const action = ref<'pass' | 'reject' | 'transfer' | 'depute' | 'add' | 'reduction'>('pass')
const message = ref('')
const selected = ref<string | string[]>([])
const selectedNextHandlerMap = ref<Record<string, string[]>>({})
const selectedBackNode = ref('')
const selectedCopyUsers = ref<string[]>([])
const buttonPermissions = ref<ButtonPermission[]>([])
const approvers = ref<DemoUser[]>([])
const nextNodes = ref<TaskNode[]>([])
const backNodes = ref<TaskNode[]>([])
const addHandlerOptions = ref<DemoUser[]>([])
const reductionHandlerOptions = ref<DemoUser[]>([])
const signatureHandlersLoading = ref(false)
const submitting = ref(false)
const variableRows = ref<{ id: number; key: string; value: string }[]>([])

const submitLabel = computed(() => {
  if (action.value === 'reject') return '确认退回'
  if (action.value === 'transfer') return '确认转办'
  if (action.value === 'depute') return '确认委派'
  if (action.value === 'add') return '确认加签'
  if (action.value === 'reduction') return '确认减签'
  return '提交办理'
})

const needMessage = computed(() => action.value === 'pass' || action.value === 'reject')
const needHandler = computed(() => ['transfer', 'depute', 'add', 'reduction'].includes(action.value))
const multipleHandler = computed(() => action.value === 'add' || action.value === 'reduction')
const needNextHandler = computed(() =>
  action.value === 'pass' && can('pop') && nextNodes.value.length > 0)
const needBackNode = computed(() => action.value === 'reject')
const needCopyUsers = computed(() =>
  !props.submitOnly && can('copy') && ['pass', 'reject'].includes(action.value))
const canBack = computed(() => can('back') && backNodes.value.length > 0)

function can(code: string): boolean {
  return buttonPermissions.value.some((item) => item.code === code && item.show)
}

/** 重置表单并加载当前待办的按钮权限、下一节点和退回节点。 */
async function init() {
  const task = props.task
  if (!task) return
  action.value = 'pass'
  message.value = ''
  selected.value = []
  selectedNextHandlerMap.value = {}
  selectedBackNode.value = ''
  selectedCopyUsers.value = []
  variableRows.value = props.submitOnly
    ? Object.entries(task.variables || {}).map(([key, value], index) => ({
      id: Date.now() + index,
      key,
      value: value == null ? '' : String(value),
    }))
    : []
  buttonPermissions.value = []
  nextNodes.value = []
  backNodes.value = []
  addHandlerOptions.value = []
  reductionHandlerOptions.value = []
  signatureHandlersLoading.value = false
  const [permissions, users] = await Promise.all([
    httpGet<ButtonPermission[]>(`/tasks/${task.id}/button-permissions`),
    httpGet<DemoUser[]>('/users').catch(() => []),
  ])
  buttonPermissions.value = permissions
  approvers.value = users
  const next = await httpGet<TaskNode[]>(`/tasks/${task.id}/next-nodes`)
  nextNodes.value = next
  if (!props.submitOnly) {
    const back = await httpGet<TaskNode[]>(`/tasks/${task.id}/back-nodes`)
    backNodes.value = back
    selectedBackNode.value = back[0]?.nodeCode || ''
  }
  action.value = 'pass'
}

watch(() => props.task?.id, (id) => {
  if (id) init()
}, { immediate: true })

watch(action, () => {
  selected.value = []
  if (action.value === 'add' || action.value === 'reduction') {
    loadSignatureHandlers()
  } else {
    addHandlerOptions.value = []
    reductionHandlerOptions.value = []
    signatureHandlersLoading.value = false
  }
})

async function loadSignatureHandlers() {
  const task = props.task
  if (!task?.instanceId) return
  const actionWhenRequested = action.value
  signatureHandlersLoading.value = true
  const path = actionWhenRequested === 'add' ? 'add-signature-handlers' : 'reduction-signature-handlers'
  const handlers = await httpGet<DemoUser[]>(`/tasks/instances/${task.instanceId}/${path}`).catch(() => [])
  if (action.value === actionWhenRequested) {
    if (actionWhenRequested === 'add') {
      addHandlerOptions.value = handlers
    } else {
      reductionHandlerOptions.value = handlers
    }
  }
  signatureHandlersLoading.value = false
}

const selectedHandlers = computed(() => Array.isArray(selected.value)
  ? selected.value : selected.value ? [selected.value] : [])

function addVariable() {
  variableRows.value.push({ id: Date.now() + variableRows.value.length, key: '', value: '' })
}

function removeVariable(index: number) {
  variableRows.value.splice(index, 1)
}

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

async function submit() {
  const task = props.task
  if (!task) return
  if (needHandler.value && (!selectedHandlers.value.length
    || (!multipleHandler.value && selectedHandlers.value.length > 1))) {
    ElMessage.warning(multipleHandler.value ? '请选择至少一位办理人' : '请选择一位办理人')
    return
  }
  if (action.value === 'reduction' && selectedHandlers.value.length >= reductionHandlerOptions.value.length) {
    ElMessage.warning('减签后至少保留一位办理人')
    return
  }
  if (needNextHandler.value) {
    const missingNode = nextNodes.value.find(
      node => !selectedNextHandlerMap.value[node.nodeCode]?.length)
    if (missingNode) {
      ElMessage.warning(`请选择【${missingNode.nodeName}】办理人`)
      return
    }
    const emptyNode = nextNodes.value.find(node => node.selectableUsers.length === 0)
    if (emptyNode) {
      ElMessage.warning(`【${emptyNode.nodeName}】未配置可选办理人`)
      return
    }
  }
  if (needBackNode.value && !selectedBackNode.value) {
    ElMessage.warning('请选择退回节点')
    return
  }
  const variables = props.submitOnly ? collectVariables() : undefined
  if (props.submitOnly && variableRows.value.some(item => item.key.trim() || item.value.trim()) && !variables) return
  submitting.value = true
  try {
    const a = action.value
    const id = task.id
    const baseBody = { taskId: id, user: session.currentUser }
    if (a === 'pass') {
      await httpPost('/tasks/pass', {
        ...baseBody,
        message: message.value,
        variables,
        nextHandlerMap: needNextHandler.value ? selectedNextHandlerMap.value : undefined,
        copyUsers: needCopyUsers.value ? selectedCopyUsers.value : undefined,
      })
    } else if (a === 'reject') {
      await httpPost('/tasks/reject', {
        ...baseBody,
        message: message.value,
        nodeCode: selectedBackNode.value,
        copyUsers: needCopyUsers.value ? selectedCopyUsers.value : undefined,
      })
    } else if (a === 'transfer' || a === 'depute') {
      await httpPost(`/tasks/${a}`, { ...baseBody, nextHandlers: selectedHandlers.value })
    } else if (a === 'add') {
      await httpPost('/tasks/add-signature', { ...baseBody, addHandlers: selectedHandlers.value })
    } else {
      await httpPost('/tasks/reduction-signature', { ...baseBody, reductionHandlers: selectedHandlers.value })
    }
    ElMessage.success('操作成功')
    emit('saved')
  } catch (e) {
    // 错误提示已由 http 层统一处理
  } finally {
    submitting.value = false
  }
}

defineExpose({ init })
</script>

<style scoped>
.task-action-form-wrap {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}

.task-action-form-wrap.compact {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.task-action-form-wrap.compact .task-action-form {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.task-action-form :deep(.el-form-item__label) {
  line-height: 20px;
  white-space: normal;
}

.task-action-form :deep(.el-form-item__content) {
  min-width: 0;
}

.task-action-form :deep(.el-select__selection) {
  min-width: 0;
  overflow: hidden;
}

.action-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-pills button {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--wf-border, #e6ebf4);
  border-radius: 8px;
  background: #fff;
  color: var(--wf-text, #1e2633);
  cursor: pointer;
  font-size: 12px;
}

.action-pills button.active {
  border-color: var(--wf-primary, #409eff);
  background: var(--wf-primary-soft, #ecf5ff);
  color: var(--wf-primary, #409eff);
  font-weight: 600;
}

.next-handler-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.next-handler-item + .next-handler-item {
  margin-top: 8px;
}

.next-handler-node {
  flex: 0 0 72px;
  min-width: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

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

.task-action-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}

.task-action-footer.compact {
  flex-shrink: 0;
  margin: 0 -18px;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--wf-border, #e6ebf4);
  background: #fff;
}

.task-action-footer .is-block {
  width: 100%;
}
</style>
