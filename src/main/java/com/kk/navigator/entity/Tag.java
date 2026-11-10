package com.kk.navigator.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 站点标签。站点与标签是多对多关系，分组视图按标签对站点分组。
 * 标签可以先于站点创建（预定义），删除标签不会删除站点。
 */
@Data
@Entity
@Table(name = "nav_tag")
public class Tag {
    public static final int MAX_NAME_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(length = 40, nullable = false)
    private String name;
    /** 名称的小写形式，用于判断重名（不区分大小写） */
    @JsonIgnore
    @Column(length = 40, nullable = false, unique = true)
    private String nameKey;
    /** 颜色，#RRGGBB */
    @Column(length = 7)
    private String color;
    private Integer sortOrder;
}
