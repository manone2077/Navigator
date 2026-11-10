<script setup>
import { ref } from 'vue'
import {
  Delete,
  Download,
  InfoFilled,
  Lightning,
  MoreFilled,
  Moon,
  Picture,
  Plus,
  PriceTag,
  Search,
  Sunny,
  SwitchButton,
  Upload,
  User,
} from '@element-plus/icons-vue'
import { auth, canEdit } from '@/composables/auth.js'
import { useTheme } from '@/composables/theme.js'

const keyword = defineModel('keyword', { type: String, default: '' })
defineProps({ speedLoading: Boolean })
// command: tags / icons / export / import / deleteAll / login / logout / about
const emit = defineEmits(['add', 'speedtest', 'command', 'search-enter'])

const { isDark, toggleTheme } = useTheme()
const searchRef = ref()
defineExpose({ focusSearch: () => searchRef.value?.focus() })
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand">
        <svg class="logo" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" />
          <path d="M15.5 8.5 13.4 13.4 8.5 15.5 10.6 10.6Z" fill="currentColor" />
        </svg>
        <span class="brand-text">皮特导航</span>
      </div>

      <el-input
        ref="searchRef"
        v-model="keyword"
        class="search"
        :prefix-icon="Search"
        placeholder="搜索站点名称、描述或地址"
        clearable
        @keyup.enter="emit('search-enter')"
        @keyup.esc="keyword = ''"
      >
        <template #suffix>
          <kbd v-if="!keyword" class="kbd">/</kbd>
        </template>
      </el-input>

      <div class="tools">
        <template v-if="canEdit">
          <el-button class="add-btn nav-gradient-btn" type="primary" :icon="Plus" @click="emit('add')">
            <span class="btn-text">添加站点</span>
          </el-button>
          <el-button class="speed-btn" :icon="Lightning" :loading="speedLoading" @click="emit('speedtest')">
            <span class="btn-text">{{ speedLoading ? '测速中' : '测速' }}</span>
          </el-button>
        </template>
        <el-button v-else class="login-btn" type="primary" plain :icon="User" aria-label="登录" @click="emit('command', 'login')">
          <span class="btn-text">登录</span>
        </el-button>
        <el-tooltip :content="isDark ? '切换到浅色模式' : '切换到深色模式'" placement="bottom" :show-after="300">
          <el-button :icon="isDark ? Sunny : Moon" circle @click="toggleTheme" />
        </el-tooltip>
        <el-dropdown trigger="click" @command="(cmd) => emit('command', cmd)">
          <el-button :icon="MoreFilled" circle />
          <template #dropdown>
            <el-dropdown-menu>
              <template v-if="canEdit">
                <el-dropdown-item command="tags" :icon="PriceTag">管理标签</el-dropdown-item>
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
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--nav-topbar-bg);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid var(--nav-border);
}

/* 底部一道渐变光线 */
.topbar::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--nav-accent) 30%, var(--nav-accent-2) 70%, transparent);
  opacity: 0.5;
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
  color: var(--nav-accent);
  filter: drop-shadow(0 0 6px var(--nav-accent-glow));
}

.brand-text {
  background: var(--nav-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: 0.04em;
}

.search {
  flex: 1;
  max-width: 420px;
  margin-left: auto;
}

.search :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--nav-card-bg);
  box-shadow: 0 0 0 1px var(--nav-border) inset;
}

.kbd {
  font-family: var(--nav-mono);
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

.version {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
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

  /* 移动端使用右下角的悬浮按钮添加站点 */
  .add-btn {
    display: none;
  }

  .btn-text,
  .kbd {
    display: none;
  }

  .speed-btn,
  .login-btn {
    width: 32px;
    padding: 0;
    border-radius: 50%;
  }
}
</style>
