import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/service/api.js'
import { STATUS_TIMEOUT } from '@/composables/site.js'
import { useTags } from '@/composables/tags.js'

const VIEW_KEY = 'navigator-view'

/**
 * 站点列表的数据与操作：加载、筛选、删除、排序、测速、备份，以及定时静默刷新。
 * 页面组件只负责布局和交互，所有请求都在这里完成。
 */
export function useSites() {
  const sites = ref([])
  const { tags, loadTags } = useTags()
  const loading = ref(true)
  const speedLoading = ref(false)
  const dragging = ref(false)

  // ---------- 加载 ----------
  async function load(silent = false) {
    if (!silent) loading.value = true
    try {
      const [list] = await Promise.all([api.listSites(), loadTags()])
      sites.value = list
    } catch {
      /* 已在拦截器中提示 */
    } finally {
      loading.value = false
    }
  }

  // ---------- 搜索与筛选 ----------
  const keyword = ref('')
  // all 全部 / ok 可访问 / bad 异常 / untested 未测速
  const statusFilter = ref('all')
  // 按标签筛选：标签 id，null 表示全部
  const tagFilter = ref(null)
  // 视图：group 按标签分组（默认）/ flat 平铺
  const view = ref(localStorage.getItem(VIEW_KEY) || 'group')
  const setView = (v) => {
    view.value = v
    localStorage.setItem(VIEW_KEY, v)
  }
  const isBad = (s) => s.status >= STATUS_TIMEOUT
  const isUntested = (s) => s.status == null || s.status < 0
  const isOk = (s) => !isBad(s) && !isUntested(s)
  const matchStatus = (s) =>
    ({ ok: isOk, bad: isBad, untested: isUntested })[statusFilter.value]?.(s) ?? true

  const filtered = computed(() => {
    const k = keyword.value.trim().toLowerCase()
    const t = tagFilter.value
    return sites.value.filter(
      (s) =>
        matchStatus(s) &&
        (t == null || (s.tags || []).some((x) => x.id === t)) &&
        (!k || [s.title, s.content, s.url, ...(s.tags || []).map((x) => x.name)].some((v) => (v || '').toLowerCase().includes(k))),
    )
  })
  // 搜索或筛选时展示过滤结果，并禁用拖拽，避免排序错乱
  const searching = computed(
    () => keyword.value.trim().length > 0 || statusFilter.value !== 'all' || tagFilter.value != null,
  )
  const resetFilter = () => {
    keyword.value = ''
    statusFilter.value = 'all'
    tagFilter.value = null
  }

  // 没有标签时只能平铺显示
  const grouped = computed(() => view.value === 'group' && tags.value.length > 0)
  /** 分组：按标签顺序，一个站点可以出现在多个分组中；没有标签的站点放在“未分组”，空分组不显示 */
  const groups = computed(() => {
    const list = tags.value.map((tag) => ({ tag, sites: filtered.value.filter((s) => (s.tags || []).some((x) => x.id === tag.id)) }))
    list.push({ tag: null, sites: filtered.value.filter((s) => !(s.tags || []).length) })
    return list.filter((g) => g.sites.length)
  })

  const stats = computed(() => {
    const ok = sites.value.filter(isOk)
    const avg = ok.length ? Math.round(ok.reduce((a, s) => a + s.status, 0) / ok.length) : null
    return {
      total: sites.value.length,
      online: ok.length,
      bad: sites.value.filter(isBad).length,
      untested: sites.value.filter(isUntested).length,
      avg,
    }
  })

  // ---------- 站点操作 ----------
  let refreshTimer
  function onSaved(site, isNew) {
    load(true)
    // 新站点后台会自动测速和获取图标，稍后静默刷新一次
    if (isNew) {
      clearTimeout(refreshTimer)
      refreshTimer = setTimeout(() => load(true), 6000)
    }
  }

  function replaceSite(site) {
    const i = sites.value.findIndex((s) => s.id === site.id)
    if (i >= 0) sites.value[i] = site
  }

  async function remove(site) {
    try {
      const res = await api.deleteSite(site.id)
      sites.value = sites.value.filter((s) => s.id !== site.id)
      ElMessage.success(res.msg)
    } catch {
      /* 已在拦截器中提示 */
    }
  }

  /**
   * 分组内拖拽排序：组内站点在整体顺序中占据的位置不变，只调整它们之间的先后，
   * 这样其它分组中的相对顺序不受影响
   */
  function reorderGroup(ids) {
    const order = new Map(ids.map((id, i) => [id, i]))
    const positions = []
    sites.value.forEach((s, i) => order.has(s.id) && positions.push(i))
    const moved = ids.map((id) => sites.value.find((s) => s.id === id))
    const next = [...sites.value]
    positions.forEach((pos, k) => (next[pos] = moved[k]))
    sites.value = next
    return saveSort()
  }

  async function saveSort() {
    dragging.value = false
    try {
      await api.saveSort(sites.value.map((s) => s.id))
    } catch {
      load(true)
    }
  }

  async function speedtestAll() {
    if (!sites.value.length) {
      ElMessage.warning('请先添加站点哟')
      return
    }
    speedLoading.value = true
    try {
      sites.value = await api.speedtest()
      ElMessage.success('测速完成')
    } catch {
      /* 已在拦截器中提示 */
    } finally {
      speedLoading.value = false
    }
  }

  async function refreshAllIcons() {
    const loadingMsg = ElMessage({ message: '正在获取站点图标…', duration: 0, type: 'info' })
    try {
      const res = await api.refreshIcons()
      sites.value = res.data || sites.value
      ElMessage.success(res.msg)
    } catch {
      /* 已在拦截器中提示 */
    } finally {
      loadingMsg.close()
    }
  }

  async function removeAll() {
    if (!sites.value.length) {
      ElMessage.warning('没有站点可删除')
      return
    }
    try {
      await ElMessageBox.confirm(`将删除全部 ${sites.value.length} 个站点，且无法恢复。建议先导出备份。`, '删除所有站点', {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      })
    } catch {
      return
    }
    try {
      const res = await api.deleteAllSites()
      sites.value = []
      ElMessage.success(res.msg)
    } catch {
      /* 已在拦截器中提示 */
    }
  }

  // ---------- 备份 ----------
  async function exportBackup() {
    try {
      const data = await api.exportSites()
      if (!data.length) {
        ElMessage.warning('没有可导出的站点')
        return
      }
      const payload = data.map(({ title, url, content, description, checkinUrl, icon, tags: siteTags }) => ({
        title,
        url,
        content,
        description,
        checkinUrl,
        icon,
        tags: (siteTags || []).map((t) => t.name),
      }))
      // version 2：增加标签定义（名称、颜色、顺序），旧版本的备份文件仍可导入
      const tagDefs = tags.value.map(({ name, color }) => ({ name, color }))
      const blob = new Blob([JSON.stringify({ version: 2, tags: tagDefs, sites: payload }, null, 2)], {
        type: 'application/json',
      })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `navigator-backup-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(a.href)
      ElMessage.success(`已导出 ${payload.length} 个站点`)
    } catch {
      /* 已在拦截器中提示 */
    }
  }

  async function importBackup(file) {
    let list
    let tagDefs = []
    try {
      const json = JSON.parse(await file.text())
      list = Array.isArray(json) ? json : json.sites || json.data
      if (!Array.isArray(list)) throw new Error()
      if (Array.isArray(json.tags)) tagDefs = json.tags
    } catch {
      ElMessage.error('文件格式不正确，请选择导出的 JSON 备份文件')
      return
    }
    try {
      // 先导入标签定义以保留颜色和顺序，站点再按名称关联标签
      if (tagDefs.length) await api.importTags(tagDefs)
      const res = await api.importSites(list)
      ElMessage.success(res.msg)
      await load(true)
    } catch {
      /* 已在拦截器中提示 */
    }
  }

  // ---------- 定时刷新 ----------
  // 后台每 10 分钟自动测速，页面每分钟静默刷新一次以展示最新状态（页面不可见或拖拽时跳过）
  let pollTimer
  const canRefresh = () => !document.hidden && !dragging.value && !speedLoading.value
  const onVisible = () => canRefresh() && load(true)

  onMounted(() => {
    load()
    document.addEventListener('visibilitychange', onVisible)
    pollTimer = setInterval(() => canRefresh() && load(true), 60 * 1000)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisible)
    clearTimeout(refreshTimer)
    clearInterval(pollTimer)
  })

  return {
    sites,
    loading,
    speedLoading,
    dragging,
    keyword,
    statusFilter,
    tagFilter,
    tags,
    view,
    setView,
    grouped,
    groups,
    filtered,
    searching,
    stats,
    load,
    resetFilter,
    reorderGroup,
    onSaved,
    replaceSite,
    remove,
    saveSort,
    speedtestAll,
    refreshAllIcons,
    removeAll,
    exportBackup,
    importBackup,
  }
}
