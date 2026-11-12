/*
 *    Copyright 2026, may (1742057357@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.demo.controller;

import org.dromara.warm.demo.config.DemoTokenService;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录入口校验测试，不连接数据库或签发真实会话。
 *
 * @author warm
 */
class AuthControllerTest {

    private MockMvc mvc;
    private DemoTokenService tokenService;

    @BeforeEach
    void setUp() {
        UserService users = mock(UserService.class);
        DemoUserVo user = new DemoUserVo();
        user.setUserName("alice");
        when(users.listApprovers()).thenReturn(List.of(user));
        tokenService = mock(DemoTokenService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(users, tokenService, new MockEnvironment())).build();
    }

    @Test
    void missingNullAndBlankPasswordReturnBadRequest() throws Exception {
        for (String json : List.of("{\"username\":\"alice\"}",
            "{\"username\":\"alice\",\"password\":null}",
            "{\"username\":\"alice\",\"password\":\" \"}")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(tokenService);
    }

    @Test
    void blankUsernameIsRejectedBeforeCredentialsCheck() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" \",\"password\":\"not-used\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(tokenService);
    }
}
