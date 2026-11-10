<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { auth, login } from '@/composables/auth.js'

const emit = defineEmits(['done'])
const props = defineProps({ cancelable: { type: Boolean, default: false } })

const password = ref('')
const remember = ref(true)
const loading = ref(false)
const input = ref()

onMounted(() => input.value?.focus())

async function submit() {
  if (!password.value) {
    input.value?.focus()
    return
  }
  loading.value = true
  try {
    await login(password.value, remember.value)
    ElMessage.success('登录成功')
    emit('done')
  } catch {
    password.value = ''
    input.value?.focus()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-mask">
    <form class="login-card" @submit.prevent="submit">
      <svg class="logo" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" />
        <path d="M15.5 8.5 13.4 13.4 8.5 15.5 10.6 10.6Z" fill="currentColor" />
      </svg>
      <h1 class="brand-text">皮特导航</h1>
      <p class="sub">请输入访问密码</p>
      <el-input
        ref="input"
        v-model="password"
        type="password"
        size="large"
        :prefix-icon="Lock"
        placeholder="访问密码"
        show-password
        autocomplete="current-password"
      />
      <div class="row">
        <el-checkbox v-model="remember">30 天内免登录</el-checkbox>
      </div>
      <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit nav-gradient-btn">登录</el-button>
      <el-button v-if="props.cancelable" text class="cancel" @click="emit('done')">
        {{ auth.guestView ? '以访客身份浏览' : '取消' }}
      </el-button>
    </form>
  </div>
</template>

<style scoped>
.login-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  /* 透出页面的网格背景，并模糊下方内容（访客模式下点击登录时） */
  background: color-mix(in srgb, var(--nav-bg) 70%, transparent);
  backdrop-filter: blur(10px);
}

.login-card {
  width: 100%;
  max-width: 360px;
  padding: 36px 32px 28px;
  border-radius: 20px;
  background: var(--nav-card-bg);
  border: 1px solid var(--nav-border);
  box-shadow: var(--nav-shadow-hover);
  backdrop-filter: blur(18px) saturate(140%);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: center;
}

.logo {
  width: 44px;
  height: 44px;
  margin: 0 auto 10px;
  color: var(--nav-accent);
  filter: drop-shadow(0 0 10px var(--nav-accent-glow));
}

h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0.06em;
}

.brand-text {
  background: var(--nav-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.sub {
  margin: 6px 0 22px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.row {
  margin: 10px 0 18px;
  text-align: left;
}

.submit {
  width: 100%;
}

.cancel {
  margin: 10px 0 0 !important;
}
</style>
