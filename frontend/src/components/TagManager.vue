<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import { Delete, Plus, Rank } from '@element-plus/icons-vue'
import * as api from '@/service/api.js'
import { SUGGESTED_TAGS, TAG_COLORS, useTags } from '@/composables/tags.js'

const props = defineProps({
  // 用于显示每个标签下的站点数量
  sites: { type: Array, default: () => [] },
})
const emit = defineEmits(['changed'])

const { tags, loadTags } = useTags()
const visible = ref(false)
const list = ref([])
const newName = ref('')
const newInput = ref()
const adding = ref(false)

const counts = computed(() => {
  const m = new Map()
  props.sites.forEach((s) => (s.tags || []).forEach((t) => m.set(t.id, (m.get(t.id) || 0) + 1)))
  return m
})
const existing = computed(() => new Set(tags.value.map((t) => t.name.toLowerCase())))
const suggestions = computed(() => SUGGESTED_TAGS.filter((n) => !existing.value.has(n.toLowerCase())))

function syncList() {
  list.value = tags.value.map((t) => ({ ...t, draft: t.name }))
}

async function open() {
  await loadTags()
  syncList()
  newName.value = ''
  visible.value = true
  nextTick(() => newInput.value?.focus())
}

async function refresh() {
  await loadTags()
  syncList()
  emit('changed')
}

async function add(name = newName.value) {
  const v = (name || '').trim()
  if (!v || adding.value) return
  adding.value = true
  try {
    await api.saveTag({ name: v })
    if (name === newName.value) newName.value = ''
    await refresh()
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    adding.value = false
  }
}

async function rename(item) {
  const v = item.draft.trim()
  if (!v || v === item.name) {
    item.draft = item.name
    return
  }
  try {
    await api.saveTag({ id: item.id, name: v })
    ElMessage.success({ message: '标签已重命名', grouping: true })
    await refresh()
  } catch {
    item.draft = item.name
  }
}

async function recolor(item, color) {
  if (!color) return
  try {
    await api.saveTag({ id: item.id, name: item.name, color })
    await refresh()
  } catch {
    /* 已在拦截器中提示 */
  }
}

async function remove(item) {
  try {
    await api.deleteTag(item.id)
    ElMessage.success(`已删除标签「${item.name}」`)
    await refresh()
  } catch {
    /* 已在拦截器中提示 */
  }
}

async function saveOrder() {
  try {
    await api.sortTags(list.value.map((t) => t.id))
    await refresh()
  } catch {
    await refresh()
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="管理标签" width="460px" class="site-dialog tag-manager" align-center>
    <p class="intro">标签用于把站点分组显示，一个站点可以有多个标签。拖动调整分组顺序。</p>

    <div class="add-row">
      <el-input
        ref="newInput"
        v-model="newName"
        placeholder="新标签名称，例如：影音"
        maxlength="20"
        @keyup.enter="add()"
      />
      <el-button class="nav-gradient-btn" type="primary" :icon="Plus" :loading="adding" @click="add()">添加</el-button>
    </div>

    <div v-if="suggestions.length" class="suggest">
      <span>常用：</span>
      <button v-for="n in suggestions" :key="n" class="suggest-chip" @click="add(n)">+ {{ n }}</button>
    </div>

    <el-empty v-if="!list.length" description="还没有标签" :image-size="60" />

    <VueDraggable v-else v-model="list" class="tag-list" handle=".handle" :animation="160" @end="saveOrder">
      <div v-for="item in list" :key="item.id" class="tag-row">
        <span class="handle" aria-label="拖动排序"><el-icon><Rank /></el-icon></span>
        <el-color-picker
          :model-value="item.color"
          :predefine="TAG_COLORS"
          size="small"
          @change="(c) => recolor(item, c)"
        />
        <el-input
          v-model="item.draft"
          class="name"
          size="small"
          maxlength="20"
          @blur="rename(item)"
          @keyup.enter="$event.target.blur()"
        />
        <span class="count" :title="`${counts.get(item.id) || 0} 个站点`">{{ counts.get(item.id) || 0 }}</span>
        <el-popconfirm
          :title="`删除标签「${item.name}」？站点不会被删除，只会移除该标签`"
          confirm-button-text="删除"
          cancel-button-text="取消"
          confirm-button-type="danger"
          width="240"
          @confirm="remove(item)"
        >
          <template #reference>
            <button class="del" aria-label="删除标签"><el-icon><Delete /></el-icon></button>
          </template>
        </el-popconfirm>
      </div>
    </VueDraggable>
  </el-dialog>
</template>

<style scoped>
.intro {
  margin: -6px 0 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add-row {
  display: flex;
  gap: 8px;
}

.suggest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.suggest-chip {
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px dashed var(--nav-border-strong);
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
}

.suggest-chip:hover {
  border-color: var(--nav-accent);
  color: var(--nav-accent);
}

.tag-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 16px;
  max-height: 50vh;
  overflow-y: auto;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 10px;
  border: 1px solid var(--nav-border);
  background: var(--nav-icon-bg);
}

.handle {
  display: inline-flex;
  color: var(--el-text-color-placeholder);
  cursor: grab;
}

.name {
  flex: 1;
}

.count {
  min-width: 24px;
  text-align: center;
  font-family: var(--nav-mono);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.del:hover {
  color: var(--el-color-danger);
  background: var(--nav-icon-bg);
}
</style>
