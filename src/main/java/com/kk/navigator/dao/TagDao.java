package com.kk.navigator.dao;

import com.kk.navigator.entity.Tag;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TagDao extends CrudRepository<Tag, Integer> {
    List<Tag> findAllByOrderBySortOrderAscIdAsc();

    Optional<Tag> findByNameKey(String nameKey);

    /** 删除标签前先解除与站点的关联，站点本身保留 */
    @Transactional
    @Modifying
    @Query(value = "delete from nav_site_tag where tag_id = :id", nativeQuery = true)
    int unlinkSites(@Param("id") Integer id);

    @Transactional
    @Modifying
    @Query("update Tag t set t.sortOrder = :sortOrder where t.id = :id")
    int updateSortOrder(@Param("id") Integer id, @Param("sortOrder") Integer sortOrder);
}
