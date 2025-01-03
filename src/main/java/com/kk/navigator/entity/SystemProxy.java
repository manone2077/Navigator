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
public class SystemProxy {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String host;
    private String port;
    private String username;
    private String password;
    private String type;
    private String status;
    private String remark;

    @CreationTimestamp
    private Timestamp create;
    @UpdateTimestamp
    private Timestamp update;
}
