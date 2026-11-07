<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import { Delete, Download, Lightning, MoreFilled, Moon, Plus, Rank, Search, Sunny, Upload } from '@element-plus/icons-vue'
import Site from '@/components/Site.vue'
import SiteDialog from '@/components/SiteDialog.vue'
import * as api from '@/service/api.js'
import { useTheme } from '@/composables/theme.js'

const sites = ref([])
const loading = ref(true)
const keyword = ref('')
const speedLoading = ref(false)
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

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return sites.value
  return sites.value.filter((s) => [s.title, s.content, s.url].some((v) => (v || '').toLowerCase().includes(k)))
})
const searching = computed(() => keyword.value.trim().length > 0)

const stats = computed(() => {
  const tested = sites.value.filter((s) => s.status >= 0 && s.status < 88888)
  const bad = sites.value.filter((s) => s.status >= 88888).length
  const avg = tested.length ? Math.round(tested.reduce((a, s) => a + s.status, 0) / tested.length) : null
  return { total: sites.value.length, online: tested.length, bad, avg }
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

async function onSortEnd() {
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

function onCommand(cmd) {
  if (cmd === 'export') exportBackup()
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

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(refreshTimer)
})
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="brand">
          <img src="/favicon.ico" alt="" />
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
          <el-button type="primary" :icon="Plus" @click="openAdd"><span class="btn-text">添加站点</span></el-button>
          <el-button class="speed-btn" :icon="Lightning" :loading="speedLoading" @click="runSpeedtest">
            <span class="btn-text">{{ speedLoading ? '测速中' : '测速' }}</span>
          </el-button>
          <el-tooltip :content="isDark ? '切换到浅色模式' : '切换到深色模式'" placement="bottom" :show-after="300">
            <el-button :icon="isDark ? Sunny : Moon" circle @click="toggleTheme" />
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <el-button :icon="MoreFilled" circle />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="export" :icon="Download">导出备份</el-dropdown-item>
                <el-dropdown-item command="import" :icon="Upload">导入备份</el-dropdown-item>
                <el-dropdown-item command="deleteAll" :icon="Delete" divided class="danger-item">删除所有站点</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input ref="importInput" type="file" accept=".json,application/json" hidden @change="onImportFile" />
        </div>
      </div>
    </header>

    <main class="main">
      <div v-if="sites.length" class="summary">
        <span>共 <b>{{ stats.total }}</b> 个站点</span>
        <span v-if="stats.online">· <b>{{ stats.online }}</b> 个可访问</span>
        <span v-if="stats.bad" class="bad">· <b>{{ stats.bad }}</b> 个异常</span>
        <span v-if="stats.avg != null">· 平均延迟 <b>{{ stats.avg }}</b> ms</span>
        <span v-if="searching" class="hint">· 搜索到 {{ filtered.length }} 个</span>
        <span v-else class="hint">· 拖动卡片右上角的 <el-icon><Rank /></el-icon> 可调整顺序</span>
      </div>

      <div v-if="loading" class="grid">
        <el-skeleton v-for="i in 6" :key="i" class="skeleton" animated :rows="2" />
      </div>

      <el-empty v-else-if="!sites.length" description="还没有站点，先添加一个吧">
        <el-button type="primary" :icon="Plus" @click="openAdd">添加站点</el-button>
        <el-button :icon="Upload" @click="importInput.click()">导入备份</el-button>
      </el-empty>

      <el-empty v-else-if="!filtered.length" :description="`没有找到与「${keyword}」相关的站点`" />

      <!-- 搜索时展示过滤结果，禁用拖拽，避免排序错乱 -->
      <div v-else-if="searching" class="grid">
        <Site v-for="site in filtered" :key="site.id" :site="site" :draggable="false" @edit="openEdit" @delete="onDelete" />
      </div>

      <VueDraggable
        v-else
        v-model="sites"
        class="grid"
        :animation="180"
        handle=".drag-handle"
        ghost-class="drag-ghost"
        @end="onSortEnd"
      >
        <Site v-for="site in sites" :key="site.id" :site="site" @edit="openEdit" @delete="onDelete" />
      </VueDraggable>
    </main>

    <el-button class="fab" type="primary" :icon="Plus" circle aria-label="添加站点" @click="openAdd" />

    <SiteDialog ref="dialogRef" @saved="onSaved" />
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

.brand img {
  width: 28px;
  height: 28px;
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

.summary .bad b {
  color: var(--el-color-danger);
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
