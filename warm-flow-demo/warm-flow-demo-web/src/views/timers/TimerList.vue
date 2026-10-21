<template>
  <div class="page-card">
    <!-- 状态计数：点击按该状态过滤，再点取消；计数与列表共用筛选条件 -->
    <div class="stat-row">
      <button
        v-for="card in statCards"
        :key="card.status"
        type="button"
        class="stat-chip"
        :class="{ active: filters.statuses.includes(card.status) }"
        @click="toggleStatus(card.status)"
      >
        <span class="stat-label">{{ card.label }}</span>
        <b class="stat-value">{{ card.value }}</b>
      </button>
      <span class="stat-scope">统计范围：{{ includeNonTimer ? '全部事件类型' : '定时类事件' }}</span>
      <div class="toolbar-spacer" />
      <el-select v-model="refreshSeconds" style="width: 150px" @change="setupAutoRefresh">
        <el-option :value="0" label="不自动刷新" />
        <el-option :value="5" label="每 5 秒刷新" />
        <el-option :value="10" label="每 10 秒刷新" />
        <el-option :value="30" label="每 30 秒刷新" />
      </el-select>
      <el-button :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <div class="table-toolbar">
      <el-select v-model="filters.statuses" multiple collapse-tags clearable placeholder="状态"
        style="width: 200px" @change="search">
        <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="filters.eventTypes" multiple collapse-tags clearable placeholder="事件类型（留空按范围）"
        style="width: 230px" @change="search">
        <el-option-group v-for="g in EVENT_TYPE_GROUPS" :key="g.label" :label="g.label">
          <el-option v-for="t in g.items" :key="t.value" :label="t.label" :value="t.value" />
        </el-option-group>
      </el-select>
      <el-input v-model="filters.flowCode" placeholder="流程编码" clearable style="width: 160px"
        @keyup.enter="search" />
      <el-input v-model="filters.businessId" placeholder="业务ID" clearable style="width: 150px"
        @keyup.enter="search" />
      <el-input v-model="filters.nodeCode" placeholder="节点编码" clearable style="width: 150px"
        @keyup.enter="search" />
      <el-input v-if="showEventName" v-model="filters.eventName" :placeholder="eventNamePlaceholder" clearable
        style="width: 170px" @keyup.enter="search" />
      <el-switch v-model="includeNonTimer" active-text="含消息/错误类事件" @change="search" />
      <el-date-picker v-model="filters.triggerRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
        range-separator="~" start-placeholder="下次触发 ≥" end-placeholder="下次触发 ≤" style="width: 330px" />
      <el-date-picker v-model="filters.createRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
        range-separator="~" start-placeholder="创建 ≥" end-placeholder="创建 ≤" style="width: 300px" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <template #empty>
        <el-empty :description="emptyText" :image-size="72" />
      </template>
      <el-table-column label="下次触发时间" width="165">
        <template #default="{ row }">
          <span :class="{ overdue: isOverdue(row) }" :title="isOverdue(row) ? '已过触发时间仍在等待' : ''">
            {{ row.nextTriggerAt || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ row.statusName || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="事件类型" width="108">
        <template #default="{ row }">
          <span :title="row.eventType">{{ row.eventTypeName || row.eventType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="流程" min-width="140">
        <template #default="{ row }">{{ row.flowName || '-' }}</template>
      </el-table-column>
      <el-table-column label="实例 / 业务ID" min-width="170">
        <template #default="{ row }">
          <div>{{ row.businessId || '-' }}</div>
          <div class="cell-sub">{{ row.instanceId || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="节点" min-width="110">
        <template #default="{ row }">{{ row.nodeCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="事件名称" min-width="120">
        <template #default="{ row }">
          <span v-if="row.eventName">{{ row.eventName }}</span>
          <span v-else-if="isErrorWait(row)" class="cell-sub">通配</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="附加任务" width="150">
        <template #default="{ row }">{{ row.attachedTaskId || '-' }}</template>
      </el-table-column>
      <el-table-column label="重试" width="80">
        <template #default="{ row }">
          {{ row.retryCount == null ? '-' : `${row.retryCount}/${row.maxRetryCount ?? '-'}` }}
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160">
        <template #default="{ row }">
          <span :title="row.lastError || ''">{{ row.lastError || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="165">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" :disabled="!row.instanceId" @click="viewInstance(row)">查看实例</el-button>
          <el-button v-if="isTimerStart(row)" link type="success" @click="openTrigger(row)">立即触发</el-button>
          <el-button v-if="isMessageWait(row)" link type="warning" @click="openMessage(row)">触发消息</el-button>
          <el-button v-if="isErrorWait(row)" link type="danger" @click="openError(row)">抛错</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination layout="total, prev, pager, next"
      :total="total" v-model:current-page="pageNum" @current-change="load" />

    <!-- 等待记录详情 -->
    <el-drawer v-model="detailVisible" title="等待记录详情" size="560px">
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item label="记录ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          {{ detailRow.eventTypeName || detailRow.eventType }}（{{ detailRow.eventType }}）
        </el-descriptions-item>
        <el-descriptions-item label="事件名称">{{ detailRow.eventName || '（未设置）' }}</el-descriptions-item>
        <el-descriptions-item label="等待状态">
          {{ detailRow.statusName || detailRow.status }}（{{ detailRow.status }}）
        </el-descriptions-item>
        <el-descriptions-item label="流程">{{ detailRow.flowName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流程定义ID">{{ detailRow.definitionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实例 / 业务ID">
          {{ detailRow.instanceId || '-' }} / {{ detailRow.businessId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="事件节点">{{ detailRow.nodeCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="附加任务">{{ detailRow.attachedTaskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="定时策略">
          {{ detailRow.triggerType || '-' }} / {{ detailRow.triggerInterval || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="下次触发">{{ detailRow.nextTriggerAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时区">{{ detailRow.timezone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发次数">
          {{ detailRow.repeatCount == null ? '-' : `${detailRow.repeatCount}/${detailRow.maxRepeatCount ?? '-'}` }}
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">
          {{ detailRow.retryCount == null ? '-' : `${detailRow.retryCount}/${detailRow.maxRetryCount ?? '-'}` }}
        </el-descriptions-item>
        <el-descriptions-item label="下次重试">{{ detailRow.retryAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="抢占持有者">{{ detailRow.lockOwner || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租约到期">{{ detailRow.lockUntil || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本号">{{ detailRow.version ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ detailRow.lastError || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建 / 更新">
          {{ detailRow.createTime || '-' }} / {{ detailRow.updateTime || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="detailRow?.payload" class="payload-block">
        <div class="payload-title">事件载荷</div>
        <pre class="payload-body">{{ prettyPayload(detailRow.payload) }}</pre>
      </div>
    </el-drawer>

    <!-- 手动补发定时启动任务 -->
    <el-dialog v-model="triggerVisible" title="立即触发一次" width="540px" :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="任务">
          <span class="cell-sub">{{ triggerRow?.flowName || '-' }} / {{ triggerRow?.nodeCode || '-' }}</span>
        </el-form-item>
        <el-form-item label="下次触发">
          <span>{{ triggerRow?.nextTriggerAt || '-' }}</span>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model="triggerForm.businessId" clearable placeholder="留空自动生成（M任务ID_时间戳）" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">
        只额外发起一次实例，操作人记为当前登录用户。定时任务本身不会变动，
        <b>下次触发时间保持不变</b>，例如每天 9 点的任务提前补发后，当天 9 点仍会照常触发。
      </div>
      <template #footer>
        <el-button @click="triggerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTrigger">确认触发</el-button>
      </template>
    </el-dialog>

    <!-- 触发消息捕获 -->
    <el-dialog v-model="messageVisible" title="触发消息捕获" width="540px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="消息名称" required>
          <el-input v-model="messageForm.eventName" placeholder="匹配事件节点的事件名称" />
        </el-form-item>
        <el-form-item label="载荷 JSON">
          <el-input v-model="messageForm.payloadText" type="textarea" :rows="4"
            placeholder='留空表示无载荷，例如 {"riskLevel":"low"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="messageVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMessage">触发</el-button>
      </template>
    </el-dialog>

    <!-- 抛出流程错误 -->
    <el-dialog v-model="errorVisible" :title="errorDialogTitle" width="540px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="错误码">
          <el-input v-model="errorForm.errorCode" placeholder="留空表示通配，例如 PAY_FAILED" />
        </el-form-item>
        <el-form-item label="错误描述">
          <el-input v-model="errorForm.message" placeholder="仅用于日志与异常信息" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">
        错误经宿主 API 显式抛出：任务级报错只匹配附着在该任务上的错误边界，实例级报错路由到实例级错误捕获。
      </div>
      <template #footer>
        <el-button @click="errorVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitError">抛错</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getEventWaitSummary, pageEventWaits, triggerTimerStart } from '../../api/eventWaits'
import { throwInstanceError, throwTaskError, triggerInstanceMessage } from '../../api/events'
import { usePagedList } from '../../composables/usePagedList'
import type { EventWaitRow, EventWaitSummary } from '../../types/workflow'

const router = useRouter()

/** 等待状态选项，与引擎 EventCons 对齐。 */
const STATUS_OPTIONS = [
  { value: 'WAITING', label: '待触发' },
  { value: 'RETRY', label: '重试中' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'DEAD', label: '已死' },
  { value: 'CANCELLED', label: '已取消' },
]

/** 事件类型选项，与引擎 EventType 对齐；定时类在前。 */
const EVENT_TYPE_GROUPS = [
  {
    label: '定时类',
    items: [
      { value: 'TIMER_START', label: '定时启动' },
      { value: 'TIMER_CATCH', label: '定时捕获' },
      { value: 'TIMER_BOUNDARY', label: '定时边界' },
    ],
  },
  {
    label: '消息类',
    items: [
      { value: 'MESSAGE_START', label: '消息启动' },
      { value: 'MESSAGE_CATCH', label: '消息捕获' },
      { value: 'MESSAGE_BOUNDARY', label: '消息边界' },
      { value: 'MESSAGE_THROW', label: '消息抛出' },
      { value: 'MESSAGE_END', label: '消息结束' },
    ],
  },
  {
    label: '错误类',
    items: [
      { value: 'ERROR_CATCH', label: '错误捕获' },
      { value: 'ERROR_BOUNDARY', label: '错误边界' },
    ],
  },
  { label: '其他', items: [{ value: 'TERMINATE_END', label: '终止结束' }] },
]

const filters = reactive({
  statuses: [] as string[],
  eventTypes: [] as string[],
  flowCode: '',
  businessId: '',
  nodeCode: '',
  eventName: '',
  triggerRange: null as string[] | null,
  createRange: null as string[] | null,
})

/** 是否把消息类 / 错误类等待一并纳入（关闭时只看定时类，引擎默认）。 */
const includeNonTimer = ref(false)

/** 自动刷新间隔（秒），0 表示关闭。 */
const refreshSeconds = ref(0)

const summary = ref<EventWaitSummary>({ waiting: 0, retry: 0, running: 0, dead: 0, cancelled: 0 })

const detailVisible = ref(false)
const detailRow = ref<EventWaitRow | null>(null)

const messageVisible = ref(false)
const messageRow = ref<EventWaitRow | null>(null)
const messageForm = reactive({ eventName: '', payloadText: '' })

const errorVisible = ref(false)
const errorRow = ref<EventWaitRow | null>(null)
const errorForm = reactive({ errorCode: '', message: '' })

const triggerVisible = ref(false)
const triggerRow = ref<EventWaitRow | null>(null)
const triggerForm = reactive({ businessId: '' })

const submitting = ref(false)

const { rows, total, pageNum, loading, load } = usePagedList<EventWaitRow>(
  (num, size) => pageEventWaits({ ...buildParams(), pageNum: num, pageSize: size }),
  { immediate: false, pageSize: 20 },
)

const statCards = computed(() => [
  { status: 'WAITING', label: '待触发', value: summary.value.waiting },
  { status: 'RETRY', label: '重试中', value: summary.value.retry },
  { status: 'RUNNING', label: '执行中', value: summary.value.running },
  { status: 'DEAD', label: '已死', value: summary.value.dead },
  { status: 'CANCELLED', label: '已取消', value: summary.value.cancelled },
])

/** 消息名 / 错误码输入框只在查询范围含消息类或错误类事件时出现。 */
const showEventName = computed(() =>
  includeNonTimer.value
  || filters.eventTypes.some((t) => t.startsWith('MESSAGE_') || t.startsWith('ERROR_')),
)

const eventNamePlaceholder = computed(() =>
  filters.eventTypes.some((t) => t.startsWith('ERROR_')) ? '错误码' : '消息名称',
)

const emptyText = computed(() =>
  hasFilter() ? '当前筛选条件下没有等待记录，可放宽条件或重置后重试' : '暂无定时任务：发起含定时 / 消息 / 错误事件的流程后会在这里出现等待记录',
)

const errorDialogTitle = computed(() =>
  errorRow.value?.eventType === 'ERROR_BOUNDARY' && errorRow.value?.attachedTaskId
    ? '抛出流程错误（任务级）'
    : '抛出流程错误（实例级）',
)

/** 判断是否设置了除状态以外的筛选条件，仅用于空表文案。 */
function hasFilter() {
  return Boolean(
    filters.statuses.length || filters.eventTypes.length || filters.flowCode || filters.businessId
    || filters.nodeCode || filters.eventName || filters.triggerRange || filters.createRange || includeNonTimer.value,
  )
}

/** 组装引擎查询参数；空值一律不传，避免命中引擎的「条件已给但无匹配」语义。 */
function buildParams() {
  return {
    statuses: filters.statuses.length ? [...filters.statuses] : undefined,
    eventTypes: filters.eventTypes.length ? [...filters.eventTypes] : undefined,
    timerOnly: !includeNonTimer.value,
    flowCode: filters.flowCode || undefined,
    businessId: filters.businessId || undefined,
    nodeCode: filters.nodeCode || undefined,
    eventName: showEventName.value ? filters.eventName || undefined : undefined,
    nextTriggerAtFrom: filters.triggerRange?.[0] || undefined,
    nextTriggerAtTo: filters.triggerRange?.[1] || undefined,
    createTimeFrom: filters.createRange?.[0] || undefined,
    createTimeTo: filters.createRange?.[1] || undefined,
  }
}

/** 加载列表与状态计数（计数的状态条件由后端逐个覆盖）。 */
async function refreshAll() {
  const params = buildParams()
  const summaryParams = { ...params, statuses: undefined }
  await Promise.all([
    load(),
    getEventWaitSummary(summaryParams)
      .then((res) => { summary.value = res })
      .catch(() => undefined),
  ]).catch(() => undefined)
}

/** 条件变更后回到第一页查询。 */
function search() {
  pageNum.value = 1
  refreshAll()
}

/** 清空全部筛选条件。 */
function reset() {
  filters.statuses = []
  filters.eventTypes = []
  filters.flowCode = ''
  filters.businessId = ''
  filters.nodeCode = ''
  filters.eventName = ''
  filters.triggerRange = null
  filters.createRange = null
  includeNonTimer.value = false
  search()
}

/** 点击计数卡片切换对应状态过滤。 */
function toggleStatus(status: string) {
  const index = filters.statuses.indexOf(status)
  if (index >= 0) filters.statuses.splice(index, 1)
  else filters.statuses.push(status)
  search()
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'RUNNING') return 'success'
  if (status === 'WAITING' || status === 'RETRY') return 'warning'
  if (status === 'DEAD') return 'danger'
  return 'info'
}

function isMessageWait(row: EventWaitRow) {
  return row.eventType === 'MESSAGE_CATCH' || row.eventType === 'MESSAGE_BOUNDARY'
}

/** 定时启动任务：只有它支持手动补发一次（补发不推进等待记录）。 */
function isTimerStart(row: EventWaitRow) {
  return row.eventType === 'TIMER_START'
}

function isErrorWait(row: EventWaitRow) {
  return row.eventType === 'ERROR_CATCH' || row.eventType === 'ERROR_BOUNDARY'
}

/** 已过下次触发时间仍在等待，通常意味着定时扫描未开启。 */
function isOverdue(row: EventWaitRow) {
  if (!row.nextTriggerAt || row.status !== 'WAITING') return false
  return new Date(row.nextTriggerAt.replace(/-/g, '/')).getTime() < Date.now()
}

function prettyPayload(payload?: string) {
  if (!payload) return ''
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function openDetail(row: EventWaitRow) {
  detailRow.value = row
  detailVisible.value = true
}

function viewInstance(row: EventWaitRow) {
  if (row.instanceId) router.push(`/instances/${row.instanceId}`)
}

function openTrigger(row: EventWaitRow) {
  triggerRow.value = row
  triggerForm.businessId = ''
  triggerVisible.value = true
}

async function submitTrigger() {
  const row = triggerRow.value
  if (!row) return
  submitting.value = true
  try {
    const instanceId = await triggerTimerStart(row.id, {
      businessId: triggerForm.businessId.trim() || undefined,
    })
    ElMessage.success(`已发起一次实例 ${instanceId}`)
    triggerVisible.value = false
    refreshAll()
  } finally {
    submitting.value = false
  }
}

function openMessage(row: EventWaitRow) {
  messageRow.value = row
  messageForm.eventName = row.eventName || ''
  messageForm.payloadText = ''
  messageVisible.value = true
}

async function submitMessage() {
  const row = messageRow.value
  if (!row?.instanceId) return
  if (!messageForm.eventName) {
    ElMessage.warning('消息名称不能为空')
    return
  }
  let payload: Record<string, unknown> | undefined
  if (messageForm.payloadText.trim()) {
    try {
      payload = JSON.parse(messageForm.payloadText)
    } catch {
      ElMessage.error('载荷必须是合法 JSON')
      return
    }
  }
  submitting.value = true
  try {
    await triggerInstanceMessage(row.instanceId, { eventName: messageForm.eventName, payload })
    ElMessage.success('已触发消息捕获事件')
    messageVisible.value = false
    refreshAll()
  } finally {
    submitting.value = false
  }
}

function openError(row: EventWaitRow) {
  errorRow.value = row
  errorForm.errorCode = row.eventName || ''
  errorForm.message = ''
  errorVisible.value = true
}

async function submitError() {
  const row = errorRow.value
  if (!row) return
  submitting.value = true
  try {
    const payload = { errorCode: errorForm.errorCode || undefined, message: errorForm.message || undefined }
    if (row.eventType === 'ERROR_BOUNDARY' && row.attachedTaskId) {
      await throwTaskError(row.attachedTaskId, payload)
    } else if (row.instanceId) {
      await throwInstanceError(row.instanceId, payload)
    } else {
      ElMessage.warning('该等待记录缺少实例或附加任务，无法抛错')
      return
    }
    ElMessage.success('已抛出流程错误')
    errorVisible.value = false
    refreshAll()
  } finally {
    submitting.value = false
  }
}

let autoRefreshTimer: number | undefined

/** 按选择的间隔开启 / 关闭自动刷新。 */
function setupAutoRefresh() {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = undefined
  }
  if (refreshSeconds.value > 0) {
    autoRefreshTimer = window.setInterval(refreshAll, refreshSeconds.value * 1000)
  }
}

onMounted(refreshAll)

onUnmounted(() => {
  if (autoRefreshTimer) window.clearInterval(autoRefreshTimer)
})
</script>

<style scoped>
.stat-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.stat-chip {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-md);
  background: var(--wf-surface);
  color: var(--wf-text);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.stat-chip:hover {
  border-color: var(--wf-primary);
}

.stat-chip.active {
  border-color: var(--wf-primary);
  background: var(--wf-primary-soft);
  color: var(--wf-primary-strong);
}

.stat-label {
  font-size: 13px;
  color: var(--wf-text-muted);
}

.stat-chip.active .stat-label {
  color: var(--wf-primary-strong);
}

.stat-value {
  font-size: 18px;
  line-height: 1;
}

.stat-scope {
  font-size: 12px;
  color: var(--wf-text-muted);
}

.cell-sub {
  font-size: 12px;
  color: var(--wf-text-muted);
}

.overdue {
  color: var(--el-color-danger);
  font-weight: 600;
}

.payload-block {
  margin-top: 16px;
}

.payload-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--wf-text-muted);
}

.payload-body {
  margin: 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-md);
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.6;
}

.dialog-tip {
  font-size: 12px;
  line-height: 1.7;
  color: var(--wf-text-muted);
}
</style>
