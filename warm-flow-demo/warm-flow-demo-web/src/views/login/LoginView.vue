<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <span class="logo-mark">W</span>
        <div>
          <h1>Warm-Flow Demo</h1>
          <p>选择演示用户登录</p>
        </div>
      </div>

      <el-form label-position="top" @submit.prevent="login">
        <el-form-item label="演示用户">
          <el-select
            v-model="selectedUser"
            filterable
            placeholder="请选择用户"
            :loading="loading"
            style="width: 100%"
          >
            <el-option
              v-for="user in users"
              :key="user.userName"
              :label="`${user.realName}（${user.userName}）`"
              :value="user.userName"
            />
          </el-select>
        </el-form-item>

        <el-button
          type="primary"
          class="login-button"
          :disabled="!selectedUser"
          :loading="loading"
          native-type="submit"
        >
          登录
        </el-button>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        title="Demo 未接入真实鉴权，用户与部门来自后端内存数据。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { httpGet, type DemoUser } from '../../api/http'
import { useSessionStore } from '../../stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const users = ref<DemoUser[]>([])
const selectedUser = ref('')
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    users.value = await httpGet<DemoUser[]>('/users')
    if (users.value.length === 1) {
      selectedUser.value = users.value[0].userName
    }
  } finally {
    loading.value = false
  }
})

function redirectPath(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/'
}

function login() {
  if (!selectedUser.value) return
  session.setCurrentUser(selectedUser.value)
  router.push(redirectPath())
}
</script>

<style scoped lang="scss">
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 18%, rgba(47, 107, 240, 0.16), transparent 34%),
    radial-gradient(circle at 82% 72%, rgba(47, 107, 240, 0.12), transparent 38%),
    var(--wf-bg);
}

.login-card {
  width: 400px;
  border: 1px solid var(--wf-border);
  border-radius: var(--wf-radius-lg);
  box-shadow: var(--wf-shadow-card);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;

  h1 {
    margin: 0;
    font-size: 20px;
    color: var(--wf-text);
  }

  p {
    margin: 3px 0 0;
    font-size: 13px;
    color: var(--wf-text-muted);
  }
}

.login-button {
  width: 100%;
  margin-bottom: 16px;
}
</style>
