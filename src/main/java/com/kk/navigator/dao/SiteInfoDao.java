package com.kk.navigator.dao;

import com.kk.navigator.entity.SiteInfo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


public interface SiteInfoDao extends CrudRepository<SiteInfo, Integer> {
    /**
     * 根据id查找
     * @param id
     * @return
     */
    SiteInfo findSiteInfoById(Integer id);

    long countByIcon(String icon);

    /**
     * 只更新测速状态，避免测速过程中覆盖用户同时修改的其它字段
     */
    @Transactional
    @Modifying
    @Query("update SiteInfo s set s.status = :status, s.statusMsg = :msg, s.checkedAt = :checkedAt where s.id = :id")
    int updateStatus(@Param("id") Integer id, @Param("status") long status, @Param("msg") String msg,
                     @Param("checkedAt") LocalDateTime checkedAt);

    @Transactional
    @Modifying
    @Query("update SiteInfo s set s.icon = :icon where s.id = :id")
    int updateIcon(@Param("id") Integer id, @Param("icon") String icon);

    @Transactional
    @Modifying
    @Query("update SiteInfo s set s.sortOrder = :sortOrder where s.id = :id")
    int updateSortOrder(@Param("id") Integer id, @Param("sortOrder") Integer sortOrder);
}
