package com.kk.navigator.dao;

import com.kk.navigator.entity.SiteInfo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;


public interface SiteInfoDao extends CrudRepository<SiteInfo, Integer> {
    /**
     * 根据id查找
     * @param id
     * @return
     */
    SiteInfo findSiteInfoById(Integer id);

    /**
     * 只更新测速状态，避免测速过程中覆盖用户同时修改的其它字段
     */
    @Transactional
    @Modifying
    @Query("update SiteInfo s set s.status = :status where s.id = :id")
    int updateStatus(@Param("id") Integer id, @Param("status") long status);

    @Transactional
    @Modifying
    @Query("update SiteInfo s set s.sortOrder = :sortOrder where s.id = :id")
    int updateSortOrder(@Param("id") Integer id, @Param("sortOrder") Integer sortOrder);
}
