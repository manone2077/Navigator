package com.kk.navigator.service;

import com.kk.navigator.controller.dto.TagForm;
import com.kk.navigator.dao.TagDao;
import com.kk.navigator.entity.BizException;
import com.kk.navigator.entity.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 标签管理：新建、改名、改颜色、排序、删除，以及按名称查找或创建（添加站点时直接输入新标签）
 */
@Service
public class TagService {
    public static final int MAX_TAGS_PER_SITE = 10;
    public static final int MAX_TAGS = 100;
    /** 新建标签时依次分配的颜色 */
    static final String[] PALETTE = {
            "#38bdf8", "#a78bfa", "#f472b6", "#fb923c", "#4ade80", "#facc15", "#22d3ee", "#f87171"
    };

    private final TagDao tagDao;

    public TagService(TagDao tagDao) {
        this.tagDao = tagDao;
    }

    public List<Tag> list() {
        return tagDao.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public synchronized Tag save(TagForm form) {
        String name = normalizeName(form == null ? null : form.name());
        String key = key(name);
        Tag tag;
        if (form.id() == null) {
            tag = new Tag();
            tag.setSortOrder(nextSortOrder());
            checkTotal();
        } else {
            tag = tagDao.findById(form.id()).orElseThrow(() -> new BizException(404, "标签不存在"));
        }
        tagDao.findByNameKey(key)
                .filter(other -> !Objects.equals(other.getId(), tag.getId()))
                .ifPresent(other -> {
                    throw new BizException("标签「" + other.getName() + "」已存在");
                });
        tag.setName(name);
        tag.setNameKey(key);
        if (form.color() != null || tag.getColor() == null) {
            tag.setColor(normalizeColor(form.color()));
        }
        return tagDao.save(tag);
    }

    /** 删除标签，站点不会被删除，只会移除该标签 */
    @Transactional
    public void delete(Integer id) {
        Tag tag = id == null ? null : tagDao.findById(id).orElse(null);
        if (tag == null) {
            throw new BizException(404, "标签不存在");
        }
        tagDao.unlinkSites(tag.getId());
        tagDao.delete(tag);
    }

    /** 保存标签顺序（分组视图中分组的顺序），参数为按顺序排列的标签 id */
    @Transactional
    public void sort(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            tagDao.updateSortOrder(ids.get(i), i);
        }
    }

    /**
     * 按名称查找标签，不存在时创建；名称不区分大小写，重复的名称只保留一个
     */
    @Transactional
    public synchronized List<Tag> resolve(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : names) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = normalizeName(raw);
            unique.putIfAbsent(key(name), name);
        }
        if (unique.size() > MAX_TAGS_PER_SITE) {
            throw new BizException("每个站点最多 " + MAX_TAGS_PER_SITE + " 个标签");
        }
        List<Tag> result = new ArrayList<>();
        unique.forEach((key, name) -> result.add(tagDao.findByNameKey(key).orElseGet(() -> create(name, null))));
        return result;
    }

    /**
     * 导入备份中的标签定义：只创建不存在的标签（保留备份中的颜色），已存在的标签不做修改
     *
     * @return 新建的数量
     */
    @Transactional
    public synchronized int importTags(List<TagForm> forms) {
        int created = 0;
        for (TagForm form : forms == null ? List.<TagForm>of() : forms) {
            if (form == null || !StringUtils.hasText(form.name())) {
                continue;
            }
            String name;
            try {
                name = normalizeName(form.name());
            } catch (BizException e) {
                continue;
            }
            if (tagDao.findByNameKey(key(name)).isEmpty()) {
                create(name, form.color());
                created++;
            }
        }
        return created;
    }

    private Tag create(String name, String color) {
        checkTotal();
        Tag tag = new Tag();
        tag.setName(name);
        tag.setNameKey(key(name));
        tag.setColor(normalizeColor(color));
        tag.setSortOrder(nextSortOrder());
        return tagDao.save(tag);
    }

    private void checkTotal() {
        if (tagDao.count() >= MAX_TAGS) {
            throw new BizException("标签数量已达上限（" + MAX_TAGS + " 个）");
        }
    }

    private int nextSortOrder() {
        return list().stream()
                .map(Tag::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .map(o -> o + 1)
                .orElse(0);
    }

    /** 未指定或格式不正确时，按已有标签数量轮流分配颜色 */
    private String normalizeColor(String color) {
        if (color != null && color.trim().matches("#[0-9a-fA-F]{6}")) {
            return color.trim().toLowerCase(Locale.ROOT);
        }
        return PALETTE[(int) (tagDao.count() % PALETTE.length)];
    }

    static String normalizeName(String name) {
        String v = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) {
            throw new BizException("标签名不能为空");
        }
        if (v.length() > Tag.MAX_NAME_LENGTH) {
            throw new BizException("标签名不能超过 " + Tag.MAX_NAME_LENGTH + " 个字符");
        }
        return v;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
