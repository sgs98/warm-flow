/**
 * 分页列表通用逻辑：加载、翻页、刷新、失败重试。
 * <p>把各列表页重复的 rows/total/pageNum/loading/load 模式收敛为一个组合式函数；
 * 数据获取函数由调用方注入，页面只关心展示与操作。</p>
 * <p>请求失败时不再向外抛错（http 层已统一提示），而是置位 error 供页面展示重试入口，
 * 避免列表页出现未捕获的 Promise 异常。</p>
 */
import { onMounted, ref, type Ref } from 'vue'
import type { PageResult } from '../types/workflow'

/**
 * 分页列表组合式函数。
 *
 * @param fetcher   分页数据获取函数，入参为 pageNum / pageSize
 * @param options   pageSize 默认 10；immediate 是否挂载即加载，默认 true
 * @returns rows 数据、total 总数、pageNum 当前页、loading 加载态、error 失败态、load 手动加载、reload 回到第一页重新加载
 */
export function usePagedList<T>(
  fetcher: (pageNum: number, pageSize: number) => Promise<PageResult<T>>,
  options: { pageSize?: number; immediate?: boolean } = {},
) {
  const pageSize = options.pageSize ?? 10
  const rows = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const pageNum = ref(1)
  const loading = ref(false)
  const error = ref(false)

  /** 加载当前页数据，失败时清空列表并置位 error。 */
  async function load() {
    loading.value = true
    error.value = false
    try {
      const res = await fetcher(pageNum.value, pageSize)
      rows.value = res.list || []
      total.value = res.total || 0
    } catch {
      rows.value = []
      total.value = 0
      error.value = true
    } finally {
      loading.value = false
    }
  }

  /** 回到第一页重新加载，用于增删改后的刷新。 */
  async function reload() {
    pageNum.value = 1
    await load()
  }

  if (options.immediate !== false) {
    onMounted(load)
  }

  return { rows, total, pageNum, pageSize, loading, error, load, reload }
}
