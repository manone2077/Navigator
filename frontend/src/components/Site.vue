<script setup>
import { computed, ref, watch } from 'vue'
import { Delete, EditPen, Rank } from '@element-plus/icons-vue'
import { avatarColor, faviconOf, hostOf, latencyInfo } from '@/composables/site.js'

const props = defineProps({
  site: { type: Object, required: true },
  draggable: { type: Boolean, default: true },
})
const emit = defineEmits(['edit', 'delete'])

// 图标：自定义图标 -> 站点 favicon -> 文字头像
const iconFailed = ref(false)
const iconSrc = computed(() => props.site.icon || faviconOf(props.site.url))
watch(iconSrc, () => (iconFailed.value = false))

const latency = computed(() => latencyInfo(props.site.status))
const host = computed(() => hostOf(props.site.url))
const letter = computed(() => (props.site.title || host.value || '?').trim().charAt(0).toUpperCase())
</script>

<template>
  <a class="site-card" :href="site.url" target="_blank" rel="noopener noreferrer" :title="site.url">
    <div class="card-top">
      <div class="icon">
        <img v-if="iconSrc && !iconFailed" :src="iconSrc" alt="" loading="lazy" @error="iconFailed = true" />
        <span v-else class="letter" :style="{ background: avatarColor(site.title) }">{{ letter }}</span>
      </div>
      <div class="titles">
        <div class="title">{{ site.title }}</div>
        <div class="host">{{ host }}</div>
      </div>
      <div class="actions" @click.prevent.stop>
        <el-tooltip content="编辑" placement="top" :show-after="400">
          <button class="act" aria-label="编辑" @click="emit('edit', site)"><el-icon><EditPen /></el-icon></button>
        </el-tooltip>
        <el-popconfirm
          :title="`确定删除「${site.title}」吗？`"
          confirm-button-text="删除"
          cancel-button-text="取消"
          confirm-button-type="danger"
          width="220"
          @confirm="emit('delete', site)"
        >
          <template #reference>
            <button class="act danger" aria-label="删除"><el-icon><Delete /></el-icon></button>
          </template>
        </el-popconfirm>
        <el-tooltip v-if="draggable" content="拖动排序" placement="top" :show-after="400">
          <span class="act drag-handle" aria-label="拖动排序"><el-icon><Rank /></el-icon></span>
        </el-tooltip>
      </div>
    </div>
    <p class="content" :class="{ empty: !site.content }">{{ site.content || '暂无描述' }}</p>
    <div class="card-bottom">
      <el-tooltip :content="latency.tip" placement="top" :show-after="200">
        <span class="latency" :class="latency.level"><i class="dot" />{{ latency.text }}</span>
      </el-tooltip>
    </div>
  </a>
</template>

<style scoped>
.site-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 14px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
  color: inherit;
  text-decoration: none;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  min-width: 0;
}

.site-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--nav-shadow-hover);
  border-color: var(--el-color-primary-light-5);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.icon {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--nav-icon-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.icon img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.letter {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 20px;
}

.titles {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.host {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.site-card:hover .actions,
.site-card:focus-within .actions {
  opacity: 1;
}

/* 触屏设备没有 hover，始终显示操作按钮 */
@media (hover: none) {
  .actions {
    opacity: 1;
  }
}

.act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 16px;
}

.act:hover {
  background: var(--nav-icon-bg);
  color: var(--el-color-primary);
}

.act.danger:hover {
  color: var(--el-color-danger);
}

.drag-handle {
  cursor: grab;
}

.content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
  word-break: break-all;
}

.content.empty {
  color: var(--el-text-color-placeholder);
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.latency {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--nav-icon-bg);
  color: var(--el-text-color-secondary);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.latency.good { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.latency.ok { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.latency.slow { color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.latency.bad { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
</style>
