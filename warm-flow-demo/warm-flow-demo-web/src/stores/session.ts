import { defineStore } from 'pinia'
import { ref } from 'vue'
import { pageTodoTasks } from '../api/tasks'

export const useSessionStore = defineStore('session', () => {
  // 身份在 HttpOnly Cookie 中，前端只缓存用户名用于展示；登录/登出接口负责真实会话
  const currentUser = ref<string>(sessionStorage.getItem('wf_user') || '')
  const todoCount = ref(0)

  function setCurrentUser(user: string) {
    currentUser.value = user
    sessionStorage.setItem('wf_user', user)
    sessionStorage.setItem('wf_logged_in', '1')
  }

  function clearCurrentUser() {
    currentUser.value = ''
    sessionStorage.removeItem('wf_user')
    sessionStorage.removeItem('wf_logged_in')
    todoCount.value = 0
  }

  async function refreshTodoCount() {
    if (!currentUser.value) {
      todoCount.value = 0
      return
    }
    try {
      const result = await pageTodoTasks({ pageNum: 1, pageSize: 1 })
      todoCount.value = result.total || 0
    } catch {
      todoCount.value = 0
    }
  }

  return { currentUser, todoCount, setCurrentUser, clearCurrentUser, refreshTodoCount }
})
