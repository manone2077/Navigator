<script setup>
import { computed } from 'vue'
import { Grid, Menu, Rank } from '@element-plus/icons-vue'

const filter = defineModel({ type: String, default: 'all' })
// 标签筛选：标签 id，null 表示不按标签筛选
const tagFilter = defineModel('tag', { type: Number, default: null })
// 视图：group 分组 / flat 平铺
const view = defineModel('view', { type: String, default: 'group' })
const props = defineProps({
  stats: { type: Object, required: true },
  // 正在搜索或筛选时显示结果数量，否则显示拖拽排序提示
  resultCount: { type: Number, default: null },
  showDragHint: Boolean,
  tags: { type: Array, default: () => [] },
})

const toggleTag = (id) => (tagFilter.value = tagFilter.value === id ? null : id)

const toggle = (f) => (filter.value = filter.value === f ? 'all' : f)
const tested = computed(() => props.stats.online + props.stats.bad)
const ratio = computed(() => (tested.value ? Math.round((props.stats.online / tested.value) * 100) : 0))
// 整体状态：有异常 / 全部正常 / 尚未测速
const overall = computed(() => {
  if (props.stats.bad) return { level: 'bad', text: `${props.stats.bad} 个站点异常` }
  if (props.stats.online) return { level: 'good', text: '所有站点运行正常' }
  return { level: 'none', text: '等待测速' }
})
</script>

<template>
  <section class="status-panel">
    <div class="overview">
      <div class="overall" :class="overall.level">
        <span class="dot" />
        <span>{{ overall.text }}</span>
      </div>
      <div class="metrics">
        <div class="metric">
          <span class="label">在线</span>
          <span class="value"><b>{{ stats.online }}</b>/{{ tested || stats.total }}</span>
        </div>
        <div class="metric">
          <span class="label">平均延迟</span>
          <span class="value"><b>{{ stats.avg ?? '--' }}</b>{{ stats.avg != null ? ' ms' : '' }}</span>
        </div>
      </div>
      <div class="bar" :title="`可访问率 ${ratio}%`"><span :style="{ width: `${ratio}%` }" /></div>
    </div>

    <div class="chips">
      <button class="chip" :class="{ active: filter === 'all' }" @click="filter = 'all'">
        全部 <b>{{ stats.total }}</b>
      </button>
      <button v-if="stats.online" class="chip ok" :class="{ active: filter === 'ok' }" @click="toggle('ok')">
        <i />可访问 <b>{{ stats.online }}</b>
      </button>
      <button v-if="stats.bad" class="chip bad" :class="{ active: filter === 'bad' }" @click="toggle('bad')">
        <i />异常 <b>{{ stats.bad }}</b>
      </button>
      <button v-if="stats.untested" class="chip none" :class="{ active: filter === 'untested' }" @click="toggle('untested')">
        <i />未测速 <b>{{ stats.untested }}</b>
      </button>
      <template v-if="tags.length">
        <span class="sep" />
        <button
          v-for="t in tags"
          :key="t.id"
          class="chip tag"
          :class="{ active: tagFilter === t.id }"
          :style="{ '--tag': t.color }"
          @click="toggleTag(t.id)"
        >
          <i />{{ t.name }}
        </button>
        <span class="sep" />
        <div class="view-toggle" role="group" aria-label="显示方式">
          <button :class="{ active: view === 'group' }" title="按标签分组" @click="view = 'group'">
            <el-icon><Menu /></el-icon>分组
          </button>
          <button :class="{ active: view === 'flat' }" title="平铺显示" @click="view = 'flat'">
            <el-icon><Grid /></el-icon>平铺
          </button>
        </div>
      </template>
      <span v-if="resultCount != null" class="hint">显示 {{ resultCount }} 个</span>
      <span v-else-if="showDragHint" class="hint">拖动卡片右上角的 <el-icon><Rank /></el-icon> 调整顺序</span>
    </div>
  </section>
</template>

<style scoped>
.status-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px 24px;
  margin-bottom: 18px;
  padding: 14px 18px;
  border-radius: 16px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
  box-shadow: var(--nav-shadow);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.overview {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  position: relative;
  padding-bottom: 8px;
}

.overall {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
}

.overall .dot {
  position: relative;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--nav-muted);
}

.overall.good .dot { background: var(--nav-ok); box-shadow: 0 0 10px var(--nav-ok); }
.overall.bad .dot { background: var(--nav-bad); box-shadow: 0 0 10px var(--nav-bad); }

.overall.good .dot::after,
.overall.bad .dot::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: inherit;
  animation: ping 2.4s cubic-bezier(0, 0, 0.2, 1) infinite;
}

@keyframes ping {
  0% { transform: scale(1); opacity: 0.6; }
  70%, 100% { transform: scale(2.8); opacity: 0; }
}

.metrics {
  display: flex;
  gap: 20px;
}

.metric {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric .value {
  font-family: var(--nav-mono);
  color: var(--el-text-color-secondary);
}

.metric b {
  font-size: 16px;
  color: var(--el-text-color-primary);
}

/* 可访问率进度条 */
.bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--nav-icon-bg);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--nav-gradient);
  box-shadow: 0 0 8px var(--nav-accent-glow);
  transition: width 0.6s ease;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid var(--nav-border);
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip b {
  font-family: var(--nav-mono);
  color: var(--el-text-color-primary);
}

.chip i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.chip.ok i { background: var(--nav-ok); }
.chip.bad i { background: var(--nav-bad); }
.chip.none i { background: var(--nav-muted); }

.chip:hover {
  border-color: var(--nav-border-strong);
  color: var(--el-text-color-primary);
}

.chip.active {
  border-color: var(--nav-accent);
  color: var(--nav-accent);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 3px var(--nav-accent-glow);
}

.chip.tag i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: var(--tag);
  box-shadow: 0 0 6px var(--tag);
}

.chip.tag.active {
  border-color: var(--tag);
  color: var(--el-text-color-primary);
  background: color-mix(in srgb, var(--tag) 14%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tag) 22%, transparent);
}

.sep {
  width: 1px;
  height: 16px;
  margin: 0 4px;
  background: var(--nav-border-strong);
}

.view-toggle {
  display: inline-flex;
  padding: 2px;
  border-radius: 999px;
  border: 1px solid var(--nav-border);
}

.view-toggle button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
}

.view-toggle button.active {
  background: var(--nav-gradient);
  color: #fff;
}

.hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 760px) {
  .status-panel {
    padding: 12px 14px;
  }

  .hint,
  .sep {
    display: none;
  }
}
</style>
