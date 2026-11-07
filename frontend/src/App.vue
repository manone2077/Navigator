<script setup>
import { onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import Login from '@/components/Login.vue'
import { auth, refreshAuth } from '@/composables/auth.js'
import { setUnauthorizedHandler } from '@/service/http.js'

// 主动点击“登录”时显示的登录框（访客模式下可以取消）
const showLogin = ref(false)

setUnauthorizedHandler(() => {
  auth.loggedIn = false
})

onMounted(refreshAuth)
</script>

<template>
  <template v-if="auth.checked">
    <!-- 启用密码且不允许访客浏览时，必须先登录 -->
    <Login v-if="auth.enabled && !auth.loggedIn && !auth.guestView" />
    <template v-else>
      <RouterView @login="showLogin = true" />
      <Login v-if="showLogin && !auth.loggedIn" cancelable @done="showLogin = false" />
    </template>
  </template>
</template>
