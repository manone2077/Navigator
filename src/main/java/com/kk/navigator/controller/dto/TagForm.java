package com.kk.navigator.controller.dto;

/**
 * 新建 / 修改标签
 *
 * @param id    修改时必填
 * @param color #RRGGBB，为空时自动分配
 */
public record TagForm(Integer id, String name, String color) {
}
