package com.kk.navigator.controller.dto;

/**
 * 添加 / 编辑 / 导入站点时客户端可以提交的字段。
 * 测速状态、排序号等由服务端维护的字段不在其中，避免被请求直接改写。
 *
 * @param id 编辑时必填，添加和导入时忽略
 */
public record SiteForm(Integer id, String title, String url, String content, String description,
                       String checkinUrl, String icon) {
}
