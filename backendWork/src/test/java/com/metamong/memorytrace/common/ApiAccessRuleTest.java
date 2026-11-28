package com.metamong.memorytrace.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metamong.memorytrace.common.config.SecurityConfig;
import com.metamong.memorytrace.common.security.JsonAccessDeniedHandler;
import com.metamong.memorytrace.common.security.JsonAuthenticationEntryPoint;
import com.metamong.memorytrace.common.security.SecurityErrorWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 접근 규칙 (docs/backend_design.md 5.2, 8장). DB 없이 실행한다.
 * T14: 비로그인의 보호 API는 401 JSON. T29(API 부분): 없는 /api 경로는 JSON 404, 비로그인이면 401.
 */
@WebMvcTest(controllers = ApiAccessRuleTest.NoController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class,
        JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class ApiAccessRuleTest {

    private static final String UNAUTHORIZED_BODY = """
            {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}""";
    private static final String NOT_FOUND_BODY = """
            {"code":"NOT_FOUND","message":"요청한 정보를 찾을 수 없습니다."}""";

    /** 업무 컨트롤러를 띄우지 않고 접근 규칙만 확인하기 위한 표시용 타입 */
    static class NoController {
    }

    @Autowired
    private MockMvc mockMvc;

    /** T14. api_spec의 로그인 필요 API 전부. 업무 컨트롤러 없이 접근 규칙만 확인한다. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,  /api/auth/me",
            "POST, /api/auth/logout",
            "GET,  /api/cases",
            "POST, /api/cases/1/sessions",
            "GET,  /api/sessions/1",
            "POST, /api/sessions/1/investigations",
            "POST, /api/sessions/1/exclusions",
            "POST, /api/sessions/1/accusations",
            "GET,  /api/sessions/1/result",
            "GET,  /api/me/sessions",
            "GET,  /api/me/summary",
            "GET,  /api/me/sessions/1/actions",
            // 가입·로그인은 POST만 열려 있다
            "GET,  /api/auth/signup",
            "GET,  /api/auth/login",
            "PUT,  /api/auth/login",
            "DELETE, /api/sessions/1",
            // T29: 없는 API 경로도 비로그인이면 401이 먼저다
            "GET,  /api",
            "GET,  /api/xxx",
            "POST, /api/xxx",
            "GET,  /api/sessions/1/xxx",
            "POST, /api/sessions/1/xxx",
    })
    void anonymousGets401Json(String method, String path) throws Exception {
        MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method), path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();

        // 요청 캐시를 껐으므로 401 때문에 세션이 생기지 않는다
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    /** T29(API 부분). 로그인 상태의 없는 API 경로는 index.html이 아닌 JSON 404. CSRF 토큰 없이 POST한다. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,  /api/xxx",
            "POST, /api/xxx",
            "GET,  /api/sessions/1/xxx",
            "POST, /api/sessions/1/xxx",
    })
    void unknownApiPathIsJson404WhenLoggedIn(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(user("tester")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(NOT_FOUND_BODY, JsonCompareMode.STRICT));
    }

    /** 인증을 요구하지 않는 요청. 이 테스트에는 컨트롤러가 없어 404까지 도달하면 통과한 것이다. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST, /api/auth/signup",
            "POST, /api/auth/login",
            "GET,  /play/3",
            "GET,  /scenes/none.webp",
            // 기본 로그인 화면과 기본 로그아웃 처리는 꺼져 있다
            "GET,  /login",
            "POST, /login",
            "POST, /logout",
    })
    void publicRequestsPassSecurity(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isNotFound());
    }
}
