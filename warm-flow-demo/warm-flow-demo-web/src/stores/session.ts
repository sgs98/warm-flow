import { defineStore } from 'pinia'
import { ref } from 'vue'

const KEY = 'wf_user'

export const useSessionStore = defineStore('session', () => {
  const currentUser = ref<string>(localStorage.getItem(KEY) || '')

  function setCurrentUser(user: string) {
    currentUser.value = user
    localStorage.setItem(KEY, user)
  }

  function clearCurrentUser() {
    currentUser.value = ''
    localStorage.removeItem(KEY)
  }

  return { currentUser, setCurrentUser, clearCurrentUser }
})
