<script setup>
import { computed, ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import Site from '@/components/Site.vue'

const props = defineProps({
  // 标签；为 null 表示“未分组”
  tag: { type: Object, default: null },
  sites: { type: Array, required: true },
  draggable: Boolean,
  editable: Boolean,
})
const emit = defineEmits(['reorder', 'edit', 'delete', 'updated', 'drag-start'])

// 折叠状态保存在浏览器中
const storeKey = computed(() => `navigator-group-collapsed-${props.tag ? props.tag.id : 'untagged'}`)
const collapsed = ref(localStorage.getItem(storeKey.value) === '1')
function toggle() {
  collapsed.value = !collapsed.value
  localStorage.setItem(storeKey.value, collapsed.value ? '1' : '0')
}

// 拖拽需要可写的列表
const list = ref([...props.sites])
watch(() => props.sites, (v) => (list.value = [...v]))
</script>

<template>
  <section class="group" :style="{ '--tag': tag ? tag.color : 'var(--nav-muted)' }">
    <button class="group-head" :aria-expanded="!collapsed" @click="toggle">
      <el-icon class="chevron" :class="{ open: !collapsed }"><ArrowRight /></el-icon>
      <i class="tag-dot" />
      <span class="name">{{ tag ? tag.name : '未分组' }}</span>
      <span class="count">{{ sites.length }}</span>
      <span class="line" />
    </button>

    <div v-show="!collapsed">
      <VueDraggable
        v-if="draggable"
        v-model="list"
        class="grid"
        :animation="180"
        handle=".drag-handle"
        ghost-class="drag-ghost"
        @start="emit('drag-start')"
        @end="emit('reorder', list.map((s) => s.id))"
      >
        <Site
          v-for="site in list"
          :key="site.id"
          :site="site"
          :editable="editable"
          @edit="(s) => emit('edit', s)"
          @delete="(s) => emit('delete', s)"
          @updated="(s) => emit('updated', s)"
        />
      </VueDraggable>
      <div v-else class="grid">
        <Site
          v-for="site in sites"
          :key="site.id"
          :site="site"
          :draggable="false"
          :editable="editable"
          @edit="(s) => emit('edit', s)"
          @delete="(s) => emit('delete', s)"
          @updated="(s) => emit('updated', s)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.group + .group {
  margin-top: 22px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 12px;
  padding: 0;
  border: none;
  background: none;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.chevron {
  color: var(--el-text-color-secondary);
  transition: transform 0.2s ease;
}

.chevron.open {
  transform: rotate(90deg);
}

.tag-dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  background: var(--tag);
  box-shadow: 0 0 10px var(--tag);
}

.count {
  font-family: var(--nav-mono);
  font-size: 12px;
  font-weight: 500;
  padding: 0 7px;
  border-radius: 999px;
  color: var(--el-text-color-secondary);
  background: var(--nav-icon-bg);
}

.line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--tag) 45%, transparent), transparent);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

:deep(.drag-ghost) {
  opacity: 0.4;
  border: 1px dashed var(--el-color-primary);
}

@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
