<template>
  <router-view v-if="route.path === '/login'" />
  <AppLayout v-else-if="session.currentUser" />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppLayout from './layout/AppLayout.vue'
import { useSessionStore } from './stores/session'

const route = useRoute()
const session = useSessionStore()
</script>
