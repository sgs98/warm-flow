<template>
  <div class="login-page">
    <button
      class="icon-button login-theme"
      :aria-label="`外观：${theme.modeLabel}，点击切换`"
      @click="theme.toggle()"
    >
      <svg v-if="theme.isDark" viewBox="0 0 24 24" aria-hidden="true">
        <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
      </svg>
      <svg v-else viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" stroke-width="1.8" />
        <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
      </svg>
    </button>

    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <span class="logo-mark" aria-hidden="true">W</span>
        <div>
          <h1>Warm-Flow Demo</h1>
          <p>选择演示用户登录</p>
        </div>
      </div>

      <el-form label-position="top" @submit.prevent="doLogin">
        <el-form-item label="演示用户">
          <el-select
            v-model="selectedUser"
            filterable
            placeholder="请选择用户"
            :loading="userLoading"
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

        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="演示密码见登录提示"
            show-password
            @keyup.enter="doLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="login-button"
          :disabled="!selectedUser"
          :loading="submitting"
          native-type="submit"
        >
          登录
        </el-button>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        title="演示密码：warm-flow-demo（可用环境变量 WARM_DEMO_PASSWORD 覆盖）。身份由 HttpOnly Cookie 保存，仅限本机联调。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listLoginUsers, login as loginApi, type DemoUser } from '../../api/auth'
import { useSessionStore } from '../../stores/session'
import { useThemeStore } from '../../stores/theme'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const theme = useThemeStore()
const users = ref<DemoUser[]>([])
const selectedUser = ref('')
const password = ref('')
const userLoading = ref(false)
const submitting = ref(false)

onMounted(async () => {
  userLoading.value = true
  try {
    users.value = await listLoginUsers()
    if (users.value.length === 1) {
      selectedUser.value = users.value[0].userName
    }
  } catch {
    users.value = []
  } finally {
    userLoading.value = false
  }
})

function redirectPath(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/'
}

async function doLogin() {
  if (!selectedUser.value || !password.value) return
  submitting.value = true
  try {
    const user = await loginApi({ username: selectedUser.value, password: password.value })
    session.setCurrentUser(user.userName)
    await router.push(redirectPath())
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
}

.login-theme {
  position: absolute;
  top: 20px;
  right: 20px;
}

.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--wf-surface);
  backdrop-filter: blur(var(--wf-glass-blur)) saturate(160%);
  -webkit-backdrop-filter: blur(var(--wf-glass-blur)) saturate(160%);
  border: 1px solid var(--wf-glass-border) !important;
  border-radius: var(--wf-radius-xl) !important;
  box-shadow: var(--wf-shadow-raised);
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
  height: 40px;
}

@media (max-width: 480px) {
  .login-card {
    width: 100%;
  }
}
</style>
