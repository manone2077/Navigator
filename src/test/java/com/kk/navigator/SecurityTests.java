package com.kk.navigator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.navigator.dao.SiteInfoDao;
import com.kk.navigator.entity.SiteInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 跨站请求防护、请求方法限制、服务端请求地址限制（SSRF）与错误响应格式
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SecurityTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    SiteInfoDao dao;

    @BeforeEach
    void seed() {
        dao.deleteAll();
        SiteInfo s = new SiteInfo();
        s.setTitle("a");
        s.setUrl("http://127.0.0.1:1");
        dao.save(s);
    }

    MvcResult postJson(String path, String body, boolean withHeader) throws Exception {
        var req = post("/api/" + path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (withHeader) {
            req.header("X-Requested-With", "XMLHttpRequest");
        }
        return mvc.perform(req).andReturn();
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void destructiveEndpointsRejectGet() throws Exception {
        MvcResult r = mvc.perform(get("/api/deleteAll")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(405);
        assertThat(body(r).get("code").asInt()).isEqualTo(405);
        assertThat(mvc.perform(get("/api/speedtest")).andReturn().getResponse().getStatus()).isEqualTo(405);
        assertThat(dao.count()).isEqualTo(1);
    }

    @Test
    void writeRequestsRequireHeader() throws Exception {
        MvcResult r = postJson("deleteAll", "{}", false);
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(r).get("msg").asText()).contains("X-Requested-With");
        assertThat(dao.count()).isEqualTo(1);
        // 读取接口不受影响
        assertThat(mvc.perform(get("/api/all")).andReturn().getResponse().getStatus()).isEqualTo(200);
        // 系统设置接口同样受保护
        assertThat(postJson("system/setProxy", "{}", false).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void siteMetaBlocksInternalAddresses() throws Exception {
        for (String url : new String[]{"http://169.254.169.254/latest/meta-data/", "http://127.0.0.1:8000/api/export",
                "http://localhost/", "http://0.0.0.0/", "file:///etc/passwd"}) {
            MvcResult r = postJson("siteMeta", "{\"url\":\"" + url + "\"}", true);
            assertThat(r.getResponse().getStatus()).as(url).isEqualTo(400);
            assertThat(body(r).get("msg").asText()).as(url).isNotBlank();
        }
    }

    @Test
    void errorsUseMatchingHttpStatus() throws Exception {
        MvcResult bad = postJson("add", "{not json", true);
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(bad).get("msg").asText()).isEqualTo("请求格式不正确");

        MvcResult invalid = postJson("add", "{\"title\":\"\",\"url\":\"http://a.com\"}", true);
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(invalid).get("msg").asText()).isEqualTo("站点名称不能为空");

        MvcResult missing = postJson("delete", "{\"id\":99999}", true);
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);

        assertThat(mvc.perform(get("/api/no-such-api")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void serverManagedFieldsCannotBeSet() throws Exception {
        MvcResult r = postJson("add", "{\"title\":\"x\",\"url\":\"http://127.0.0.1:2\",\"status\":5,\"sortOrder\":-100}", true);
        JsonNode site = body(r).get("data");
        assertThat(site.get("status").asLong()).isEqualTo(-1);
        assertThat(site.get("sortOrder").asInt()).isGreaterThanOrEqualTo(0);
    }
}
