import { ref, watchEffect } from 'vue'

const KEY = 'navigator-theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')
const saved = localStorage.getItem(KEY)
const isDark = ref(saved ? saved === 'dark' : media.matches)

watchEffect(() => document.documentElement.classList.toggle('dark', isDark.value))

// 用户未手动选择时跟随系统
media.addEventListener('change', (e) => {
  if (!localStorage.getItem(KEY)) isDark.value = e.matches
})

export function useTheme() {
  const toggleTheme = () => {
    isDark.value = !isDark.value
    localStorage.setItem(KEY, isDark.value ? 'dark' : 'light')
  }
  return { isDark, toggleTheme }
}
