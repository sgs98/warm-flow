import { defineStore } from 'pinia'
import { ref } from 'vue'
import { httpGet, type PageVo } from '../api/http'

const KEY = 'wf_user'

export const useSessionStore = defineStore('session', () => {
  const currentUser = ref<string>(localStorage.getItem(KEY) || '')
  const todoCount = ref(0)

  function setCurrentUser(user: string) {
    currentUser.value = user
    localStorage.setItem(KEY, user)
  }

  function clearCurrentUser() {
    currentUser.value = ''
    localStorage.removeItem(KEY)
    todoCount.value = 0
  }

  async function refreshTodoCount() {
    if (!currentUser.value) {
      todoCount.value = 0
      return
    }
    try {
      const result = await httpGet<PageVo<unknown>>('/tasks/todo', { pageNum: 1, pageSize: 1 })
      todoCount.value = result.total || 0
    } catch {
      todoCount.value = 0
    }
  }

  return { currentUser, todoCount, setCurrentUser, clearCurrentUser, refreshTodoCount }
})
