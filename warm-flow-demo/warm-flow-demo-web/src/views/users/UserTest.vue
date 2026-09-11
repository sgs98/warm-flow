<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
      title="多用户审批模型：节点办理人可配置多个 demo 用户(user_name)。需要切换身份时，请先退出登录并重新选择用户。" />
    <el-table :data="users" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userName" label="用户(user_name)" width="160" />
      <el-table-column prop="realName" label="姓名" width="160" />
      <el-table-column prop="roleType" label="角色" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { httpGet, type DemoUser } from '../../api/http'

const users = ref<DemoUser[]>([])
onMounted(async () => {
  users.value = await httpGet<DemoUser[]>('/users')
})
</script>
