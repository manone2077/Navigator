package com.kk.navigator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.dao.TagDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class TagTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    SiteInfoDao siteDao;
    @Autowired
    TagDao tagDao;

    @BeforeEach
    void clean() {
        siteDao.deleteAll();
        tagDao.findAll().forEach(t -> tagDao.unlinkSites(t.getId()));
        tagDao.deleteAll();
    }

    JsonNode call(String path, Object body) throws Exception {
        String content = mvc.perform(post("/api/" + path).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .content(body instanceof String s ? s : json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    JsonNode read(String path) throws Exception {
        return json.readTree(mvc.perform(get("/api/" + path))
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    List<String> names(JsonNode arr) {
        List<String> r = new ArrayList<>();
        arr.forEach(n -> r.add(n.get("name").asText()));
        return r;
    }

    @Test
    void createRenameAndValidate() throws Exception {
        JsonNode t = call("tags/save", Map.of("name", " 影音 ")).get("data");
        assertThat(t.get("name").asText()).isEqualTo("影音");
        assertThat(t.get("color").asText()).matches("#[0-9a-f]{6}");
        assertThat(t.has("nameKey")).isFalse();

        call("tags/save", Map.of("name", "Download"));
        // 重名不区分大小写
        assertThat(call("tags/save", Map.of("name", "download")).get("msg").asText()).contains("已存在");
        assertThat(call("tags/save", Map.of("name", "")).get("code").asInt()).isEqualTo(400);
        assertThat(call("tags/save", Map.of("name", "x".repeat(21))).get("msg").asText()).contains("20");

        JsonNode renamed = call("tags/save", Map.of("id", t.get("id").asInt(), "name", "影视", "color", "#FF0000")).get("data");
        assertThat(renamed.get("name").asText()).isEqualTo("影视");
        assertThat(renamed.get("color").asText()).isEqualTo("#ff0000");
        assertThat(names(read("tags"))).containsExactly("影视", "Download");
    }

    @Test
    void sitesCreateAndKeepTags() throws Exception {
        call("tags/save", Map.of("name", "下载"));
        JsonNode site = call("add", Map.of("title", "qb", "url", "http://127.0.0.1:1",
                "tags", List.of("下载", "常用", "常用", " "))).get("data");
        // 已有标签复用，新标签自动创建，重复和空白被忽略
        assertThat(names(site.get("tags"))).containsExactly("下载", "常用");
        assertThat(read("tags")).hasSize(2);

        int id = site.get("id").asInt();
        // 编辑时不传 tags 表示不修改
        JsonNode updated = call("update", Map.of("id", id, "title", "qBittorrent", "url", "http://127.0.0.1:1")).get("data");
        assertThat(names(updated.get("tags"))).containsExactly("下载", "常用");
        // 传空列表表示清空
        updated = call("update", Map.of("id", id, "title", "qBittorrent", "url", "http://127.0.0.1:1", "tags", List.of())).get("data");
        assertThat(updated.get("tags")).isEmpty();

        assertThat(call("add", Map.of("title", "x", "url", "http://127.0.0.1:2",
                "tags", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))).get("msg").asText()).contains("最多");
    }

    @Test
    void deletingTagKeepsSites() throws Exception {
        JsonNode site = call("add", Map.of("title", "pve", "url", "http://127.0.0.1:1", "tags", List.of("基础设施", "常用"))).get("data");
        int tagId = site.get("tags").get(0).get("id").asInt();
        assertThat(call("tags/delete", Map.of("id", tagId)).get("code").asInt()).isEqualTo(200);
        JsonNode sites = read("all");
        assertThat(sites).hasSize(1);
        assertThat(names(sites.get(0).get("tags"))).containsExactly("常用");
        assertThat(call("tags/delete", Map.of("id", tagId)).get("code").asInt()).isEqualTo(404);
    }

    @Test
    void sortAndImport() throws Exception {
        int a = call("tags/save", Map.of("name", "A")).get("data").get("id").asInt();
        int b = call("tags/save", Map.of("name", "B")).get("data").get("id").asInt();
        call("tags/sort", List.of(b, a));
        assertThat(names(read("tags"))).containsExactly("B", "A");

        JsonNode r = call("tags/import", List.of(Map.of("name", "a", "color", "#000000"), Map.of("name", "监控", "color", "#123456")));
        assertThat(r.get("data").asInt()).isEqualTo(1);
        JsonNode tags = read("tags");
        assertThat(names(tags)).containsExactly("B", "A", "监控");
        assertThat(tags.get(2).get("color").asText()).isEqualTo("#123456");

        // 导入站点时按名称关联标签
        call("import", List.of(Map.of("title", "x", "url", "http://127.0.0.1:1", "tags", List.of("监控", "新标签"))));
        assertThat(names(read("all").get(0).get("tags"))).containsExactly("监控", "新标签");
    }

    @Test
    void tagWritesRequirePost() throws Exception {
        assertThat(mvc.perform(get("/api/tags/delete")).andReturn().getResponse().getStatus()).isEqualTo(405);
        assertThat(mvc.perform(post("/api/tags/save").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
