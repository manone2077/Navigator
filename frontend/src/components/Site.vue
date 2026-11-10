<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Delete, EditPen, Loading, Rank } from '@element-plus/icons-vue'
import { avatarColor, faviconOf, hostOf, latencyInfo, timeAgo } from '@/composables/site.js'
import { speedtestOne } from '@/service/api.js'

const props = defineProps({
  site: { type: Object, required: true },
  draggable: { type: Boolean, default: true },
  editable: { type: Boolean, default: true },
  // 平铺视图下在卡片上显示标签；分组视图中由分组标题体现
  showTags: Boolean,
})
const emit = defineEmits(['edit', 'delete', 'updated'])

// 点击延迟标签重新测速
const testing = ref(false)
async function retest() {
  if (!props.editable || testing.value) return
  testing.value = true
  try {
    emit('updated', await speedtestOne(props.site.id))
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    testing.value = false
  }
}

async function copyUrl() {
  try {
    await navigator.clipboard.writeText(props.site.url)
  } catch {
    // 非 https 页面下 clipboard API 不可用，退回旧方式
    const ta = Object.assign(document.createElement('textarea'), { value: props.site.url })
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    ta.remove()
  }
  ElMessage.success({ message: '地址已复制', grouping: true })
}

// 图标：自定义/服务端获取的图标 -> 站点 favicon -> 文字头像
const iconFailed = ref(false)
// 已知不可达的站点不再让浏览器去请求 favicon，直接显示文字头像
const iconSrc = computed(() => props.site.icon || (props.site.status >= 88888 ? '' : faviconOf(props.site.url)))
watch(iconSrc, () => (iconFailed.value = false))

const latency = computed(() => latencyInfo(props.site.status, props.site.statusMsg))
const latencyTip = computed(() => {
  const ago = timeAgo(props.site.checkedAt)
  const base = ago ? `${latency.value.tip}（${ago}测速）` : latency.value.tip
  return props.editable ? `${base}，点击重新测速` : base
})
const host = computed(() => hostOf(props.site.url))
const letter = computed(() => (props.site.title || host.value || '?').trim().charAt(0).toUpperCase())
// 信号格数：good 4 / ok 3 / slow 2 / bad、未测速 0
const bars = computed(() => ({ good: 4, ok: 3, slow: 2 })[latency.value.level] ?? 0)

// 鼠标跟随的聚光效果：记录鼠标在卡片内的位置
function onMove(e) {
  const r = e.currentTarget.getBoundingClientRect()
  e.currentTarget.style.setProperty('--mx', `${e.clientX - r.left}px`)
  e.currentTarget.style.setProperty('--my', `${e.clientY - r.top}px`)
}

// 描述与名称相同（如 PVE / PVE）时不重复显示
const description = computed(() => {
  const c = (props.site.content || '').trim()
  return c && c.toLowerCase() !== (props.site.title || '').trim().toLowerCase() ? c : ''
})
</script>

<template>
  <a
    class="site-card"
    :class="`is-${latency.level}`"
    :href="site.url"
    target="_blank"
    rel="noopener noreferrer"
    :title="site.url"
    @mousemove="onMove"
  >
    <div class="card-main">
      <div class="icon" :class="{ 'has-img': iconSrc && !iconFailed }">
        <img v-if="iconSrc && !iconFailed" :src="iconSrc" alt="" loading="lazy" @error="iconFailed = true" />
        <span v-else class="letter" :style="{ background: avatarColor(site.title) }">{{ letter }}</span>
      </div>
      <div class="titles">
        <div class="title">{{ site.title }}</div>
        <div class="host">{{ host }}</div>
      </div>
    </div>

    <p v-if="description" class="content">{{ description }}</p>

    <div v-if="showTags && site.tags && site.tags.length" class="tags">
      <span v-for="t in site.tags" :key="t.id" class="tag" :style="{ '--tag': t.color }"><i />{{ t.name }}</span>
    </div>

    <div class="card-foot">
      <el-tooltip :content="latencyTip" placement="top" :show-after="200">
        <span class="latency" :class="[latency.level, { clickable: editable }]" @click.prevent.stop="retest">
          <el-icon v-if="testing" class="is-loading"><Loading /></el-icon>
          <span v-else class="signal" :class="`bars-${bars}`" aria-hidden="true"><i /><i /><i /><i /></span>
          <span class="latency-text">{{ testing ? '测速中' : latency.text }}</span>
        </span>
      </el-tooltip>
      <span class="pulse" :class="latency.level" aria-hidden="true" />
    </div>

    <div class="actions" @click.prevent.stop>
      <el-tooltip content="复制地址" placement="top" :show-after="400">
        <button class="act" aria-label="复制地址" @click="copyUrl"><el-icon><CopyDocument /></el-icon></button>
      </el-tooltip>
      <template v-if="editable">
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
      </template>
    </div>
  </a>
