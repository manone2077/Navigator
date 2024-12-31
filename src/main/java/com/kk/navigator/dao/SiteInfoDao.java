package com.kk.navigator.dao;

import com.kk.navigator.entity.SiteInfo;
import org.springframework.data.repository.CrudRepository;


public interface SiteInfoDao extends CrudRepository<SiteInfo, Integer> {
    /**
     * 根据id查找
     * @param id
     * @return
     */
    SiteInfo findSiteInfoById(Integer id);
}
