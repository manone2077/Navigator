package com.kk.navigator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.navigator.dao.SiteInfoDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SiteApiTests {
    /** 1x1 png */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    SiteInfoDao dao;

    @BeforeEach
    void clean() {
        dao.deleteAll();
    }

    JsonNode call(String path, Object body) throws Exception {
        String content = mvc.perform(post("/api/" + path).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .content(body instanceof String str ? str : json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    JsonNode all() throws Exception {
        return json.readTree(mvc.perform(get("/api/all")).andReturn().getResponse().getContentAsString()).get("data");
    }

    /** 使用不可连接的本地端口，避免测试访问外网 */
    int add(String title, String url) throws Exception {
        JsonNode r = call("add", "{\"title\":\"" + title + "\",\"url\":\"" + url + "\"}");
        assertThat(r.get("code").asInt()).isEqualTo(200);
        return r.get("data").get("id").asInt();
    }

    @Test
    void addValidatesInput() throws Exception {
        assertThat(call("add", "{\"title\":\"\",\"url\":\"http://127.0.0.1:1\"}").get("code").asInt()).isEqualTo(400);
        assertThat(call("add", "{\"title\":\"a\",\"url\":\"abc\"}").get("code").asInt()).isEqualTo(400);
        JsonNode r = call("add", "{\"title\":\" a \",\"url\":\" http://127.0.0.1:1/x \"}");
        assertThat(r.get("data").get("title").asText()).isEqualTo("a");
        assertThat(r.get("data").get("url").asText()).isEqualTo("http://127.0.0.1:1/x");
        assertThat(r.get("data").get("status").asLong()).isEqualTo(-1);
    }

    @Test
    void sortAndDelete() throws Exception {
        int a = add("a", "http://127.0.0.1:1");
        int b = add("b", "http://127.0.0.1:2");
        int c = add("c", "http://127.0.0.1:3");
        call("sort", List.of(c, a, b));
        List<String> titles = new ArrayList<>();
        all().forEach(n -> titles.add(n.get("title").asText()));
        assertThat(titles).containsExactly("c", "a", "b");

        assertThat(call("delete", "{\"id\":" + a + "}").get("code").asInt()).isEqualTo(200);
        assertThat(call("delete", "{\"id\":" + a + "}").get("code").asInt()).isEqualTo(404);
        assertThat(all()).hasSize(2);
        call("deleteAll", "{}");
        assertThat(all()).isEmpty();
    }

    @Test
    void updateKeepsSite() throws Exception {
        int id = add("old", "http://127.0.0.1:1");
        call("update", "{\"id\":" + id + ",\"title\":\"new\",\"url\":\"http://127.0.0.1:1\"}");
        assertThat(all().get(0).get("title").asText()).isEqualTo("new");
        assertThat(call("update", "{\"id\":99999,\"title\":\"x\",\"url\":\"http://127.0.0.1:1\"}").get("code").asInt()).isEqualTo(404);
    }

    @Test
    void speedtestRecordsFailureReason() throws Exception {
        int id = add("refused", "http://127.0.0.1:1");
        JsonNode site = call("speedtest/one", "{\"id\":" + id + "}").get("data");
        assertThat(site.get("status").asLong()).isEqualTo(99999);
        assertThat(site.get("statusMsg").asText()).contains("连接被拒绝");
        assertThat(site.get("checkedAt").isNull()).isFalse();
    }

    @Test
    void uploadOnlyAcceptsImages() throws Exception {
        String ok = mvc.perform(multipart("/api/upload").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(ok).get("data").asText()).endsWith(".png");
        String bad = mvc.perform(multipart("/api/upload").file(new MockMultipartFile("file", "a.html", "text/html", "<script>".getBytes()))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(bad).get("code").asInt()).isEqualTo(400);
    }

    @Test
    void exportAndImportKeepIcons() throws Exception {
        String png = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);
        JsonNode r = call("import", List.of(
                java.util.Map.of("title", "with icon", "url", "http://127.0.0.1:1", "icon", png),
                java.util.Map.of("title", "missing icon", "url", "http://127.0.0.1:2", "icon", "/images/not-exist.png"),
                java.util.Map.of("title", "invalid", "url", "nope")));
        assertThat(r.get("data").asInt()).isEqualTo(2);

        JsonNode sites = all();
        assertThat(sites.get(0).get("icon").asText()).startsWith("/images/up-").endsWith(".png");
        assertThat(sites.get(1).get("icon").isNull()).isTrue();

        JsonNode exported = json.readTree(mvc.perform(get("/api/export")).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(exported.get(0).get("icon").asText()).isEqualTo(png);
    }
}
