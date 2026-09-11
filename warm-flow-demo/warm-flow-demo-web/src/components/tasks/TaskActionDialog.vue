<template>
  <el-dialog
    :model-value="modelValue"
    :title="(submitOnly ? '提交申请 - ' : '任务办理 - ') + (task?.nodeName || '')"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="init"
  >
    <el-form label-width="90px">
      <el-form-item v-if="!submitOnly" label="动作">
        <el-radio-group v-model="action">
            <el-radio-button label="pass">通过</el-radio-button>
            <el-radio-button v-if="canBack" label="reject">退回</el-radio-button>
            <el-radio-button v-if="can('transfer')" label="transfer">转办</el-radio-button>
            <el-radio-button v-if="can('trust')" label="depute">委派</el-radio-button>
            <el-radio-button v-if="can('addSign')" label="add">加签</el-radio-button>
            <el-radio-button v-if="can('subSign')" label="reduction">减签</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="needMessage" :label="submitOnly ? '提交意见' : '办理意见'">
        <el-input v-model="message" type="textarea" :rows="3" :placeholder="submitOnly ? '请输入提交意见' : '请输入办理意见'" />
      </el-form-item>

      <el-form-item v-if="needNextHandler" label="下一步办理人">
        <div v-for="node in nextNodes" :key="node.nodeCode" class="next-handler-item">
          <span class="next-handler-node">{{ node.nodeName }}</span>
          <el-select
            v-model="selectedNextHandlerMap[node.nodeCode]"
            multiple
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
          style="width: 100%"
          placeholder="请选择 demo 用户"
        >
          <el-option v-for="u in approvers" :key="u.userName" :label="`${u.realName}(${u.userName})`" :value="u.userName" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="needCopyUsers" label="抄送人">
        <el-select v-model="selectedCopyUsers" multiple style="width: 100%" placeholder="请选择抄送人，可不选">
          <el-option v-for="u in approvers" :key="u.userName" :label="`${u.realName}(${u.userName})`" :value="u.userName" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../../stores/session'
import { httpGet, httpPost, type DemoUser } from '../../api/http'

export interface TodoTask {
  /** 待办任务主键。 */
  id: string
  /** 待办节点名称。 */
  nodeName: string
}

interface ButtonPermission {
  /** 按钮权限编码。 */
  code: string
  /** 按钮权限名称。 */
  label: string
  /** 是否显示。 */
  show: boolean
}

interface TaskNode {
  /** 节点编码。 */
  nodeCode: string
  /** 节点名称。 */
  nodeName: string
  /** 节点类型。 */
  nodeType: number
  /** 节点默认办理权限标识。 */
  permissionFlags: string[]
  /** 节点默认办理权限对应的可选用户。 */
  selectableUsers: DemoUser[]
}

const props = defineProps<{ modelValue: boolean; task: TodoTask | null; submitOnly?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()
const session = useSessionStore()

const action = ref<'pass' | 'reject' | 'transfer' | 'depute' | 'add' | 'reduction'>('pass')
const message = ref('')
const selected = ref<string[]>([])
const selectedNextHandlerMap = ref<Record<string, string[]>>({})
const selectedBackNode = ref('')
const selectedCopyUsers = ref<string[]>([])
const buttonPermissions = ref<ButtonPermission[]>([])
const approvers = ref<DemoUser[]>([])
const nextNodes = ref<TaskNode[]>([])
const backNodes = ref<TaskNode[]>([])
const submitting = ref(false)

const needMessage = computed(() => action.value === 'pass' || action.value === 'reject')
/** 转办、委派、加签和减签需要先选择目标办理人。 */
const needHandler = computed(() => ['transfer', 'depute', 'add', 'reduction'].includes(action.value))
/** 加签和减签允许选择多个办理人。 */
const multipleHandler = computed(() => action.value === 'add' || action.value === 'reduction')
/** 当前节点开启弹窗选人且存在下一审批节点时，通过/提交申请需要选择下一步办理人。 */
const needNextHandler = computed(() =>
  action.value === 'pass' && can('pop') && nextNodes.value.length > 0)
/** 退回必须选择目标节点，避免依赖连线默认退回语义。 */
const needBackNode = computed(() => action.value === 'reject')
/** 通过和退回支持选择抄送人。 */
const needCopyUsers = computed(() =>
  !props.submitOnly && can('copy') && ['pass', 'reject'].includes(action.value))
/** 仅存在可退回节点时展示退回按钮。 */
const canBack = computed(() => can('back') && backNodes.value.length > 0)

/** 判断当前待办节点是否开启指定按钮；旧接口未返回权限时沿用默认行为。 */
function can(code: string): boolean {
  return buttonPermissions.value.some((item) => item.code === code && item.show)
}

/** 初始化弹窗：重置表单、加载按钮权限和可选办理人。 */
async function init() {
  const task = props.task
  if (!task) return
  action.value = 'pass'
  message.value = ''
  selected.value = []
  selectedNextHandlerMap.value = {}
  selectedBackNode.value = ''
  selectedCopyUsers.value = []
  buttonPermissions.value = []
  nextNodes.value = []
  backNodes.value = []
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

/** 提交当前办理动作，并根据动作类型调用对应任务接口。 */
async function submit() {
  const task = props.task
  if (!task) return
  if (needMessage.value && !message.value) {
    ElMessage.warning('请填写办理意见')
    return
  }
  if (needHandler.value && (!selected.value.length || (!multipleHandler.value && selected.value.length > 1))) {
    ElMessage.warning(multipleHandler.value ? '请选择至少一位办理人' : '请选择一位办理人')
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
  submitting.value = true
  try {
    const a = action.value
    const id = task.id
    const baseBody = { taskId: id, user: session.currentUser }
    if (a === 'pass') {
      await httpPost('/tasks/pass', {
        ...baseBody,
        message: message.value,
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
      await httpPost(`/tasks/${a}`, { ...baseBody, nextHandlers: selected.value })
    } else if (a === 'add') {
      await httpPost('/tasks/add-signature', { ...baseBody, addHandlers: selected.value })
    } else {
      await httpPost('/tasks/reduction-signature', { ...baseBody, reductionHandlers: selected.value })
    }
    ElMessage.success('操作成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    // 错误提示已由 http 层统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
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
  min-width: 72px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
