package com.kk.navigator.repository;

import com.kk.navigator.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    Setting findFirstByOrderByLastModifiedDateDesc();
}