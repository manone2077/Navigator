package com.kk.navigator.controller.dto;

import java.util.List;

/**
 * 添加 / 编辑 / 导入站点时客户端可以提交的字段。
 * 测速状态、排序号等由服务端维护的字段不在其中，避免被请求直接改写。
 *
 * @param id   编辑时必填，添加和导入时忽略
 * @param tags 标签名称列表；不存在的标签会自动创建；编辑时为 null 表示不修改标签
 */
public record SiteForm(Integer id, String title, String url, String content, String description,
                       String checkinUrl, String icon, List<String> tags) {
}
