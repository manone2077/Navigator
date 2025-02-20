![Static Badge](https://img.shields.io/badge/%E7%9A%AE%E7%89%B9%E5%AF%BC%E8%88%AA-%E7%AB%99%E7%82%B9%E7%AE%A1%E7%90%86%E7%A5%9E%E5%99%A8-brightgreen?logoColor=%2375c74b)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/manone2077/Navigator/.github%2Fworkflows%2Fdocker-image.yml)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/manone2077/Navigator)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues-pr/manone2077/Navigator)
![GitHub Tag](https://img.shields.io/github/v/tag/manone2077/Navigator)

# 皮特导航

Navigator for website management

## Todo

1. 分类
2. 支持单个删除
3. 直接添加PT 站点
4. 备份（导入、导出？）
5. 支持代理访问[in progress]
6. docker 自动部署[√]

## Docker 部署
```shell
docker run -d -p 9000:80 --name MyNavigator -v /config:/app/data greatpower/navigator:latest
```

## 开发相关
1. 本项目使用git 进行版本管理
2. 主要分为`master、dev、release` 三个分支，其中[dev](https://github.com/manone2077/Navigator/tree/dev) 用作开发，[release](https://github.com/manone2077/Navigator/tree/release) 用作版本发布，[master](https://github.com/manone2077/Navigator) 用作部署(只用来合并`dev` 分支代码)

## 效果如下
访问  http://yourip:9000/   
图中的图像获取失败，不是系统问题，是本身对应的站点没有favicon 文件导致，可以忽略或点击图片位置上传一张即可
![nav.png](images/nav.png)