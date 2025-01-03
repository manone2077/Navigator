package com.kk.navigator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Data
@Entity
public class SystemUserAgent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String userAgent;
    // 标识 ua 是否启动，0 启用，1 禁用
    private Integer status;


    @CreationTimestamp
    private Timestamp create;
    @UpdateTimestamp
    private Timestamp update;
}
