package com.kk.navigator;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"navigator.password=secret", "navigator.guest-view=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class AuthTests {
    @Autowired
    MockMvc mvc;

    Cookie login(String password, String ip) throws Exception {
        var res = mvc.perform(post("/api/auth/login").header("X-Real-IP", ip).header("X-Requested-With", "XMLHttpRequest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}")).andReturn().getResponse();
        return res.getCookie("nav_token");
    }

    @Test
    void publicEndpointsAndGuestView() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/status")).andExpect(content().string(org.hamcrest.Matchers.containsString("\"loggedIn\":false")));
        // 访客可浏览但不能修改
        mvc.perform(get("/api/all")).andExpect(status().isOk());
        mvc.perform(get("/api/tags")).andExpect(status().isOk());
        mvc.perform(post("/api/tags/save").header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/deleteAll").header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginGrantsAccess() throws Exception {
        assertThat(login("wrong", "10.0.0.1")).isNull();
        Cookie token = login("secret", "10.0.0.1");
        assertThat(token).isNotNull();
        assertThat(token.isHttpOnly()).isTrue();
        mvc.perform(get("/api/export").cookie(token)).andExpect(status().isOk());
        mvc.perform(get("/api/export").cookie(new Cookie("nav_token", "99999999999999.forged"))).andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyFailuresLocksIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("wrong", "10.0.0.2");
        }
        assertThat(login("secret", "10.0.0.2")).isNull();
        assertThat(login("secret", "10.0.0.3")).isNotNull();
    }
}