</template>

<style scoped>
.site-card {
  --status: var(--nav-muted);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 16px 14px;
  border-radius: 16px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
  box-shadow: var(--nav-shadow);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  color: inherit;
  text-decoration: none;
  overflow: hidden;
  isolation: isolate;
  min-width: 0;
  transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 0.25s ease, border-color 0.25s ease;
}

.site-card.is-good { --status: var(--nav-ok); }
.site-card.is-ok { --status: var(--nav-accent); }
.site-card.is-slow { --status: var(--nav-warn); }
.site-card.is-bad { --status: var(--nav-bad); }

/* 顶部状态细线 */
.site-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 16px;
  right: 16px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--status), transparent);
  opacity: 0.55;
  transition: opacity 0.25s ease;
}

/* 鼠标跟随聚光 */
.site-card::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: radial-gradient(260px circle at var(--mx, 50%) var(--my, 0%), var(--nav-spotlight), transparent 70%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.site-card:hover {
  transform: translateY(-3px);
  border-color: transparent;
  box-shadow: var(--nav-shadow-hover);
}

.site-card:hover::before {
  opacity: 1;
}

.site-card:hover::after {
  opacity: 1;
}

/* 悬停时的渐变描边 */
.site-card:hover .card-main::before {
  opacity: 1;
}

.card-main::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 16px;
  padding: 1px;
  background: linear-gradient(135deg, var(--nav-accent), transparent 40%, transparent 60%, var(--nav-accent-2));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s ease;
}

.card-main {
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
  box-shadow: inset 0 0 0 1px var(--nav-border);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.icon img {
  width: 30px;
  height: 30px;
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
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.titles {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.host {
  margin-top: 2px;
  font-size: 12px;
  font-family: var(--nav-mono);
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.content {
  margin: -2px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: -4px;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 20px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  background: color-mix(in srgb, var(--tag) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--tag) 30%, transparent);
}

.tag i {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--tag);
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--nav-border);
}

.latency {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-family: var(--nav-mono);
  color: var(--status, var(--nav-muted));
  border-radius: 6px;
  padding: 2px 6px;
  margin-left: -6px;
}

.latency.clickable {
  cursor: pointer;
}

.latency.clickable:hover {
  background: var(--nav-icon-bg);
}

.latency.good { color: var(--nav-ok); }
.latency.ok { color: var(--nav-accent); }
.latency.slow { color: var(--nav-warn); }
.latency.bad { color: var(--nav-bad); }
.latency.none { color: var(--nav-muted); }

/* 信号格 */
.signal {
  display: inline-flex;
  align-items: flex-end;
  gap: 2px;
  height: 12px;
}

.signal i {
  width: 3px;
  border-radius: 1px;
  background: currentColor;
  opacity: 0.2;
}

.signal i:nth-child(1) { height: 4px; }
.signal i:nth-child(2) { height: 6px; }
.signal i:nth-child(3) { height: 9px; }
.signal i:nth-child(4) { height: 12px; }

.bars-1 i:nth-child(-n + 1),
.bars-2 i:nth-child(-n + 2),
.bars-3 i:nth-child(-n + 3),
.bars-4 i:nth-child(-n + 4) {
  opacity: 1;
}

/* 在线状态点：可访问时呼吸闪烁 */
.pulse {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--status);
  box-shadow: 0 0 8px var(--status);
}

.pulse.none {
  box-shadow: none;
  opacity: 0.6;
}

.pulse.good::after,
.pulse.ok::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--status);
  animation: ping 2.4s cubic-bezier(0, 0, 0.2, 1) infinite;
}

@keyframes ping {
  0% { transform: scale(1); opacity: 0.6; }
  70%, 100% { transform: scale(2.6); opacity: 0; }
}

/* 操作按钮悬浮在右上角，不占用标题和地址的宽度 */
.actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: var(--nav-card-solid);
  border: 1px solid var(--nav-border);
  box-shadow: 0 6px 20px -8px rgba(0, 0, 0, 0.35);
  opacity: 0;
  transform: translateY(-4px);
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.site-card:hover .actions,
.site-card:focus-within .actions {
  opacity: 1;
  transform: none;
  pointer-events: auto;
}

/* 触屏设备没有 hover，始终显示操作按钮 */
@media (hover: none) {
  .actions {
    opacity: 1;
    transform: none;
    pointer-events: auto;
    box-shadow: none;
    top: 8px;
    right: 8px;
    padding: 1px;
    background: transparent;
    border-color: transparent;
  }

  .act {
    width: 26px;
    height: 26px;
    font-size: 14px;
  }
}

.act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 15px;
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
</style>
