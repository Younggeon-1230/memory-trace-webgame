package com.metamong.memorytrace.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.testdb.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/cases (11장 1-3). 테스트 DB의 샘플 데이터를 쓴다.
 * 샘플의 사건은 PENDING이므로 필요한 사건만 테스트 안에서 VALID로 바꾸고 (D28),
 * 모든 변경은 롤백한다.
 */
@AutoConfigureMockMvc
@Transactional
class CaseListIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("샘플 그대로(모두 PENDING)면 빈 목록")
    void pendingCasesAreHidden() throws Exception {
        assertThat(casesOf(newUser("list_pending"))).isEmpty();
    }

    @Test
    @DisplayName("T15(목록): 3건이 표시 순서로 나오고 JSON 키가 정확히 일치한다")
    void threeCasesWithExactKeys() throws Exception {
        publishAll();
        // ID 순서가 아니라 display_order 순서인지 구분되도록 순서를 뒤집는다
        jdbc.update("UPDATE case_file SET display_order = 10 - display_order");

        JsonNode cases = casesOf(newUser("list_keys"));

        assertThat(cases.findValuesAsText("caseCode")).containsExactly("CASE03", "CASE02", "CASE01");
        for (JsonNode item : cases) {
            assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                    "caseId", "caseCode", "title", "subtitle", "sceneImagePath", "myStatus");
            assertThat(fieldNames(item.get("myStatus"))).containsExactlyInAnyOrder(
                    "inProgressSessionId", "playCount", "bestGrade", "bestScore");
            assertThat(item.get("myStatus").get("inProgressSessionId").isNull()).isTrue();
            assertThat(item.get("myStatus").get("bestGrade").isNull()).isTrue();
            assertThat(item.get("myStatus").get("bestScore").isNull()).isTrue();
            assertThat(item.get("myStatus").get("playCount").asInt()).isZero();
        }
        JsonNode case3 = cases.get(0);
        assertThat(case3.get("title").asText()).isEqualTo("들어간 발자국만 있는 창고");
        assertThat(case3.get("subtitle").asText()).isEqualTo("남겨진 발자국의 모순을 풀어라");
        assertThat(case3.get("sceneImagePath").asText()).isEqualTo("/scenes/case03_scene.webp");
    }

    @ParameterizedTest(name = "is_enabled={0}, {1} → 노출 {2}")
    @CsvSource({
            "true,  VALID,   true",
            "true,  PENDING, false",
            "true,  INVALID, false",
            "false, VALID,   false",
            "false, PENDING, false",
            "false, INVALID, false",
    })
    @DisplayName("T32(목록): 켜져 있고 VALID인 사건만 노출")
    void onlyEnabledAndValidIsListed(boolean enabled, String validationStatus, boolean listed) throws Exception {
        jdbc.update("UPDATE case_file SET is_enabled = ?, validation_status = ? WHERE case_code = 'CASE03'",
                enabled, validationStatus);

        List<String> codes = casesOf(newUser("list_t32")).findValuesAsText("caseCode");

        assertThat(codes).isEqualTo(listed ? List.of("CASE03") : List.of());
    }

    @Test
    @DisplayName("샘플의 CASE 3 해결 기록은 그 회원에게만 보인다")
    void recordsAreSeparatedByUser() throws Exception {
        publishAll();
        User owner = users.findByLoginId("detective01").orElseThrow();
        User other = newUser("list_other");

        JsonNode mine = statusOf(casesOf(owner), "CASE03");
        assertThat(mine.get("playCount").asInt()).isEqualTo(1);
        assertThat(mine.get("bestScore").asInt()).isEqualTo(100);
        assertThat(mine.get("bestGrade").asText()).isEqualTo("A");

        JsonNode theirs = statusOf(casesOf(other), "CASE03");
        assertThat(theirs.get("playCount").asInt()).isZero();
        assertThat(theirs.get("bestScore").isNull()).isTrue();
    }

    @Test
    @DisplayName("진행·실패·해결 세션이 사건별로 계산된다")
    void myStatusIsCalculatedPerCase() throws Exception {
        publishAll();
        User player = newUser("list_player");
        insertEndedSession(player, "CASE01", "FAILED", 0, null);
        insertEndedSession(player, "CASE01", "SOLVED", 70, "B");
        insertEndedSession(player, "CASE01", "SOLVED", 90, "A");
        long activeId = insertActiveSession(player, "CASE01");
        insertEndedSession(player, "CASE02", "FAILED", 0, null);

        JsonNode cases = casesOf(player);

        JsonNode case1 = statusOf(cases, "CASE01");
        assertThat(case1.get("inProgressSessionId").asLong()).isEqualTo(activeId);
        assertThat(case1.get("playCount").asInt()).isEqualTo(3);
        assertThat(case1.get("bestScore").asInt()).isEqualTo(90);
        assertThat(case1.get("bestGrade").asText()).isEqualTo("A");

        JsonNode case2 = statusOf(cases, "CASE02");
        assertThat(case2.get("playCount").asInt()).isEqualTo(1);
        assertThat(case2.get("inProgressSessionId").isNull()).isTrue();
        assertThat(case2.get("bestGrade").isNull()).isTrue();

        assertThat(statusOf(cases, "CASE03").get("playCount").asInt()).isZero();
    }

    private void publishAll() {
        jdbc.update("UPDATE case_file SET validation_status = 'VALID'");
    }

    private User newUser(String loginId) {
        return users.saveAndFlush(User.signUp(loginId, "not-used-in-this-test", "목록탐정",
                LocalDateTime.of(2026, 11, 28, 12, 0)));
    }

    private JsonNode casesOf(User account) throws Exception {
        String body = mockMvc.perform(get("/api/cases").with(user(UserPrincipal.from(account))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static JsonNode statusOf(JsonNode cases, String caseCode) {
        for (JsonNode item : cases) {
            if (item.get("caseCode").asText().equals(caseCode)) {
                return item.get("myStatus");
            }
        }
        throw new AssertionError("목록에 없는 사건: " + caseCode);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void insertEndedSession(User account, String caseCode, String sessionStatus, int score, String grade) {
        jdbc.update("""
                INSERT INTO play_session (user_id, case_id, status, score, grade, active_flag, started_at, ended_at)
                SELECT ?, case_id, ?, ?, ?, NULL, '2026-11-28 12:00:00', '2026-11-28 12:10:00'
                FROM case_file WHERE case_code = ?
                """, account.getId(), sessionStatus, score, grade, caseCode);
    }

    private long insertActiveSession(User account, String caseCode) {
        jdbc.update("""
                INSERT INTO play_session (user_id, case_id, status, score, active_flag, started_at)
                SELECT ?, case_id, 'IN_PROGRESS', 100, 1, '2026-11-28 13:00:00'
                FROM case_file WHERE case_code = ?
                """, account.getId(), caseCode);
        return jdbc.queryForObject(
                "SELECT session_id FROM play_session WHERE user_id = ? AND status = 'IN_PROGRESS'",
                Long.class, account.getId());
    }
}
