<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import { Delete, Download, InfoFilled, Lightning, MoreFilled, Moon, Picture, Plus, Rank, Search, Sunny, SwitchButton, Upload, User } from '@element-plus/icons-vue'
import Site from '@/components/Site.vue'
import SiteDialog from '@/components/SiteDialog.vue'
import * as api from '@/service/api.js'
import { useTheme } from '@/composables/theme.js'
import { auth, canEdit, logout } from '@/composables/auth.js'

const emit = defineEmits(['login'])

const sites = ref([])
const loading = ref(true)
const keyword = ref('')
const speedLoading = ref(false)
const dragging = ref(false)
const dialogRef = ref()
const searchRef = ref()
const importInput = ref()
const { isDark, toggleTheme } = useTheme()

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    sites.value = await api.listSites()
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    loading.value = false
  }
}

// 状态筛选：all 全部 / ok 可访问 / bad 异常 / untested 未测速
const statusFilter = ref('all')
const matchStatus = (s) => {
  if (statusFilter.value === 'ok') return s.status >= 0 && s.status < 88888
  if (statusFilter.value === 'bad') return s.status >= 88888
  if (statusFilter.value === 'untested') return s.status == null || s.status < 0
  return true
}
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return sites.value.filter(
    (s) => matchStatus(s) && (!k || [s.title, s.content, s.url].some((v) => (v || '').toLowerCase().includes(k))),
  )
})
// 搜索或筛选时展示过滤结果，并禁用拖拽，避免排序错乱
const searching = computed(() => keyword.value.trim().length > 0 || statusFilter.value !== 'all')
const toggleFilter = (f) => (statusFilter.value = statusFilter.value === f ? 'all' : f)

const stats = computed(() => {
  const tested = sites.value.filter((s) => s.status >= 0 && s.status < 88888)
  const bad = sites.value.filter((s) => s.status >= 88888).length
  const avg = tested.length ? Math.round(tested.reduce((a, s) => a + s.status, 0) / tested.length) : null
  const untested = sites.value.filter((s) => s.status == null || s.status < 0).length
  return { total: sites.value.length, online: tested.length, bad, untested, avg }
})

// ---------- 站点操作 ----------
const openAdd = () => dialogRef.value.open()
const openEdit = (site) => dialogRef.value.open(site)

let refreshTimer
function onSaved(site, isNew) {
  load(true)
  // 新站点后台会自动测速，稍后静默刷新一次以显示延迟
  if (isNew) {
    clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => load(true), 6000)
  }
}

async function onDelete(site) {
  try {
    const res = await api.deleteSite(site.id)
    sites.value = sites.value.filter((s) => s.id !== site.id)
    ElMessage.success(res.msg)
  } catch {
    /* 已在拦截器中提示 */
  }
}

// 单个站点重新测速后更新列表
function onSiteUpdated(site) {
  const i = sites.value.findIndex((s) => s.id === site.id)
  if (i >= 0) sites.value[i] = site
}

async function onSortEnd() {
  dragging.value = false
  try {
    await api.saveSort(sites.value.map((s) => s.id))
  } catch {
    load(true)
  }
}

