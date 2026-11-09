<script setup>
import { Rank } from '@element-plus/icons-vue'

const filter = defineModel({ type: String, default: 'all' })
defineProps({
  stats: { type: Object, required: true },
  // 正在搜索或筛选时显示结果数量，否则显示拖拽排序提示
  resultCount: { type: Number, default: null },
  showDragHint: Boolean,
})

const toggle = (f) => (filter.value = filter.value === f ? 'all' : f)
</script>

<template>
  <div class="summary">
    <button class="chip" :class="{ active: filter === 'all' }" @click="filter = 'all'">
      全部 <b>{{ stats.total }}</b>
    </button>
    <button v-if="stats.online" class="chip ok" :class="{ active: filter === 'ok' }" @click="toggle('ok')">
      可访问 <b>{{ stats.online }}</b>
    </button>
    <button v-if="stats.bad" class="chip bad" :class="{ active: filter === 'bad' }" @click="toggle('bad')">
      异常 <b>{{ stats.bad }}</b>
    </button>
    <button v-if="stats.untested" class="chip" :class="{ active: filter === 'untested' }" @click="toggle('untested')">
      未测速 <b>{{ stats.untested }}</b>
    </button>
    <span v-if="stats.avg != null" class="avg">平均延迟 <b>{{ stats.avg }}</b> ms</span>
    <span v-if="resultCount != null" class="hint">· 显示 {{ resultCount }} 个</span>
    <span v-else-if="showDragHint" class="hint">· 拖动卡片右上角的 <el-icon><Rank /></el-icon> 可调整顺序</span>
  </div>
</template>

<style scoped>
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

.hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 760px) {
  .hint {
    display: none;
  }
}
</style>
