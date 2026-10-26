<template>
  <div class="event-test">
    <!-- 案例说明卡片 -->
    <div class="case-grid">
      <el-card v-for="c in cases" :key="c.flowCode" class="case-card" shadow="never">
        <template #header>
          <div class="case-head">
            <b>{{ c.name }}</b>
            <div class="case-tags">
              <el-tag v-for="t in c.tags" :key="t" size="small" type="warning" effect="plain">{{ t }}</el-tag>
            </div>
          </div>
        </template>
        <ol class="case-steps">
          <li v-for="(s, i) in c.steps" :key="i">{{ s }}</li>
        </ol>
        <div class="case-actions">
          <!-- 消息启动流程无法普通发起，必须经消息事件创建实例 -->
          <el-button v-if="c.action === 'message'" type="primary" size="small" :loading="starting === c.flowCode"
            @click="startByMessage(c)">消息启动</el-button>
          <el-button v-else-if="c.action === 'goto'" size="small" @click="router.push('/instances')">去实例页发起</el-button>
          <span v-else class="case-note">{{ c.buttonNote }}</span>
        </div>
      </el-card>
    </div>

    <!-- 事件实例与模拟操作 -->
    <div class="page-card">
      <div class="table-toolbar">
        <b>事件流程实例</b>
        <span class="muted">（列出 6 个事件案例的实例；「触发消息」按实例当前等待中的消息事件自动出现）</span>
        <div class="toolbar-spacer" />
        <el-button size="small" @click="loadAll">刷新</el-button>
      </div>
      <el-table :data="instances" v-loading="loading" border>
        <template #empty>
          <el-empty description="暂无事件流程实例，在「流程实例」页发起案例流程后这里会出现" :image-size="64" />
        </template>
        <el-table-column prop="id" label="实例ID" width="165" />
        <el-table-column prop="flowName" label="流程" min-width="150" />
        <el-table-column prop="businessId" label="业务ID" min-width="130" />
        <el-table-column prop="nodeName" label="当前节点" min-width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.flowStatusKey, row.flowStatusName)">
              {{ row.flowStatusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="发起人" width="90" />
        <el-table-column label="模拟操作" min-width="280">
          <template #default="{ row }">
            <el-button v-if="messageWait(row)" link type="warning" @click="openTrigger(row)">触发消息</el-button>
            <el-button link type="primary" @click="openWaiting(row)">等待事件</el-button>
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 消息发布记录 -->
    <div class="page-card">
      <div class="table-toolbar">
        <b>消息发布记录</b>
        <span class="muted">（MESSAGE_THROW / MESSAGE_END 经 DemoEventPublisher 发布，最新在前，保留 100 条）</span>
        <div class="toolbar-spacer" />
        <el-button size="small" @click="loadPublished">刷新</el-button>
      </div>
      <el-empty v-if="published.length === 0" description="暂无发布记录，走完含消息抛出/消息结束的流程后这里会出现记录" :image-size="60" />
      <el-timeline v-else style="padding-left: 8px">
        <el-timeline-item v-for="(p, i) in published" :key="i" :timestamp="p.slice(0, p.indexOf(']') + 1)">
          {{ p.slice(p.indexOf(']') + 1).trim() }}
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 触发消息 -->
    <el-dialog v-model="triggerVisible" title="触发消息（模拟外部系统回调）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="实例">
          <span>{{ triggerRow?.flowName }}（{{ triggerRow?.businessId }}）</span>
        </el-form-item>
        <el-form-item label="等待消息">
          <el-select v-model="triggerName" style="width: 100%" placeholder="请选择要触发的消息">
            <el-option v-for="w in triggerMessageWaits" :key="w.eventName"
              :label="`${w.eventName}（${w.eventType === 'MESSAGE_BOUNDARY' ? '消息边界' : '消息捕获'}）`"
              :value="w.eventName || ''" />
          </el-select>
        </el-form-item>
        <el-form-item label="载荷 JSON">
          <el-input v-model="triggerPayload" type="textarea" :rows="3" placeholder='可选，如 {"riskLevel":"low"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triggerVisible = false">取消</el-button>
        <el-button type="primary" :loading="triggering" @click="doTrigger">触发</el-button>
      </template>
    </el-dialog>

    <!-- 等待中的事件 -->
    <el-dialog v-model="waitingVisible" title="等待中的事件" width="680px">
      <el-table :data="waitingRows" v-loading="waitingLoading" border>
        <template #empty>
          <el-empty description="该实例当前没有等待中的事件" :image-size="60" />
        </template>
        <el-table-column prop="eventType" label="事件类型" width="140" />
        <el-table-column label="消息/错误码" min-width="120">
          <template #default="{ row }">{{ row.eventName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="nodeCode" label="节点" min-width="110" />
        <el-table-column prop="nextTriggerAt" label="下次触发" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listDefinitions } from '../../api/definitions'
import { pageInstances } from '../../api/instances'
import {
  getInstanceWaitingEvents,
  getPublishedMessages,
  startInstanceByMessage,
  triggerInstanceMessage,
} from '../../api/events'
import { statusTagType } from '../../utils/status'
import type { DefinitionOption, InstanceRow, WaitingEvent } from '../../types/workflow'

/** 带等待事件缓存的实例行。 */
interface EventInstanceRow extends InstanceRow {
  /** 该实例当前等待中的事件（用于渲染模拟按钮）。 */
  waits?: WaitingEvent[]
}

/** 事件测试案例说明配置。 */
interface EventCase {
  flowCode: string
  name: string
  tags: string[]
  steps: string[]
  action: 'goto' | 'message' | 'none'
  buttonNote?: string
  /** 消息启动案例（action=message）使用的消息名，缺省沿用案例 2 的 order.paid。 */
  messageEventName?: string
  /** 消息启动案例生成的 businessId 前缀（不同案例用不同前缀，便于分辨）。 */
  businessIdPrefix?: string
  /** 消息启动时携带的流程变量（每次点击重新生成）。 */
  messageVariables?: () => Record<string, unknown>
}

const router = useRouter()

const cases: EventCase[] = [
  {
    flowCode: 'demo_event_refund',
    name: '退款审批（定时边界 + 消息结束）',
    tags: ['TIMER_BOUNDARY', 'MESSAGE_END'],
    action: 'goto',
    steps: [
      '在「流程实例」页正常发起，你是发起人，"提交退款申请"自动归你办理',
      '到"我的待办"故意不办初审，等 2 分钟定时边界自动触发',
      '初审任务失效，流程自动转"客服主管复核"',
      '走完财务退款后，下方"消息发布记录"会出现 refund.finish',
    ],
  },
  {
    flowCode: 'demo_event_risk_wait',
    name: '支付对账（消息启动 + 消息捕获）',
    tags: ['MESSAGE_START', 'MESSAGE_CATCH'],
    action: 'message',
    messageEventName: 'order.paid',
    businessIdPrefix: 'risk',
    messageVariables: () => ({ orderId: `ORD-${Date.now()}` }),
    steps: [
      '点「消息启动」，按消息 order.paid 直接创建实例（消息启动流程无法普通发起）',
      '实例停在"风控结果等待"节点，等待外部消息',
      '在下方实例表点「触发消息」模拟风控回调',
      '到"我的待办"办理"对账确认"完成流程',
    ],
  },
  {
    flowCode: 'demo_event_cooldown',
    name: '注销冷静期（定时捕获 + 消息抛出）',
    tags: ['TIMER_CATCH', 'MESSAGE_THROW'],
    action: 'goto',
    steps: [
      '在「流程实例」页正常发起并办理"提交注销申请"',
      '进入 2 分钟冷静期：实例无待办，可在下方点「等待事件」查看倒计时',
      '到期后自动推进到"注销终审确认"',
      '确认完成后，下方"消息发布记录"会出现 membership.cancelled',
      '同一步还会自动多出一个"注销结果通知"实例：发布器把消息转成 Spring 事件，订阅示例按消息启动了下一个流程（见案例 6）',
    ],
  },
  {
    flowCode: 'demo_event_pay_fail',
    name: '付款失败善后（错误边界）',
    tags: ['ERROR_BOUNDARY'],
    action: 'goto',
    steps: [
      '在「流程实例」页发起时加入流程变量 payAmount=5000（大于限额 1000），再办理"发起付款申请"',
      '"财务执行付款"节点创建时监听器读取金额，超限即自动抛错，付款任务不产生待办即被中断置为失效',
      '错误码 PAY_FAILED 命中边界，流程自动转"失败善后处理"，办理后结束',
      '金额不超过 1000（或不传）则正常出现"财务执行付款"待办，正常办理付款即可',
    ],
  },
  {
    flowCode: 'demo_event_daily_check',
    name: '每日巡检（定时启动 + 终止结束）',
    tags: ['TIMER_START', 'TERMINATE_END'],
    action: 'none',
    buttonNote: '定时启动：每天 09:00 自动创建实例，无需人工发起',
    steps: [
      '每天 09:00 定时器自动创建实例并停在"现场巡检"（需 warm-flow.timer-enabled=true）',
      '想立即测试：在设计器里复制该流程，把定时点改成当前时间 1-2 分钟后并发布',
      '办理巡检后进入并行分支（质检 + 风控）',
      '办理风控时带流程变量 veto=1 → 终止结束打断质检分支；veto=0 → 两分支正常汇合',
    ],
  },
  {
    flowCode: 'demo_event_notify',
    name: '注销结果通知-下游流程（消息启动，宿主消费示例）',
    tags: ['MESSAGE_START', '宿主订阅示例'],
    action: 'message',
    messageEventName: 'membership.cancelled',
    businessIdPrefix: 'notify',
    steps: [
      '本流程不是给人工发起的，而是"被消息驱动"：走完案例 3 的注销流程后会自动多出一个实例',
      '也可点「消息启动」直接按消息 membership.cancelled 手工创建实例（模拟外部系统回调）',
      '实例停在"发送注销通知"（admin 待办），办理后结束',
      '消费逻辑见 DemoMessageSubscriber：@EventListener 收到发布器转发的消息 → 调 startByMessage 启动本流程',
    ],
  },
]

const definitions = ref<DefinitionOption[]>([])
const instances = ref<EventInstanceRow[]>([])
const published = ref<string[]>([])
const loading = ref(false)
const starting = ref('')

const triggerVisible = ref(false)
const triggerRow = ref<EventInstanceRow | null>(null)
const triggerName = ref('')
const triggerPayload = ref('{}')
const triggering = ref(false)

const waitingVisible = ref(false)
const waitingLoading = ref(false)
const waitingRows = ref<WaitingEvent[]>([])

const eventDefIds = ref(new Set<string>())

/** 触发弹窗内展示当前实例全部可触发的消息等待（捕获/边界）。 */
const triggerMessageWaits = computed(() =>
  (triggerRow.value?.waits || []).filter(isMessageWait))

/** 事件等待是否处于可触发状态。 */
function isTriggerable(wait: WaitingEvent): boolean {
  return wait.status === 'WAITING' || wait.status === 'RETRY'
}

/** 是否为可触发的消息类等待（捕获/边界）。 */
function isMessageWait(wait: WaitingEvent): boolean {
  return isTriggerable(wait) && ['MESSAGE_CATCH', 'MESSAGE_BOUNDARY'].includes(wait.eventType)
}

/** 行内第一个可触发的消息等待；无则返回 undefined（隐藏「触发消息」按钮）。 */
function messageWait(row: EventInstanceRow): WaitingEvent | undefined {
  return (row.waits || []).find(isMessageWait)
}

/** 生成不会重复的业务ID。 */
function autoBusinessId(prefix: string) {
  return `${prefix}-${Date.now()}`
}

async function loadDefinitions() {
  const data = await listDefinitions({ pageNum: 1, pageSize: 100 })
  definitions.value = (data.list || []).filter(d => d.flowCode?.startsWith('demo_event_'))
  eventDefIds.value = new Set(definitions.value.map(d => String(d.id)))
}

async function loadInstances() {
  loading.value = true
  try {
    const data = await pageInstances({ pageNum: 1, pageSize: 100 })
    const rows = (data.list || []).filter(i => eventDefIds.value.has(String(i.definitionId))) as EventInstanceRow[]
    // 逐实例拉取等待事件，驱动「触发消息」按钮显隐（实例数最多 100，demo 场景可控）
    await Promise.all(rows.map(async (row) => {
      row.waits = await getInstanceWaitingEvents(row.id).catch(() => [])
    }))
    instances.value = rows
  } finally {
    loading.value = false
  }
}

async function loadPublished() {
  try {
    published.value = await getPublishedMessages()
  } catch {
    published.value = []
  }
}

async function loadAll() {
  await loadDefinitions()
  await Promise.all([loadInstances(), loadPublished()])
}

/** 消息启动案例：按案例配置的消息名 / 业务前缀创建实例（消息启动流程唯一的创建实例入口）。 */
async function startByMessage(c: EventCase) {
  starting.value = c.flowCode
  try {
    await startInstanceByMessage({
      flowCode: c.flowCode,
      businessId: autoBusinessId(c.businessIdPrefix || 'msg'),
      eventName: c.messageEventName || 'order.paid',
      variables: c.messageVariables ? c.messageVariables() : {},
    })
    ElMessage.success('消息启动成功，实例已创建并进入等待')
    await loadInstances()
  } finally {
    starting.value = ''
  }
}

function openTrigger(row: EventInstanceRow) {
  triggerRow.value = row
  const first = messageWait(row)
  triggerName.value = first?.eventName || ''
  triggerPayload.value = '{}'
  triggerVisible.value = true
}

/** 触发实例等待中的消息，命中捕获/边界后流程继续推进。 */
async function doTrigger() {
  const row = triggerRow.value
  if (!row) return
  if (!triggerName.value) {
    ElMessage.warning('请选择要触发的消息')
    return
  }
  let payload: Record<string, unknown> | undefined
  const raw = triggerPayload.value.trim()
  if (raw) {
    try {
      payload = JSON.parse(raw)
    } catch {
      ElMessage.warning('载荷不是合法 JSON')
      return
    }
  }
  triggering.value = true
  try {
    await triggerInstanceMessage(row.id, { eventName: triggerName.value, payload })
    ElMessage.success('消息已触发')
    triggerVisible.value = false
    await loadInstances()
  } finally {
    triggering.value = false
  }
}

/** 查看实例等待中的事件（含下次触发时间）。 */
async function openWaiting(row: EventInstanceRow) {
  waitingVisible.value = true
  waitingLoading.value = true
  waitingRows.value = []
  try {
    waitingRows.value = await getInstanceWaitingEvents(row.id)
  } finally {
    waitingLoading.value = false
  }
}

function goDetail(row: InstanceRow) {
  router.push(`/instances/${row.id}`)
}

onMounted(loadAll)
</script>

<style scoped>
.case-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.case-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.case-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.case-steps {
  margin: 0;
  padding-left: 18px;
  color: var(--wf-text, #1e2633);
  font-size: 13px;
  line-height: 1.8;
}

.case-actions {
  margin-top: 12px;
}

.case-note {
  font-size: 12px;
  color: var(--wf-text-muted, #6b7688);
}

.muted {
  font-size: 12px;
  color: var(--wf-text-muted, #6b7688);
}
</style>
