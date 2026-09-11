<template>
  <router-view v-if="$route.path === '/login'" />
  <AppLayout v-else />
</template>

<script setup lang="ts">
import AppLayout from './layout/AppLayout.vue'
</script>
