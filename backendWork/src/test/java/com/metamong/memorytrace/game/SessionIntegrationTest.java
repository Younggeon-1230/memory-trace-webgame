package com.metamong.memorytrace.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.testdb.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수사 시작·이어하기와 게임 상태 조회 (11장 1-4). 테스트 DB의 샘플 데이터를 쓰며 모든 변경을 롤백한다.
 * 사건은 필요한 것만 테스트 안에서 VALID로 바꾼다 (D28).
 */
@AutoConfigureMockMvc
@Transactional
class SessionIntegrationTest extends IntegrationTestSupport {

    private static final long CASE3 = 3L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private record Reply(int status, String body, JsonNode json) {
    }

    @Test
    @DisplayName("처음은 201, 다시 시작하면 같은 세션이 200. 용의자 4명은 모두 ACTIVE")
    void startThenResume() throws Exception {
        publish(CASE3);
        User player = newUser("start_user");

        Reply first = start(player, CASE3);
        Reply second = start(player, CASE3);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        long sessionId = first.json().get("sessionId").asLong();
        assertThat(second.json().get("sessionId").asLong()).isEqualTo(sessionId);
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM play_session WHERE user_id = ?", Integer.class, player.getId())).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT status FROM session_suspect WHERE session_id = ?", String.class, sessionId))
                .containsExactly("ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE");
        assertThat(jdbc.queryForMap("""
                SELECT status, score, wrong_exclusion_count, accusation_count, active_flag + 0 AS flag, ended_at
                FROM play_session WHERE session_id = ?
                """, sessionId))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("score", 100)
                .containsEntry("wrong_exclusion_count", 0)
                .containsEntry("accusation_count", 0)
                .containsEntry("ended_at", null)
                .hasEntrySatisfying("flag", flag -> assertThat(((Number) flag).intValue()).isEqualTo(1));
    }

    @Test
    @DisplayName("T15: 새 수사의 게임 상태는 키가 정확히 일치하고 숨길 정보가 없다")
    void newStateHasExactKeysAndNoSecrets() throws Exception {
        publish(CASE3);

        Reply reply = start(newUser("keys_user"), CASE3);

        assertExactKeys(reply.json());
        JsonNode state = reply.json();
        assertThat(state.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(state.get("score").asInt()).isEqualTo(100);
        assertThat(state.get("accusationsLeft").asInt()).isEqualTo(2);
        assertThat(state.get("clues")).isEmpty();
        assertThat(state.get("sceneAreas")).hasSize(5)
                .allSatisfy(area -> assertThat(area.get("collected").asBoolean()).isFalse());
        assertThat(state.get("suspects")).hasSize(4).allSatisfy(suspect -> {
            assertThat(suspect.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(suspect.get("basisClueId").isNull()).isTrue();
        });
        // 수집한 단서가 없으므로 어떤 상세 내용도, 정답·진상·반박 문구도 나오면 안 된다
        assertThat(reply.body()).doesNotContain(secretTexts(CASE3).toArray(String[]::new));
        assertThat(reply.body()).doesNotContain(jdbc.queryForList(
                "SELECT detail_text FROM clue WHERE case_id = ?", String.class, CASE3).toArray(String[]::new));
    }

    @Test
    @DisplayName("T15: 샘플의 해결된 세션 — 수집 단서, 정렬, 날짜, 배제 근거")
    void solvedSampleSession() throws Exception {
        Reply reply = state(users.findByLoginId("detective01").orElseThrow(), 1L);

        assertThat(reply.status()).isEqualTo(200);
        assertExactKeys(reply.json());
        JsonNode state = reply.json();
        assertThat(state.get("status").asText()).isEqualTo("SOLVED");
        assertThat(state.get("score").asInt()).isEqualTo(100);
        assertThat(state.get("accusationsLeft").asInt()).isEqualTo(1);
        assertThat(state.get("wrongExclusionCount").asInt()).isEqualTo(1);
        assertThat(state.get("case").get("caseCode").asText()).isEqualTo("CASE03");
        // I1(25)을 E5(24)보다 먼저 수집했지만 표시 순서로 나온다
        assertThat(longs(state.get("clues"), "clueId")).containsExactly(20L, 21L, 22L, 23L, 24L, 25L);
        assertThat(state.get("clues").get(0).get("collectedAt").asText()).isEqualTo("2026-12-01T20:01:00");
        assertThat(state.get("clues").get(5).get("kind").asText()).isEqualTo("INFERENCE");
        // 추론 단서는 클릭 영역이 없다
        assertThat(longs(state.get("sceneAreas"), "clueId")).containsExactly(20L, 21L, 22L, 23L, 24L);
        assertThat(state.get("sceneAreas").get(0).get("x").asDouble()).isEqualTo(0.514);
        // 규칙 ID 순서: 5(SHAPE_IS), 6(MAX_WEIGHT), 7(COLOR_IS)
        assertThat(texts(state.get("traceDictionary"), "basisText")).containsExactlyElementsOf(jdbc.queryForList(
                "SELECT basis_text FROM evidence_rule WHERE rule_id IN (5, 6, 7) ORDER BY rule_id", String.class));

        JsonNode suspects = state.get("suspects");
        assertThat(longs(suspects, "caseSuspectId")).containsExactly(9L, 10L, 11L, 12L);
        assertThat(suspects.get(0).get("basisClueId").isNull()).isTrue();
        assertThat(suspects.get(1).get("basisClueId").asLong()).isEqualTo(24);
        assertThat(suspects.get(2).get("basisClueId").asLong()).isEqualTo(25);
        assertThat(suspects.get(3).get("basisClueId").asLong()).isEqualTo(20);
        JsonNode spearow = suspects.get(3);
        assertThat(spearow.get("nameKo").asText()).isEqualTo("깨비참");
        assertThat(spearow.get("heightM").asDouble()).isEqualTo(0.3);
        assertThat(spearow.get("weightKg").asDouble()).isEqualTo(2.0);
        assertThat(spearow.get("types")).extracting(JsonNode::asText).containsExactly("normal", "flying");
        assertThat(spearow.get("spritePath").asText()).isEqualTo("/sprites/21.png");

        // 종료된 세션이어도 정답·진상·반박 문구는 결과 API에서만 보낸다
        assertThat(reply.body()).doesNotContain(secretTexts(CASE3).toArray(String[]::new));
    }

    @Test
    @DisplayName("판정에 쓰는 특징(is_judgment)만 보낸다")
    void onlyJudgmentTraits() throws Exception {
        publish(2L);
        jdbc.update("UPDATE pokemon_trait SET is_judgment = FALSE WHERE trait_code = 'STARTLE_FLARE'");
        jdbc.update("UPDATE pokemon_trait SET is_judgment = TRUE WHERE trait_code = 'CONSTANT_TAIL_FLAME'");

        Reply reply = start(newUser("trait_user"), 2L);

        List<String> traitCodes = new ArrayList<>();
        for (JsonNode suspect : reply.json().get("suspects")) {
            for (JsonNode trait : suspect.get("traits")) {
                assertThat(fieldNames(trait)).containsExactlyInAnyOrder("traitCode", "description");
                traitCodes.add(trait.get("traitCode").asText());
            }
        }
        assertThat(traitCodes).containsExactly("CONSTANT_TAIL_FLAME");
    }

    @ParameterizedTest(name = "is_enabled={0}, {1} → 시작 가능 {2}")
    @CsvSource({
            "true,  VALID,   true",
            "true,  PENDING, false",
            "true,  INVALID, false",
            "false, VALID,   false",
            "false, PENDING, false",
            "false, INVALID, false",
    })
    @DisplayName("T32: 공개 중일 때만 시작·이어하기, 기존 세션 조회는 항상 가능")
    void publishConditionForStartAndExistingSession(boolean enabled, String validationStatus,
                                                    boolean published) throws Exception {
        publish(CASE3);
        User owner = newUser("t32_owner");
        long sessionId = start(owner, CASE3).json().get("sessionId").asLong();
        change("UPDATE case_file SET is_enabled = ?, validation_status = ? WHERE case_id = 3",
                enabled, validationStatus);

        Reply newcomer = start(newUser("t32_newcomer"), CASE3);
        Reply resume = start(owner, CASE3);
        Reply existing = state(owner, sessionId);

        assertThat(newcomer.status()).isEqualTo(published ? 201 : 404);
        // 진행 중 세션이 있어도 공개 중이 아니면 이 API는 404
        assertThat(resume.status()).isEqualTo(published ? 200 : 404);
        assertThat(existing.status()).isEqualTo(200);
        assertExactKeys(existing.json());
        if (!published) {
            assertThat(newcomer.json().get("code").asText()).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    @DisplayName("7.5: 검증에서 위반이 될 데이터여도 기존 세션 조회는 성공한다")
    void existingSessionSurvivesInvalidData() throws Exception {
        publish(CASE3);
        User owner = newUser("broken_user");
        long sessionId = start(owner, CASE3).json().get("sessionId").asLong();
        change("UPDATE case_file SET validation_status = 'INVALID' WHERE case_id = 3");
        // 이미지 밖으로 나가는 클릭 영역과 세션을 만든 뒤 늘어난 사건 용의자
        change("UPDATE clue SET area_x = 0.9000, area_w = 0.5000 WHERE clue_id = 20");
        change("""
                INSERT INTO case_suspect (case_id, pokemon_id, suspicion_text, display_order)
                VALUES (3, 54, NULL, 9)
                """);

        Reply added = state(owner, sessionId);

        assertThat(added.status()).isEqualTo(200);
        assertThat(added.json().get("suspects")).hasSize(4);
        assertThat(added.json().get("sceneAreas").get(0).get("x").asDouble()).isEqualTo(0.9);
        assertThat(added.json().get("sceneAreas").get(0).get("w").asDouble()).isEqualTo(0.5);

        // 용의자가 4명보다 적으면 있는 만큼
        change("DELETE FROM session_suspect WHERE session_id = ? AND case_suspect_id = 12", sessionId);

        Reply fewer = state(owner, sessionId);

        assertThat(fewer.status()).isEqualTo(200);
        assertThat(longs(fewer.json().get("suspects"), "caseSuspectId")).containsExactly(9L, 10L, 11L);
    }

    @Test
    @DisplayName("남의 세션은 403, 없는 세션·사건은 404, 숫자가 아닌 ID는 400")
    void accessErrors() throws Exception {
        User stranger = newUser("stranger_user");

        Reply others = state(stranger, 1L);
        assertThat(others.status()).isEqualTo(403);
        assertThat(others.body()).isEqualTo("""
                {"code":"NOT_SESSION_OWNER","message":"다른 탐정의 수사 기록입니다."}""");

        Reply missing = state(stranger, 999_999L);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.json().get("code").asText()).isEqualTo("NOT_FOUND");

        assertThat(start(stranger, 999_999L).status()).isEqualTo(404);
        assertThat(send(stranger, get("/api/sessions/abc")).status()).isEqualTo(400);
    }

    private static void assertExactKeys(JsonNode state) {
        assertThat(fieldNames(state)).containsExactlyInAnyOrder("sessionId", "status", "score", "accusationsLeft",
                "wrongExclusionCount", "case", "sceneAreas", "clues", "traceDictionary", "suspects");
        assertThat(fieldNames(state.get("case"))).containsExactlyInAnyOrder(
                "caseId", "caseCode", "title", "briefing", "sceneImagePath");
        assertThat(state.get("sceneAreas")).isNotEmpty().allSatisfy(area -> assertThat(fieldNames(area))
                .containsExactlyInAnyOrder("clueId", "x", "y", "w", "h", "collected"));
        assertThat(state.get("clues")).allSatisfy(clue -> assertThat(fieldNames(clue))
                .containsExactlyInAnyOrder("clueId", "clueCode", "kind", "name", "summary", "detailText",
                        "collectedAt"));
        assertThat(state.get("traceDictionary")).isNotEmpty().allSatisfy(entry -> assertThat(fieldNames(entry))
                .containsExactly("basisText"));
        assertThat(state.get("suspects")).isNotEmpty().allSatisfy(suspect -> assertThat(fieldNames(suspect))
                .containsExactlyInAnyOrder("caseSuspectId", "pokemonId", "nameKo", "spritePath", "heightM",
                        "weightKg", "types", "color", "shape", "traits", "suspicionText", "status",
                        "basisClueId"));
    }

    /** 게임 상태에 절대 나오면 안 되는 문구: 진상·동기·반전과 반박 문구 */
    private List<String> secretTexts(long caseId) {
        List<String> secrets = new ArrayList<>(jdbc.queryForMap(
                        "SELECT truth_text, motive_text, twist_text FROM case_file WHERE case_id = ?", caseId)
                .values().stream().map(String::valueOf).toList());
        secrets.addAll(jdbc.queryForList(
                "SELECT rebuttal_text FROM clue WHERE case_id = ? AND rebuttal_text IS NOT NULL",
                String.class, caseId));
        assertThat(secrets).hasSizeGreaterThan(3);
        return secrets;
    }

    private void publish(long caseId) {
        change("UPDATE case_file SET is_enabled = TRUE, validation_status = 'VALID' WHERE case_id = ?", caseId);
    }

    /** SQL로 바꾼 값을 다음 요청이 다시 읽도록, 테스트 트랜잭션의 영속성 컨텍스트를 비운다. */
    private void change(String sql, Object... args) {
        entityManager.flush();
        jdbc.update(sql, args);
        entityManager.clear();
    }

    private User newUser(String loginId) {
        return users.saveAndFlush(User.signUp(loginId, "not-used-in-this-test", "세션탐정",
                LocalDateTime.of(2026, 11, 28, 12, 0)));
    }

    private Reply start(User account, long caseId) throws Exception {
        return send(account, post("/api/cases/{caseId}/sessions", caseId));
    }

    private Reply state(User account, long sessionId) throws Exception {
        return send(account, get("/api/sessions/{sessionId}", sessionId));
    }

    private Reply send(User account, MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request.with(user(UserPrincipal.from(account)))).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        return new Reply(response.getStatus(), body, objectMapper.readTree(body));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<Long> longs(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map(item -> item.get(field).asLong()).toList();
    }

    private static List<String> texts(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map(item -> item.get(field).asText()).toList();
    }
}
