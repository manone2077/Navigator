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

## 效果如下
访问  http://yourip:9000/   
图中的图像获取失败，不是系统问题，是本身对应的站点没有favicon 文件导致，可以忽略或点击图片位置上传一张即可
![nav.png](images/nav.png)