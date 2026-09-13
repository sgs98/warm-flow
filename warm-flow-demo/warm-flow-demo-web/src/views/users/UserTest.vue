<template>
  <div class="page-card">
    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
      title="多用户审批模型：节点办理人可配置多个 demo 用户。点击「切换身份」即可立刻以该用户继续操作，无需退出登录。"
    />
    <el-table :data="users" border :row-class-name="rowClassName">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userName" label="用户(user_name)" width="160" />
      <el-table-column prop="realName" label="姓名" width="160" />
      <el-table-column prop="roleType" label="角色" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.userName === session.currentUser" size="small" type="success" effect="plain">当前身份</el-tag>
          <el-button v-else link type="primary" @click="switchTo(row)">切换身份</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { httpGet, type DemoUser } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const session = useSessionStore()
const users = ref<DemoUser[]>([])

onMounted(async () => {
  users.value = await httpGet<DemoUser[]>('/users')
})

function rowClassName({ row }: { row: DemoUser }) {
  return row.userName === session.currentUser ? 'user-current-row' : ''
}

function switchTo(user: DemoUser) {
  session.setCurrentUser(user.userName)
  ElMessage.success(`已切换为 ${user.realName}（${user.userName}）`)
  session.refreshTodoCount()
}
</script>
