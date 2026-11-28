package com.metamong.memorytrace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamong.memorytrace.auth.controller.AuthController;
import com.metamong.memorytrace.auth.dto.LoginRequest;
import com.metamong.memorytrace.auth.dto.SignupRequest;
import com.metamong.memorytrace.auth.dto.UserResponse;
import com.metamong.memorytrace.auth.entity.Role;
import com.metamong.memorytrace.auth.service.AuthService;
import com.metamong.memorytrace.common.config.SecurityConfig;
import com.metamong.memorytrace.common.security.JsonAccessDeniedHandler;
import com.metamong.memorytrace.common.security.JsonAuthenticationEntryPoint;
import com.metamong.memorytrace.common.security.SecurityErrorWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 가입·로그인 입력 검증 (docs/backend_design.md 4.1, 4.2). DB 없이 실행한다. */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class,
        JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthRequestValidationTest {

    private static final String LOGIN_ID = "아이디는 영문 소문자·숫자·_ 4~30자입니다.";
    private static final String PASSWORD = "비밀번호는 8~64자입니다.";
    private static final String PASSWORD_BYTES = "비밀번호가 너무 깁니다. 한글·특수 문자는 글자당 여러 바이트를 차지합니다.";
    private static final String NICKNAME = "닉네임은 2~20자입니다.";

    /** 한글 24자 = UTF-8 72바이트 */
    private static final String BYTES_72 = "가".repeat(24);
    private static final String BYTES_73 = BYTES_72 + "a";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private SecurityContextRepository securityContextRepository;

    static Stream<Arguments> invalidSignups() {
        return Stream.of(
                Arguments.of("loginId", null, LOGIN_ID),
                Arguments.of("loginId", "", LOGIN_ID),
                Arguments.of("loginId", "abc", LOGIN_ID),
                Arguments.of("loginId", "a".repeat(31), LOGIN_ID),
                Arguments.of("loginId", "Detective", LOGIN_ID),
                Arguments.of("loginId", "tester ", LOGIN_ID),
                Arguments.of("password", null, PASSWORD),
                Arguments.of("password", "", PASSWORD),
                Arguments.of("password", "a".repeat(7), PASSWORD),
                Arguments.of("password", "a".repeat(65), PASSWORD),
                // 글자 수와 바이트 수를 모두 넘으면 길이 문구만 나간다
                Arguments.of("password", "가".repeat(65), PASSWORD),
                Arguments.of("password", BYTES_73, PASSWORD_BYTES),
                Arguments.of("nickname", null, NICKNAME),
                Arguments.of("nickname", "   ", NICKNAME),
                Arguments.of("nickname", "  a  ", NICKNAME),
                Arguments.of("nickname", "가".repeat(21), NICKNAME));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidSignups")
    void rejectsInvalidSignup(String field, String value, String message) throws Exception {
        Map<String, String> body = validSignup();
        body.put(field, value);

        send("/api/auth/signup", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(authService);
    }

    static Stream<Arguments> validBoundaries() {
        return Stream.of(
                Arguments.of("loginId", "a_09"),
                Arguments.of("loginId", "a".repeat(30)),
                Arguments.of("password", "a".repeat(8)),
                Arguments.of("password", "a".repeat(64)),
                Arguments.of("password", BYTES_72),
                Arguments.of("nickname", "가나"),
                Arguments.of("nickname", "  " + "가".repeat(20) + "  "));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("validBoundaries")
    void acceptsBoundarySignup(String field, String value) throws Exception {
        Map<String, String> body = validSignup();
        body.put(field, value);
        when(authService.signup(any())).thenReturn(new UserResponse(1L, "tester", "탐정", Role.USER));

        send("/api/auth/signup", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void nicknameIsStripped() throws Exception {
        Map<String, String> body = validSignup();
        body.put("nickname", "  꼬마탐정  ");
        when(authService.signup(any())).thenReturn(new UserResponse(1L, "tester", "꼬마탐정", Role.USER));

        send("/api/auth/signup", body).andExpect(status().isCreated());

        ArgumentCaptor<SignupRequest> request = ArgumentCaptor.forClass(SignupRequest.class);
        verify(authService).signup(request.capture());
        assertThat(request.getValue().nickname()).isEqualTo("꼬마탐정");
    }

    static Stream<Arguments> invalidLogins() {
        String loginId = "아이디를 30자 이내로 입력해 주세요.";
        String password = "비밀번호를 64자 이내로 입력해 주세요.";
        return Stream.of(
                Arguments.of("loginId", null, loginId),
                Arguments.of("loginId", "a".repeat(31), loginId),
                Arguments.of("password", "", password),
                Arguments.of("password", "a".repeat(65), password));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidLogins")
    void rejectsInvalidLogin(String field, String value, String message) throws Exception {
        Map<String, String> body = new HashMap<>(Map.of("loginId", "tester", "password", "pass1234!"));
        body.put(field, value);

        send("/api/auth/login", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void requestsDoNotPrintPassword() {
        assertThat(new SignupRequest("tester", "secret-value", "탐정").toString()).doesNotContain("secret-value");
        assertThat(new LoginRequest("tester", "secret-value").toString()).doesNotContain("secret-value");
    }

    private static Map<String, String> validSignup() {
        return new HashMap<>(Map.of("loginId", "tester", "password", "pass1234!", "nickname", "탐정"));
    }

    private ResultActions send(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
