<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import Site from '@/components/Site.vue'
import SiteDialog from '@/components/SiteDialog.vue'
import TopBar from '@/components/TopBar.vue'
import StatusFilter from '@/components/StatusFilter.vue'
import { useSites } from '@/composables/useSites.js'
import { auth, canEdit, logout } from '@/composables/auth.js'

const emit = defineEmits(['login'])

const {
  sites,
  loading,
  speedLoading,
  dragging,
  keyword,
  statusFilter,
  filtered,
  searching,
  stats,
  load,
  resetFilter,
  onSaved,
  replaceSite,
  remove,
  saveSort,
  speedtestAll,
  refreshAllIcons,
  removeAll,
  exportBackup,
  importBackup,
} = useSites()

const topBar = ref()
const dialogRef = ref()
const importInput = ref()

const openAdd = () => dialogRef.value.open()
const openEdit = (site) => dialogRef.value.open(site)

function onImportFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (file) importBackup(file)
}

async function doLogout() {
  try {
    await logout()
    ElMessage.success('已退出登录')
    if (auth.guestView) load(true)
  } catch {
    /* 已在拦截器中提示 */
  }
}

const commands = {
  icons: refreshAllIcons,
  export: exportBackup,
  import: () => importInput.value.click(),
  deleteAll: removeAll,
  login: () => emit('login'),
  logout: doLogout,
  about: () => window.open('https://github.com/manone2077/Navigator', '_blank', 'noopener'),
}
const onCommand = (cmd) => commands[cmd]?.()

// 搜索结果只有一个时，回车直接打开
function onSearchEnter() {
  if (filtered.value.length === 1) window.open(filtered.value[0].url, '_blank', 'noopener')
}

// 按 / 聚焦搜索框
function onKeydown(e) {
  const tag = (e.target.tagName || '').toLowerCase()
  const typing = tag === 'input' || tag === 'textarea' || e.target.isContentEditable
  if (e.key === '/' && !typing) {
    e.preventDefault()
    topBar.value?.focusSearch()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="page">
    <TopBar
      ref="topBar"
      v-model:keyword="keyword"
      :speed-loading="speedLoading"
      @add="openAdd"
      @speedtest="speedtestAll"
      @command="onCommand"
      @search-enter="onSearchEnter"
    />
    <input ref="importInput" type="file" accept=".json,application/json" hidden @change="onImportFile" />

    <main class="main">
      <StatusFilter
        v-if="sites.length"
        v-model="statusFilter"
        :stats="stats"
        :result-count="searching ? filtered.length : null"
        :show-drag-hint="canEdit"
      />

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
        <el-button @click="resetFilter">显示全部站点</el-button>
      </el-empty>

      <!-- 搜索、筛选或只读模式下不能拖拽排序 -->
      <div v-else-if="searching || !canEdit" class="grid">
        <Site
          v-for="site in filtered"
          :key="site.id"
          :site="site"
          :draggable="false"
          :editable="canEdit"
          @edit="openEdit"
          @delete="remove"
          @updated="replaceSite"
        />
      </div>

      <VueDraggable
        v-else
        v-model="sites"
        class="grid"
        :animation="180"
        handle=".drag-handle"
        ghost-class="drag-ghost"
        @start="dragging = true"
        @end="saveSort"
      >
        <Site v-for="site in sites" :key="site.id" :site="site" @edit="openEdit" @delete="remove" @updated="replaceSite" />
      </VueDraggable>
    </main>

    <template v-if="canEdit">
      <el-button class="fab nav-gradient-btn" type="primary" :icon="Plus" circle aria-label="添加站点" @click="openAdd" />
      <SiteDialog ref="dialogRef" @saved="onSaved" />
    </template>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.main {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 24px 100px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.skeleton {
  padding: 16px;
  border-radius: 16px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
  backdrop-filter: blur(14px);
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
  box-shadow: 0 10px 30px -8px var(--nav-accent-glow);
}

@media (max-width: 760px) {
  .main {
    padding: 14px 14px 100px;
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
