package com.kk.navigator.entity;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Data
public class Setting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ua;
    private String proxyHost;
    private int proxyPort;
    private String authToken; // 新增字段名
    private String cookie;
    private String checkinUrl;
    private String accept;
    private String acceptEncoding;
    private int timeout;
    private LocalDateTime lastModifiedDate;

}
