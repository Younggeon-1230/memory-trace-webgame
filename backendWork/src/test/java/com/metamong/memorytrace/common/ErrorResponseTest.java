package com.metamong.memorytrace.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metamong.memorytrace.common.config.SecurityConfig;
import com.metamong.memorytrace.common.error.CaseUnavailableException;
import com.metamong.memorytrace.common.error.InvalidRequestException;
import com.metamong.memorytrace.common.error.LoginFailedException;
import com.metamong.memorytrace.common.error.LoginIdTakenException;
import com.metamong.memorytrace.common.error.NotSessionOwnerException;
import com.metamong.memorytrace.common.error.ResourceNotFoundException;
import com.metamong.memorytrace.common.error.SessionEndedException;
import com.metamong.memorytrace.common.error.SessionInProgressException;
import com.metamong.memorytrace.common.error.TargetNotInCaseException;
import com.metamong.memorytrace.common.security.JsonAccessDeniedHandler;
import com.metamong.memorytrace.common.security.JsonAuthenticationEntryPoint;
import com.metamong.memorytrace.common.security.SecurityErrorWriter;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 8장 표의 예외 → 상태 코드·code 매핑. 테스트에만 있는 컨트롤러로 확인하며 DB 없이 실행한다. */
@WebMvcTest(controllers = ErrorResponseTest.FailingController.class)
@Import({ErrorResponseTest.FailingController.class, SecurityConfig.class, SecurityErrorWriter.class,
        JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class ErrorResponseTest {

    private static final String SECRET = "내부정보-SELECT password_hash FROM users";

    private static final Map<String, Supplier<RuntimeException>> THROWABLES = Map.ofEntries(
            Map.entry("invalid", InvalidRequestException::new),
            Map.entry("target", TargetNotInCaseException::new),
            Map.entry("login", LoginFailedException::new),
            Map.entry("owner", NotSessionOwnerException::new),
            Map.entry("not-found", ResourceNotFoundException::new),
            Map.entry("taken", LoginIdTakenException::new),
            Map.entry("ended", SessionEndedException::new),
            Map.entry("in-progress", SessionInProgressException::new),
            Map.entry("unavailable", CaseUnavailableException::new),
            Map.entry("custom-message", () -> new InvalidRequestException("이미 사용된 requestId입니다.")),
            Map.entry("constraint", () -> new ConstraintViolationException(SECRET, Set.of())),
            Map.entry("authentication", () -> new BadCredentialsException(SECRET)),
            Map.entry("access-denied", () -> new AccessDeniedException(SECRET)),
            Map.entry("integrity", () -> new DataIntegrityViolationException(SECRET)),
            Map.entry("unexpected", () -> new IllegalStateException(SECRET)));

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> thrownExceptions() {
        return Stream.of(
                Arguments.of("invalid", 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다."),
                Arguments.of("target", 400, "TARGET_NOT_IN_CASE", "이 사건의 대상이 아닙니다."),
                Arguments.of("login", 401, "UNAUTHORIZED", "아이디 또는 비밀번호가 맞지 않습니다."),
                Arguments.of("owner", 403, "NOT_SESSION_OWNER", "다른 탐정의 수사 기록입니다."),
                Arguments.of("not-found", 404, "NOT_FOUND", "요청한 정보를 찾을 수 없습니다."),
                Arguments.of("taken", 409, "LOGIN_ID_TAKEN", "이미 사용 중인 아이디입니다."),
                Arguments.of("ended", 409, "SESSION_ENDED", "이미 종료된 수사입니다."),
                Arguments.of("in-progress", 409, "SESSION_IN_PROGRESS", "아직 진행 중인 수사입니다."),
                Arguments.of("unavailable", 409, "CASE_UNAVAILABLE",
                        "지금은 이 사건의 수사를 진행할 수 없습니다. 나중에 다시 확인해 주세요."),
                Arguments.of("custom-message", 400, "VALIDATION_ERROR", "이미 사용된 requestId입니다."),
                Arguments.of("constraint", 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다."),
                Arguments.of("authentication", 401, "UNAUTHORIZED", "아이디 또는 비밀번호가 맞지 않습니다."),
                // 로그인 상태의 접근 거부는 JsonAccessDeniedHandler가 응답한다
                Arguments.of("access-denied", 403, "FORBIDDEN", "허용되지 않은 요청입니다."),
                Arguments.of("integrity", 500, "INTERNAL_ERROR", "서버에서 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요."),
                Arguments.of("unexpected", 500, "INTERNAL_ERROR", "서버에서 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요."));
    }

    @ParameterizedTest(name = "{0} → {1} {2}")
    @MethodSource("thrownExceptions")
    void mapsException(String name, int httpStatus, String code, String message) throws Exception {
        expectError(get("/api/test/throw/{name}", name), httpStatus, code, message);
    }

    @Test
    void invalidBodyReturnsFieldMessage() throws Exception {
        expectError(json("""
                {"name":" "}"""), 400, "VALIDATION_ERROR", "이름을 입력해 주세요.");
    }

    @Test
    void brokenJsonIs400() throws Exception {
        expectError(json("{\"name\":"), 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다.");
    }

    @Test
    void nonNumericPathValueIs400() throws Exception {
        expectError(get("/api/test/items/abc"), 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다.");
    }

    @Test
    void invalidQueryValueIs400() throws Exception {
        expectError(get("/api/test/pages").param("size", "0"), 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다.");
    }

    @Test
    void missingQueryValueKeeps400() throws Exception {
        expectError(get("/api/test/pages"), 400, "VALIDATION_ERROR", "입력 값이 올바르지 않습니다.");
    }

    @Test
    void unsupportedMethodIs405WithAllowHeader() throws Exception {
        expectError(get("/api/test/body"), 405, "VALIDATION_ERROR", "지원하지 않는 요청입니다.")
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"));
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        expectError(post("/api/test/body").contentType(MediaType.TEXT_PLAIN).content("name"),
                415, "VALIDATION_ERROR", "지원하지 않는 요청입니다.");
    }

    @Test
    void accessDeniedWithoutLoginIs401() throws Exception {
        // 비로그인으로 열려 있는 경로에서 거부되면 Security가 401로 처리한다
        mockMvc.perform(get("/open/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private MockHttpServletRequestBuilder json(String body) {
        return post("/api/test/body").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions expectError(MockHttpServletRequestBuilder builder,
                                      int httpStatus, String code, String message) throws Exception {
        return mockMvc.perform(builder.with(user("tester")))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("password_hash", "Exception"));
    }

    record Body(@NotBlank(message = "이름을 입력해 주세요.") String name) {
    }

    @RestController
    static class FailingController {

        @GetMapping("/api/test/throw/{name}")
        void fail(@PathVariable String name) {
            throw THROWABLES.get(name).get();
        }

        @GetMapping("/open/denied")
        void denied() {
            throw new AccessDeniedException(SECRET);
        }

        @PostMapping("/api/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/api/test/items/{id}")
        void item(@PathVariable Long id) {
        }

        @GetMapping("/api/test/pages")
        void page(@RequestParam @Min(1) int size) {
        }
    }
}