async function runSpeedtest() {
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

// ---------- 备份 ----------
async function exportBackup() {
  try {
    const data = await api.exportSites()
    if (!data.length) {
      ElMessage.warning('没有可导出的站点')
      return
    }
    const payload = data.map(({ title, url, content, description, checkinUrl, icon }) => ({
      title,
      url,
      content,
      description,
      checkinUrl,
      icon,
    }))
    const blob = new Blob([JSON.stringify({ version: 1, sites: payload }, null, 2)], { type: 'application/json' })
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

async function onImportFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  let list
  try {
    const json = JSON.parse(await file.text())
    list = Array.isArray(json) ? json : json.sites || json.data
    if (!Array.isArray(list)) throw new Error()
  } catch {
    ElMessage.error('文件格式不正确，请选择导出的 JSON 备份文件')
    return
  }
  try {
    const res = await api.importSites(list)
    ElMessage.success(res.msg)
    await load(true)
  } catch {
    /* 已在拦截器中提示 */
  }
}

async function deleteAll() {
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

async function doLogout() {
  try {
    await logout()
    ElMessage.success('已退出登录')
    if (!auth.guestView) return
    load(true)
  } catch {
    /* 已在拦截器中提示 */
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') doLogout()
  else if (cmd === 'login') emit('login')
  else if (cmd === 'about') window.open('https://github.com/manone2077/Navigator', '_blank', 'noopener')
  else if (cmd === 'icons') refreshAllIcons()
  else if (cmd === 'export') exportBackup()
  else if (cmd === 'import') importInput.value.click()
  else if (cmd === 'deleteAll') deleteAll()
}

// ---------- 键盘快捷键 ----------
function onKeydown(e) {
  const tag = (e.target.tagName || '').toLowerCase()
  const typing = tag === 'input' || tag === 'textarea' || e.target.isContentEditable
  if (e.key === '/' && !typing) {
    e.preventDefault()
    searchRef.value?.focus()
  }
}

function onSearchEnter() {
  // 搜索结果只有一个时，回车直接打开
  if (filtered.value.length === 1) window.open(filtered.value[0].url, '_blank', 'noopener')
}

// 后台每 10 分钟自动测速，页面每分钟静默刷新一次以展示最新状态（页面不可见时跳过）
let pollTimer
function onVisible() {
  if (!document.hidden && !dragging.value) load(true)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('visibilitychange', onVisible)
  pollTimer = setInterval(() => {
    if (!document.hidden && !speedLoading.value && !dragging.value) load(true)
  }, 60 * 1000)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('visibilitychange', onVisible)
  clearTimeout(refreshTimer)
  clearInterval(pollTimer)
})
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="brand">
          <svg class="logo" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" />
            <path d="M15.5 8.5 13.4 13.4 8.5 15.5 10.6 10.6Z" fill="currentColor" />
          </svg>
          <span>皮特导航</span>
        </div>

        <el-input
          ref="searchRef"
          v-model="keyword"
          class="search"
          :prefix-icon="Search"
          placeholder="搜索站点名称、描述或地址"
          clearable
          @keyup.enter="onSearchEnter"
          @keyup.esc="keyword = ''"
        >
          <template #suffix>
            <kbd v-if="!keyword" class="kbd">/</kbd>
          </template>
        </el-input>

        <div class="tools">
          <template v-if="canEdit">
            <el-button type="primary" :icon="Plus" @click="openAdd"><span class="btn-text">添加站点</span></el-button>
            <el-button class="speed-btn" :icon="Lightning" :loading="speedLoading" @click="runSpeedtest">
              <span class="btn-text">{{ speedLoading ? '测速中' : '测速' }}</span>
            </el-button>
          </template>
          <el-button v-else type="primary" plain :icon="User" @click="emit('login')"><span class="btn-text">登录</span></el-button>
          <el-tooltip :content="isDark ? '切换到浅色模式' : '切换到深色模式'" placement="bottom" :show-after="300">
            <el-button :icon="isDark ? Sunny : Moon" circle @click="toggleTheme" />
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <el-button :icon="MoreFilled" circle />
            <template #dropdown>
              <el-dropdown-menu>
                <template v-if="canEdit">
                  <el-dropdown-item command="icons" :icon="Picture">重新获取图标</el-dropdown-item>
                  <el-dropdown-item command="export" :icon="Download" divided>导出备份</el-dropdown-item>
                  <el-dropdown-item command="import" :icon="Upload">导入备份</el-dropdown-item>
                  <el-dropdown-item command="deleteAll" :icon="Delete" divided class="danger-item">删除所有站点</el-dropdown-item>
                </template>
                <el-dropdown-item v-else command="login" :icon="User">登录</el-dropdown-item>
                <el-dropdown-item v-if="auth.enabled && auth.loggedIn" command="logout" :icon="SwitchButton" divided>
                  退出登录
                </el-dropdown-item>
                <el-dropdown-item command="about" :icon="InfoFilled" divided>
                  关于<span class="version">{{ auth.version ? `v${auth.version}` : '' }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input ref="importInput" type="file" accept=".json,application/json" hidden @change="onImportFile" />
        </div>
      </div>
    </header>

    <main class="main">
      <div v-if="sites.length" class="summary">
        <button class="chip" :class="{ active: statusFilter === 'all' }" @click="statusFilter = 'all'">
          全部 <b>{{ stats.total }}</b>
        </button>
        <button v-if="stats.online" class="chip ok" :class="{ active: statusFilter === 'ok' }" @click="toggleFilter('ok')">
          可访问 <b>{{ stats.online }}</b>
        </button>
        <button v-if="stats.bad" class="chip bad" :class="{ active: statusFilter === 'bad' }" @click="toggleFilter('bad')">
          异常 <b>{{ stats.bad }}</b>
        </button>
        <button
          v-if="stats.untested"
          class="chip"
          :class="{ active: statusFilter === 'untested' }"
          @click="toggleFilter('untested')"
        >
          未测速 <b>{{ stats.untested }}</b>
        </button>
        <span v-if="stats.avg != null" class="avg">平均延迟 <b>{{ stats.avg }}</b> ms</span>
        <span v-if="searching" class="hint">· 显示 {{ filtered.length }} 个</span>
        <span v-else-if="canEdit" class="hint">· 拖动卡片右上角的 <el-icon><Rank /></el-icon> 可调整顺序</span>
      </div>

      <div v-if="loading" class="grid">
        <el-skeleton v-for="i in 6" :key="i" class="skeleton" animated :rows="2" />
      </div>

      <el-empty v-else-if="!sites.length" :description="canEdit ? '还没有站点，先添加一个吧' : '还没有站点'">
        <template v-if="canEdit">
          <el-button type="primary" :icon="Plus" @click="openAdd">添加站点</el-button>
          <el-button :icon="Upload" @click="importInput.click()">导入备份</el-button>
        </template>
      </el-empty>

      <el-empty v-else-if="!filtered.length" :description="keyword ? `没有找到与「${keyword}」相关的站点` : '没有符合条件的站点'">
        <el-button @click="(keyword = ''), (statusFilter = 'all')">显示全部站点</el-button>
      </el-empty>

      <!-- 搜索时展示过滤结果，禁用拖拽，避免排序错乱 -->
      <div v-else-if="searching" class="grid">
        <Site
          v-for="site in filtered"
          :key="site.id"
          :site="site"
          :draggable="false"
          :editable="canEdit"
          @edit="openEdit"
          @delete="onDelete"
          @updated="onSiteUpdated"
        />
      </div>

      <div v-else-if="!canEdit" class="grid">
        <Site v-for="site in sites" :key="site.id" :site="site" :draggable="false" :editable="false" />
      </div>

      <VueDraggable
        v-else
        v-model="sites"
        class="grid"
        :animation="180"
        handle=".drag-handle"
        ghost-class="drag-ghost"
        @start="dragging = true"
        @end="onSortEnd"
      >
        <Site v-for="site in sites" :key="site.id" :site="site" @edit="openEdit" @delete="onDelete" @updated="onSiteUpdated" />
      </VueDraggable>
    </main>

    <template v-if="canEdit">
      <el-button class="fab" type="primary" :icon="Plus" circle aria-label="添加站点" @click="openAdd" />
      <SiteDialog ref="dialogRef" @saved="onSaved" />
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--nav-topbar-bg);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--nav-border);
}

.topbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.brand .logo {
  width: 26px;
  height: 26px;
  color: var(--el-color-primary);
}

.search {
  flex: 1;
  max-width: 420px;
  margin-left: auto;
}

.search :deep(.el-input__wrapper) {
  border-radius: 999px;
}

.kbd {
  font-family: inherit;
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  color: var(--el-text-color-placeholder);
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tools .el-button + .el-button,
.tools .el-dropdown {
  margin-left: 0;
}

.main {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 24px 100px;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary b {
  color: var(--el-text-color-primary);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--nav-border);
  background: var(--nav-card-bg);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip:hover {
  border-color: var(--el-color-primary-light-5);
}

.chip.ok b {
  color: var(--el-color-success);
}

.chip.bad b {
  color: var(--el-color-danger);
}

.chip.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.avg {
  margin-left: 4px;
}

.version {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.summary .hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.skeleton {
  padding: 16px;
  border-radius: 14px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
}

:deep(.drag-ghost) {
  opacity: 0.4;
  border: 1px dashed var(--el-color-primary);
}

.fab {
  display: none;
  position: fixed;
  right: 20px;
  bottom: 24px;
  width: 52px;
  height: 52px;
  font-size: 22px;
  box-shadow: 0 6px 20px rgba(64, 128, 255, 0.4);
}

@media (max-width: 760px) {
  .topbar-inner {
    flex-wrap: wrap;
    padding: 10px 14px;
    gap: 10px;
  }

  .search {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }

  .tools {
    margin-left: auto;
  }

  .tools > .el-button--primary {
    display: none;
  }

  .btn-text {
    display: none;
  }

  .speed-btn {
    width: 32px;
    padding: 0;
    border-radius: 50%;
  }

  .kbd {
    display: none;
  }

  .main {
    padding: 14px 14px 100px;
  }

  .summary .hint {
    display: none;
  }

  .grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .fab {
    display: inline-flex;
  }
}
</style>
