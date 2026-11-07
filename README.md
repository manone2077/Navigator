![Static Badge](https://img.shields.io/badge/%E7%9A%AE%E7%89%B9%E5%AF%BC%E8%88%AA-%E7%AB%99%E7%82%B9%E7%AE%A1%E7%90%86%E7%A5%9E%E5%99%A8-brightgreen?logoColor=%2375c74b)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/manone2077/Navigator/.github%2Fworkflows%2Fdocker-image.yml)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/manone2077/Navigator)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues-pr/manone2077/Navigator)
![GitHub Tag](https://img.shields.io/github/v/tag/manone2077/Navigator)

# 皮特导航

一个轻量的自托管站点导航，适合放在 NAS / 家庭服务器上管理内网服务（Portainer、PVE、qBittorrent、Transmission……）和常用网站。

![主界面](images/nav.png)

| 深色模式 · 添加站点 | 移动端 |
| --- | --- |
| ![添加站点](images/nav-dialog.png) | <img src="images/nav-mobile.png" width="260" alt="移动端"> |

## 功能

- **站点卡片**：完整显示 `IP:端口`，悬停显示复制地址、编辑、删除、拖拽排序按钮
- **自动获取站点信息**：填写地址后自动获取站点名称和图标。图标由服务端获取，支持自签名证书（如 Portainer、PVE）和不在根路径的图标（如 qBittorrent），获取失败时显示文字头像，也可手动上传
- **地址补全**：只输入 `192.168.1.2:8080` 时自动补全为 `http://`，`443 / 8443 / 9443 / 8006` 端口和公网域名补全为 `https://`
- **测速**：通过 TCP 连接站点端口测量延迟，后台每 10 分钟自动测速；失败时提示原因（连接被拒绝 / 无法路由到主机 / 域名无法解析 / 超时）；点击延迟标签可单独重新测速
- **筛选与搜索**：按状态筛选（可访问 / 异常 / 未测速）；按 `/` 聚焦搜索框，只有一个结果时回车直接打开
- **拖拽排序**：排序保存在服务端，多设备一致
- **备份**：导出 / 导入 JSON 备份文件，图标一并导出，可迁移到其它实例
- **访问密码（可选）**：对外开放时可设置密码，并可选择允许访客只读浏览
- **深色模式**（默认跟随系统）、移动端适配
- 镜像约 200MB，内存占用约 200MB，支持 `linux/amd64` 与 `linux/arm64`，内置健康检查

## 部署

### Docker

```shell
docker run -d --name MyNavigator --restart always \
  -p 9000:80 \
  -v /your/path/config:/app/data \
  greatpower/navigator:latest
```

访问 `http://你的IP:9000/` 即可。

### Docker Compose / Portainer Stack

```yaml
services:
  mynavigator:
    image: greatpower/navigator:latest
    container_name: MyNavigator
    restart: always
    ports:
      - "9000:80"
    volumes:
      - /your/path/config:/app/data
    # environment:
    #   - NAV_PASSWORD=你的密码   # 可选：启用访问密码
    #   - NAV_GUEST_VIEW=true     # 可选：未登录也可浏览（只读）
```

### 环境变量

| 环境变量 | 说明 |
| --- | --- |
| `NAV_PASSWORD` | 访问密码，未设置时不需要登录（默认） |
| `NAV_GUEST_VIEW` | `true` 时未登录用户可以浏览站点（只读）；默认 `false`，必须登录才能访问 |
| `JAVA_OPTS` | JVM 参数，默认 `-XX:+UseSerialGC -Xss512k -Xms32m -Xmx192m` |
| `TZ` | 时区，默认 `Asia/Shanghai` |

登录状态保存在 Cookie 中（可选 30 天内免登录），修改密码后所有已登录的设备需要重新登录。同一 IP 连续输错 5 次密码会被锁定 1 分钟。

### 数据目录

`/app/data` 下保存所有数据，备份或迁移时复制整个目录即可：

```
/app/data
├── db/navdb.mv.db        # 站点数据（H2 数据库）
└── public/images/        # 站点图标
```

### 升级

拉取最新镜像并重建容器即可，数据会自动升级：

- Docker：`docker pull greatpower/navigator:latest`，然后删除并重新创建容器（使用相同的数据目录）
- Portainer：在 Stack 中点击 **Update the stack** 并勾选 **Re-pull image**（或在容器页面使用 **Recreate** 并勾选 **Pull latest image**）

建议升级前在页面右上角「…」菜单中 **导出备份**，或复制一份数据目录。

## 常见问题

**图标显示为文字头像？**
站点本身没有图标，或服务端无法访问该站点时会显示文字头像。可以在编辑站点时点击「自动获取」重试，或手动上传图片；「…」菜单中的「重新获取图标」会为所有未手动上传图标的站点重新获取。

**站点显示「不可达」或「超时」，但浏览器能打开？**
测速和获取图标都是由容器发起的，鼠标悬停在延迟标签上可以看到具体原因。请确认容器所在网络能访问该地址（例如不同网段、防火墙限制、使用了只有本机才能解析的域名等）。

**忘记密码？**
密码就是 `NAV_PASSWORD` 环境变量，修改后重建容器即可。

## Todo

1. 分类
2. 直接添加 PT 站点
3. 支持代理访问 [in progress]
4. 支持单个删除 [√]
5. 备份（导入、导出）[√]
6. docker 自动部署 [√]
7. 访问密码 [√]

## 开发相关

- 前端项目：[Navigator_front](https://github.com/manone2077/Navigator_front)（Vue 3 + Element Plus），后端：Spring Boot 3 + H2，镜像中由 nginx 提供前端页面并代理 `/api`
- GitHub Actions 构建镜像时会自动拉取并构建前端（优先使用与本次版本同名的前端标签，否则使用 master），`docker/dist` 不提交到仓库
- 本地构建镜像：将两个项目放在同一目录下，先在前端项目执行 `yarn build`（产物输出到本项目的 `docker/dist`），再执行 `./mvnw package && docker build .`
  - 国内网络可加速 apk 下载：`docker build --build-arg APK_MIRROR=https://mirrors.aliyun.com .`
- 接口测试：`./mvnw test`（CI 构建时会自动运行）

### 分支与发布

1. 主要分为 `master、dev、release` 三个分支：
   1. [dev](https://github.com/manone2077/Navigator/tree/dev)：在 dev 分支上创建功能分支（`git checkout -b feature/your-feature-name`）进行开发
   2. release：从 dev 分支创建 `release/vX.Y.Z` 分支，进行最后的测试和准备发布
   3. [master](https://github.com/manone2077/Navigator)：用作部署，只用来合并 release 分支代码
2. 推送 `release/vX.Y.Z` 分支或 `vX.Y.Z` 标签会触发 GitHub Actions 构建并推送镜像 `navigator:vX.Y.Z` 与 `navigator:latest`
   - 同时推送分支和标签时只会构建一次：`git push --atomic origin release/vX.Y.Z vX.Y.Z`
   - 前端有改动时，先给前端项目打上同名标签再发布
3. 最后将 release 分支合并回 master 和 dev
