package com.kk.navigator.controller;

import com.kk.navigator.controller.dto.TagForm;
import com.kk.navigator.entity.BizException;
import com.kk.navigator.entity.R;
import com.kk.navigator.entity.Tag;
import com.kk.navigator.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口：读取使用 GET，修改只接受 POST
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public R<List<Tag>> list() {
        return R.success("success", tagService.list());
    }

    @PostMapping("/save")
    public R<Tag> save(@RequestBody TagForm form) {
        return R.success(form.id() == null ? "标签已创建" : "标签已保存", tagService.save(form));
    }

    @PostMapping("/delete")
    public R<Void> delete(@RequestBody TagForm form) {
        if (form.id() == null) {
            throw new BizException(404, "标签不存在");
        }
        tagService.delete(form.id());
        return R.success("标签已删除");
    }

    @PostMapping("/sort")
    public R<Void> sort(@RequestBody List<Integer> ids) {
        tagService.sort(ids);
        return R.success("顺序已保存");
    }

    /** 导入备份中的标签定义，只创建不存在的标签 */
    @PostMapping("/import")
    public R<Integer> importTags(@RequestBody List<TagForm> forms) {
        int created = tagService.importTags(forms);
        return R.success("新建 " + created + " 个标签", created);
    }
}
