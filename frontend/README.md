# 皮特导航 · 前端

Vue 3 + Element Plus。本目录原为独立仓库 [Navigator_front](https://github.com/manone2077/Navigator_front)，现已合并到 Navigator 仓库，部署与功能说明见[根目录 README](../README.md)。

```shell
yarn install
yarn dev      # 开发模式，/api 与 /images 代理到 http://127.0.0.1:8000
yarn build    # 构建到 frontend/dist；Docker 构建时会自动构建前端，无需手动执行
```

目录结构：

- `src/components/`：`Navigator`（页面）、`TopBar`（顶部栏）、`StatusFilter`（状态筛选）、`Site`（站点卡片）、`SiteDialog`（添加/编辑）、`Login`
- `src/composables/`：`useSites`（站点数据与操作）、`auth`（登录状态）、`theme`（深色模式）、`site`（工具函数）
- `src/service/`：接口请求；所有请求会带上 `X-Requested-With` 请求头，后端据此防止跨站请求伪造
