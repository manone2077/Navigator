![Static Badge](https://img.shields.io/badge/%E7%9A%AE%E7%89%B9%E5%AF%BC%E8%88%AA-%E7%AB%99%E7%82%B9%E7%AE%A1%E7%90%86%E7%A5%9E%E5%99%A8-brightgreen?logoColor=%2375c74b)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/manone2077/Navigator/.github%2Fworkflows%2Fdocker-image.yml)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/manone2077/Navigator)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues-pr/manone2077/Navigator)
![GitHub Tag](https://img.shields.io/github/v/tag/manone2077/Navigator)

# 皮特导航

Navigator for website management

## 功能

- 站点卡片展示：自动获取 favicon（失败时显示文字头像，也可上传自定义图标）
- 添加 / 编辑 / 单个删除 / 删除全部站点
- 拖拽排序（排序会保存到服务端）
- 搜索过滤（按 `/` 快速聚焦搜索框，只有一个结果时回车直接打开）
- 测速：通过 TCP 连接站点端口测量延迟，后台每 10 分钟自动测速一次
- 备份：导出 / 导入 JSON 备份文件（包含图标）
- 图标：服务端自动获取站点图标（支持自签名证书、非根路径图标），填写地址后自动填写站点名称
- 测速失败原因提示（连接被拒绝 / 无法路由 / 域名无法解析 / 超时），点击延迟标签可单独重新测速
- 按状态筛选（可访问 / 异常 / 未测速）
- 可选访问密码与访客只读模式
- 深色模式（默认跟随系统）、移动端适配
- 镜像同时支持 `linux/amd64` 与 `linux/arm64`

## Todo

1. 分类
2. 直接添加PT 站点
3. 支持代理访问[in progress]
4. 支持单个删除[√]
5. 备份（导入、导出）[√]
6. docker 自动部署[√]

## Docker 部署
```shell
docker run -d -p 9000:80 --name MyNavigator -v /config:/app/data greatpower/navigator:latest
```

### 访问密码（可选）

默认不需要登录。如需对外开放访问，建议设置访问密码：

```shell
docker run -d -p 9000:80 --name MyNavigator -v /config:/app/data \
  -e NAV_PASSWORD=你的密码 \
  -e NAV_GUEST_VIEW=true \
  greatpower/navigator:latest
```

| 环境变量 | 说明 |
| --- | --- |
| `NAV_PASSWORD` | 访问密码，未设置时不启用登录 |
| `NAV_GUEST_VIEW` | `true` 时未登录用户可以浏览站点（只读），默认 `false` 必须登录 |
| `JAVA_OPTS` | JVM 参数，默认 `-XX:+UseSerialGC -Xss512k -Xms32m -Xmx192m` |
| `TZ` | 时区，默认 `Asia/Shanghai` |

镜像内置健康检查（`/api/health`），可在 Portainer / `docker ps` 中查看容器健康状态。

## 开发相关
0. 前端项目为 [Navigator_front](https://github.com/manone2077/Navigator_front)。GitHub Actions 构建镜像时会自动拉取并构建前端（优先使用与本次版本同名的前端标签，否则使用 master），`docker/dist` 不再提交到仓库。
   本地构建镜像：将两个项目放在同一目录下，在前端项目执行 `yarn build`（产物输出到本项目的 `docker/dist`），再执行 `./mvnw package && docker build .`
   后端接口测试：`./mvnw test`（CI 中构建时会自动运行）
1. 本项目使用git 进行版本管理
2. 主要分为`master、dev、release` 三个分支，
   1. [dev](https://github.com/manone2077/Navigator/tree/dev) 在 dev 分支上创建功能(` git checkout -b feature/your-feature-name`)分支进行开发。
   2. [release](https://github.com/manone2077/Navigator/tree/release) 从 dev 分支创建一个 release 分支,通过 release 分支进行最后的测试和准备发布(调整需要使用的配置文件等)。
   3. [master](https://github.com/manone2077/Navigator) 用作部署(只用来合并`release` 分支代码)
   4. 推送 `release/vX.Y.Z` 分支或 `vX.Y.Z` 标签会触发 GitHub Actions 构建并推送镜像 `navigator:vX.Y.Z` 与 `navigator:latest`（同时推送分支和标签时只会构建一次，建议使用 `git push --atomic origin release/vX.Y.Z vX.Y.Z`）
   5. 最后，将 release 分支合并回 dev 分支，以确保 dev 分支包含最新的稳定代码`如果只是修改了release 需要使用的配置，则可以不合并`

## 效果如下
访问  http://yourip:9000/   
站点图标由服务端自动获取，如果获取失败（站点本身没有图标），可以在编辑站点时点击“自动获取”重试或手动上传图片
![nav.png](images/nav.png)
