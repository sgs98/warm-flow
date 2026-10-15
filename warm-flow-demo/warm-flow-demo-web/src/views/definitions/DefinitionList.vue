<template>
  <div class="page-card">
    <div class="table-toolbar">
      <el-input v-model="keyword" placeholder="流程编码/名称" clearable style="width: 220px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1" />
      <el-button :loading="importing" @click="triggerImport">导入流程</el-button>
      <el-button type="primary" plain @click="goCreate">+ 新建流程</el-button>
      <input
        ref="importInput"
        type="file"
        accept=".json,application/json"
        hidden
        @change="onImportFile"
      />
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="150" />
      <el-table-column prop="flowCode" label="流程编码" min-width="140" />
      <el-table-column prop="flowName" label="流程名称" min-width="140" />
      <el-table-column prop="modelValue" label="模型" width="100" />
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isPublish === 1 ? 'success' : 'info'">{{ row.isPublish === 1 ? '已发布' : row.isPublish === 9 ? '失效' : '未发布' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="激活" width="80">
        <template #default="{ row }">{{ row.activityStatus === 1 ? '激活' : '挂起' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="doPublish(row)" v-if="row.isPublish !== 1">发布</el-button>
          <el-button link type="primary" @click="doCopy(row)">复制</el-button>
          <el-button link type="primary" @click="doExport(row)">导出</el-button>
          <el-button link type="primary" @click="goEdit(row)" v-if="row.isPublish === 0">设计</el-button>
          <el-button link type="primary" @click="copyAndEdit(row)" v-else>复制并设计</el-button>
          <el-button link type="primary" @click="openAutoPass(row)">编辑</el-button>
          <el-button link type="danger" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next, sizes"
      :total="total"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      @change="load"
    />

    <el-dialog v-model="autoPassVisible" :title="autoPassTitle" width="460px">
      <el-form label-width="150px">
        <el-form-item label="同一办理人自动审批">
          <el-switch v-model="autoPassValue" />
          <span class="form-tip">下一节点办理人包含当前办理人时，由引擎以该办理人身份自动通过该待办</span>
        </el-form-item>
      </el-form>
      <div class="form-tip auto-pass-note">
        存放在流程定义配置 <code>def_config.autoPass</code>
      </div>
      <template #footer>
        <el-button @click="autoPassVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAutoPass" @click="saveAutoPass">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  httpDelete,
  httpDownload,
  httpGet,
  httpPost,
  httpPut,
  httpUpload,
  type PageVo,
} from '../../api/http'

interface DefinitionRow {
  id: string
  flowCode: string
  flowName: string
  modelValue: string
  version: string
  isPublish: number
  activityStatus: number
  ext?: string | null
  defConfig?: string | null
  updateTime: string
}

const rows = ref<DefinitionRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const router = useRouter()

/** 自动审批弹窗：当前编辑的定义、开关值与标题 */
const autoPassVisible = ref(false)
const autoPassValue = ref(false)
const autoPassRow = ref<DefinitionRow | null>(null)
const savingAutoPass = ref(false)
const autoPassTitle = computed(() =>
  autoPassRow.value ? `编辑流程【${autoPassRow.value.flowName} · v${autoPassRow.value.version}】` : '编辑流程',
)

async function load() {
  loading.value = true
  try {
    const res = await httpGet<PageVo<DefinitionRow>>('/definitions', {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function goCreate() {
  router.push('/definitions/design')
}
function goEdit(row: DefinitionRow) {
  router.push(`/definitions/design/${row.id}`)
}

function search() {
  pageNum.value = 1
  load()
}

function reset() {
  keyword.value = ''
  search()
}

async function doPublish(row: DefinitionRow) {
  await ElMessageBox.confirm(`确认发布流程【${row.flowName}】？发布后该版本可被发起。`, '发布确认', {
    type: 'warning',
    confirmButtonText: '发布',
    cancelButtonText: '取消',
  })
  await httpPut(`/definitions/${row.id}/publish`)
  ElMessage.success('发布成功')
  load()
}

async function doCopy(row: DefinitionRow) {
  const newId = await httpPost<string>(`/definitions/${row.id}/copy`)
  ElMessage.success(`已复制为新流程 ${newId}`)
  load()
}

/** 解析 JSON 里的 autoPass：声明为 true/'true' 返回 true，声明为 false/'false' 返回 false，未声明或非法返回 null */
function readAutoPass(json?: string | null): boolean | null {
  if (!json) return null
  try {
    const parsed = JSON.parse(json)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    const autoPass = parsed.autoPass
    if (autoPass === true || autoPass === 'true') return true
    if (autoPass === false || autoPass === 'false') return false
    return null
  } catch {
    return null
  }
}

/** 开关初值：只读 def_config 的 autoPass，不读业务扩展字段 ext（与引擎口径一致） */
function parseAutoPass(row: DefinitionRow): boolean {
  return readAutoPass(row.defConfig) === true
}

function openAutoPass(row: DefinitionRow) {
  autoPassRow.value = row
  autoPassValue.value = parseAutoPass(row)
  autoPassVisible.value = true
}

async function saveAutoPass() {
  const row = autoPassRow.value
  if (!row) return
  savingAutoPass.value = true
  try {
    await httpPut(`/definitions/${row.id}/auto-pass`, { autoPass: autoPassValue.value })
    ElMessage.success(autoPassValue.value ? '已开启自动审批' : '已关闭自动审批')
    autoPassVisible.value = false
    load()
  } finally {
    savingAutoPass.value = false
  }
}

async function copyAndEdit(row: DefinitionRow) {
  const newId = await httpPost<string>(`/definitions/${row.id}/copy`)
  ElMessage.success(`已复制为新流程 ${newId}，正在打开新版本`)
  router.push(`/definitions/design/${newId}`)
}

async function doExport(row: DefinitionRow) {
  const fileName = await httpDownload(`/definitions/${row.id}/export`, `${row.flowCode}_${row.version}.json`)
  ElMessage.success(`已导出 ${fileName}`)
}

function triggerImport() {
  importInput.value?.click()
}

/** 上传导出的 json 文件导入流程定义，文件校验与解析都由引擎侧完成。 */
async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空选择，保证连续选同一个文件也能再次触发 change
  input.value = ''
  if (!file) return
  const form = new FormData()
  form.append('file', file)
  importing.value = true
  try {
    const newId = await httpUpload<string>('/definitions/import', form)
    ElMessage.success(`导入 ${file.name} 成功，新流程定义 ${newId}，发布后可发起`)
    pageNum.value = 1
    load()
  } finally {
    importing.value = false
  }
}

async function doDelete(row: DefinitionRow) {
  await ElMessageBox.confirm(`确认删除流程【${row.flowName}】？`, '提示', { type: 'warning' })
  await httpDelete(`/definitions/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
/* 开关旁的轻提示 */
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--wf-text-muted, #6b7688);
}

/* 弹窗底部说明块 */
.auto-pass-note {
  margin-left: 0;
  padding: 8px 10px;
  border-radius: var(--wf-radius-md, 10px);
  background: var(--wf-bg, #eef1f6);
}

.auto-pass-note code {
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(64, 158, 255, 0.12);
  color: var(--wf-primary-strong, #2b7de9);
}
</style>
