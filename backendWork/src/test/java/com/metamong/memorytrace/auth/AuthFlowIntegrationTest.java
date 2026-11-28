package com.metamong.memorytrace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.testdb.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 → 로그인 → me → 로그아웃 (11장 1-2). 전체 애플리케이션과 테스트 DB를 쓴다.
 * 다른 테스트가 회원 수를 검사하므로 모든 변경을 롤백한다.
 */
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "pass1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("가입 → 로그인 → me → 로그아웃 → me 401")
    void signupLoginMeLogout() throws Exception {
        signup("flow_user", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.loginId").value("flow_user"))
                .andExpect(jsonPath("$.nickname").value("흐름탐정"))
                .andExpect(jsonPath("$.role").value("USER"))
                // 가입해도 자동 로그인하지 않는다
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        User saved = users.findByLoginId("flow_user").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$").doesNotContain(PASSWORD);
        assertThat(saved.getCreatedAt().getNano()).isZero();

        MockHttpSession session = new MockHttpSession();
        String idBeforeLogin = session.getId();
        login(session, "flow_user", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.loginId").value("flow_user"));
        assertThat(session.getId()).as("로그인하면 세션 ID가 바뀐다").isNotEqualTo(idBeforeLogin);

        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(((UserPrincipal) context.getAuthentication().getPrincipal()).getPassword()).isNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.nickname").value("흐름탐정"));

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션 없이 로그인해도 세션이 만들어져 다음 요청에 로그인 상태가 이어진다")
    void loginWithoutExistingSession() throws Exception {
        signup("fresh_user", PASSWORD).andExpect(status().isCreated());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("fresh_user", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 아이디로 다시 가입하면 409")
    void duplicateSignup() throws Exception {
        signup("twice_user", PASSWORD).andExpect(status().isCreated());

        signup("twice_user", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));
    }

    @Test
    @DisplayName("틀린 비밀번호, 없는 아이디, 대소문자·공백이 다른 아이디, 긴 비밀번호는 모두 같은 401")
    void loginFailuresAreIdentical() throws Exception {
        signup("strict_user", PASSWORD).andExpect(status().isCreated());
        // DB 조회만으로는 대소문자와 뒤 공백이 구분되지 않는다는 전제를 확인한다
        assertThat(users.findByLoginId("STRICT_USER")).isPresent();

        String wrongPassword = failedLoginBody("strict_user", "wrong-password");

        assertThat(wrongPassword).isEqualTo("""
                {"code":"UNAUTHORIZED","message":"아이디 또는 비밀번호가 맞지 않습니다."}""");
        assertThat(failedLoginBody("nobody_here", PASSWORD)).isEqualTo(wrongPassword);
        assertThat(failedLoginBody("STRICT_USER", PASSWORD)).isEqualTo(wrongPassword);
        assertThat(failedLoginBody("strict_user ", PASSWORD)).isEqualTo(wrongPassword);
        assertThat(failedLoginBody("strict_user", "가".repeat(64))).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("UTF-8 72바이트 비밀번호로 가입하고 로그인한다")
    void passwordOf72Bytes() throws Exception {
        String password = "가".repeat(24);

        signup("bytes_user", password).andExpect(status().isCreated());

        login(new MockHttpSession(), "bytes_user", password).andExpect(status().isOk());
    }

    private ResultActions signup(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"%s","nickname":"  흐름탐정  "}""".formatted(loginId, password)));
    }

    private ResultActions login(MockHttpSession session, String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(loginId, password)));
    }

    private String failedLoginBody(String loginId, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String body = login(session, loginId, password)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        return body;
    }

    private static String credentials(String loginId, String password) {
        return """
                {"loginId":"%s","password":"%s"}""".formatted(loginId, password);
    }
}
