import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
  { path: '/', redirect: '/definitions' },
  { path: '/definitions', name: 'definitions', component: () => import('../views/definitions/DefinitionList.vue') },
  { path: '/definitions/design/:id?', name: 'definitionDesign', component: () => import('../views/definitions/DefinitionEdit.vue') },
  { path: '/instances', name: 'instances', component: () => import('../views/instances/InstanceList.vue') },
  { path: '/instances/:id', name: 'instanceDetail', component: () => import('../views/instances/InstanceDetail.vue') },
  { path: '/todo', name: 'todo', component: () => import('../views/tasks/TodoList.vue') },
  { path: '/done', name: 'done', component: () => import('../views/tasks/DoneList.vue') },
  { path: '/copy', name: 'copy', component: () => import('../views/tasks/CopyList.vue') },
  { path: '/users', name: 'users', component: () => import('../views/users/UserTest.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const user = localStorage.getItem('wf_user')
  if (to.path === '/login') {
    return user ? { path: '/' } : true
  }
  if (!user) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }
  return true
})

export default router
