import { computed, ref } from 'vue'
import * as api from '@/service/api.js'

// 全局共享的标签列表：分组视图、标签筛选、站点表单、标签管理都使用同一份数据
const tags = ref([])

export function useTags() {
  async function loadTags() {
    try {
      tags.value = await api.listTags()
    } catch {
      /* 已在拦截器中提示 */
    }
  }

  const tagById = computed(() => new Map(tags.value.map((t) => [t.id, t])))

  return { tags, tagById, loadTags }
}

/** 常用标签建议（标签管理中一键添加，不会自动创建） */
export const SUGGESTED_TAGS = ['影音', '下载', '基础设施', '监控', '常用网站']

/** 与后端一致的默认配色，用于标签管理中的颜色选择 */
export const TAG_COLORS = ['#38bdf8', '#a78bfa', '#f472b6', '#fb923c', '#4ade80', '#facc15', '#22d3ee', '#f87171']
