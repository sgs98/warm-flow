/**
 * 主题状态：亮色 / 暗色 / 跟随系统。
 * <p>主题以 {@code data-theme} 落在 {@code <html>} 上，配色全部由 CSS 变量驱动，
 * 组件层不感知明暗，只消费语义化令牌。</p>
 */
import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark' | 'auto'

const KEY = 'wf_theme'
const QUERY = window.matchMedia('(prefers-color-scheme: dark)')

function readMode(): ThemeMode {
  const saved = localStorage.getItem(KEY)
  return saved === 'light' || saved === 'dark' || saved === 'auto' ? saved : 'auto'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readMode())
  const systemDark = ref(QUERY.matches)

  /** 实际生效的明暗状态。 */
  const isDark = computed(() => (mode.value === 'auto' ? systemDark.value : mode.value === 'dark'))

  /** 当前生效模式的展示文案。 */
  const modeLabel = computed(
    () => ({ light: '亮色', dark: '暗色', auto: '跟随系统' })[mode.value],
  )

  function apply() {
    document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(KEY, next)
  }

  /** 在亮/暗之间快速切换；跟随系统时按当前系统偏好取反。 */
  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  QUERY.addEventListener('change', (event) => {
    systemDark.value = event.matches
  })

  watch(isDark, apply, { immediate: true })

  return { mode, isDark, modeLabel, setMode, toggle }
})
