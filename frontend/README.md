# 皮特导航-前端

Navigator for website management，后端及 Docker 部署见 [Navigator](https://github.com/manone2077/Navigator)

## 开发

```shell
yarn install
yarn dev      # 开发模式，/api 与 /images 代理到 http://127.0.0.1:8000
yarn build    # 构建产物输出到同级目录 ../Navigator/docker/dist（可用 NAV_DIST 环境变量覆盖）
```

## 功能

- 站点卡片：favicon 自动获取，失败时显示文字头像，可上传自定义图标
- 添加 / 编辑 / 单个删除 / 删除全部（表单校验，自动补全 https://，默认使用域名作为名称）
- 拖拽卡片右上角手柄排序，排序保存到服务端
- 搜索过滤，`/` 聚焦搜索，`Esc` 清空，仅一个结果时回车直接打开
- 测速结果分级显示（毫秒数 / 超时 / 不可达）及统计信息
- 导出 / 导入 JSON 备份
- 深色模式（默认跟随系统），移动端适配

## Todo

1. 分类
2. 直接添加PT 站点
3. 支持单个删除[done]
4. 拖拽排序[done]
5. 备份（导入、导出）[done]
6. 修改头像[done]
