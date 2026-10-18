<template>
  <div class="page-card">
    <div class="table-toolbar">
      <el-input v-model="keyword" placeholder="流程编码/名称" clearable style="width: 220px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div class="toolbar-spacer" />
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
      <template #empty>
        <el-empty description="暂无流程定义，点击右上角「新建流程」或「导入流程」开始" :image-size="72" />
      </template>
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
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="doPublish(row)" v-if="row.isPublish !== 1">发布</el-button>
          <el-button link type="primary" @click="doCopy(row)">复制</el-button>
          <el-button link type="primary" @click="doExport(row)">导出</el-button>
          <el-button link type="primary" @click="doPreview(row)">预览</el-button>
          <el-button link type="primary" @click="goEdit(row)" v-if="row.isPublish === 0">设计</el-button>
          <el-button link type="primary" @click="copyAndEdit(row)" v-else>复制并设计</el-button>
          <el-button link type="danger" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- EP 2.4.3 的 pagination 没有 change 事件，需分别监听 current-change / size-change -->
    <el-pagination
      layout="total, prev, pager, next, sizes"
      :total="total"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="load"
      @size-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  copyDefinition,
  exportDefinition,
  importDefinition,
  listDefinitions,
  publishDefinition,
  removeDefinition,
} from '../../api/definitions'
import type { DefinitionRow } from '../../types/workflow'

const rows = ref<DefinitionRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(false)
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const router = useRouter()

/** 按当前筛选条件加载流程定义分页数据。 */
async function load() {
  loading.value = true
  try {
    const res = await listDefinitions({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    rows.value = (res.list || []) as DefinitionRow[]
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

/** 以只读模式打开设计器预览流程结构。 */
function doPreview(row: DefinitionRow) {
  router.push({ path: `/definitions/design/${row.id}`, query: { readonly: 'true' } })
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
  await publishDefinition(row.id)
  ElMessage.success('发布成功')
  load()
}

async function doCopy(row: DefinitionRow) {
  const newId = await copyDefinition(row.id)
  ElMessage.success(`已复制为新流程 ${newId}`)
  load()
}

async function copyAndEdit(row: DefinitionRow) {
  const newId = await copyDefinition(row.id)
  ElMessage.success(`已复制为新流程 ${newId}，正在打开新版本`)
  router.push(`/definitions/design/${newId}`)
}

async function doExport(row: DefinitionRow) {
  const fileName = await exportDefinition(row.id, `${row.flowCode}_${row.version}.json`)
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
  importing.value = true
  try {
    const newId = await importDefinition(file)
    ElMessage.success(`导入 ${file.name} 成功，新流程定义 ${newId}，发布后可发起`)
    pageNum.value = 1
    load()
  } finally {
    importing.value = false
  }
}

async function doDelete(row: DefinitionRow) {
  await ElMessageBox.confirm(`确认删除流程【${row.flowName}】？`, '提示', { type: 'warning' })
  await removeDefinition(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
</style>
