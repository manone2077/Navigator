import { createRouter, createWebHistory } from 'vue-router'
import Navigator from "@/components/Navigator.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Navigator
    }
  ]
})

export default router
