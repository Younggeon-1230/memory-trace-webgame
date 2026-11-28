package com.metamong.memorytrace.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.game.service.SessionService;
import com.metamong.memorytrace.testdb.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T13: 세션 시작 동시 2건. 스레드마다 commit해야 하므로 @Transactional을 붙이지 않는다.
 * 다른 테스트가 샘플의 행 수를 검사하므로, 테이블을 비우지 않고 이 테스트가 만든 회원의 데이터만 지운다.
 */
@AutoConfigureMockMvc
class SessionStartConcurrencyIntegrationTest extends IntegrationTestSupport {

    /** 이 테스트가 만드는 회원의 아이디 앞부분. 삭제 대상을 고르는 기준이다. */
    private static final String USER_PREFIX = "t13_";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @MockitoSpyBean
    private SessionService sessionService;

    private record Started(int status, long sessionId) {
    }

    @BeforeEach
    void publishCase3() {
        jdbc.update("UPDATE case_file SET validation_status = 'VALID' WHERE case_id = 3");
    }

    @AfterEach
    void removeOwnData() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            verifyTestDatabase(connection);
            String mySessions = """
                    SELECT p.session_id FROM play_session p JOIN users u ON u.user_id = p.user_id
                    WHERE u.login_id LIKE 't13!_%' ESCAPE '!'""";
            try (Statement statement = connection.createStatement()) {
                for (String table : List.of("session_action", "session_clue", "session_suspect")) {
                    statement.executeUpdate("DELETE FROM " + table + " WHERE session_id IN (" + mySessions + ")");
                }
                statement.executeUpdate("""
                        DELETE FROM play_session WHERE user_id IN (
                            SELECT user_id FROM users WHERE login_id LIKE 't13!_%' ESCAPE '!')""");
                statement.executeUpdate("DELETE FROM users WHERE login_id LIKE 't13!_%' ESCAPE '!'");
                // 샘플의 원래 상태
                statement.executeUpdate("UPDATE case_file SET validation_status = 'PENDING' WHERE case_id = 3");
            }
        }
    }

    @Test
    @DisplayName("T13 충돌 재현: 상대가 commit하기 전에 시작한 요청은 중복 충돌 뒤 그 세션을 200으로 받는다")
    void conflictingStartReturnsTheOtherSession() throws Exception {
        User player = newUser(USER_PREFIX + "conflict");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try {
                long otherSessionId = insertUncommittedSession(other, player.getId());

                Future<Started> request = executor.submit(() -> start(player));
                // 상대의 행은 조회에 보이지 않으므로 요청은 생성을 시도하고, UNIQUE 잠금에서 기다린다
                assertThatThrownBy(() -> request.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                other.commit();

                Started result = request.get(20, TimeUnit.SECONDS);
                assertThat(result).isEqualTo(new Started(200, otherSessionId));
                // 처음 조회에서 찾은 것이 아니라 충돌 뒤의 재조회 경로를 지났다
                verify(sessionService).findInProgress(player.getId(), 3L);
                assertOneSessionWithFourSuspects(player);
            } finally {
                other.rollback();
                other.setAutoCommit(true);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @RepeatedTest(5)
    @DisplayName("T13 동시 요청 2건: 진행 세션 1개, 두 응답의 sessionId가 같다")
    void simultaneousStarts(org.junit.jupiter.api.RepetitionInfo repetition) throws Exception {
        User player = newUser(USER_PREFIX + "race" + repetition.getCurrentRepetition());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Started>> requests = List.of(
                    executor.submit(() -> startAfter(barrier, player)),
                    executor.submit(() -> startAfter(barrier, player)));
            Started first = requests.get(0).get(20, TimeUnit.SECONDS);
            Started second = requests.get(1).get(20, TimeUnit.SECONDS);

            assertThat(List.of(first.status(), second.status())).containsExactlyInAnyOrder(201, 200);
            assertThat(second.sessionId()).isEqualTo(first.sessionId());
            assertOneSessionWithFourSuspects(player);
        } finally {
            executor.shutdownNow();
        }
    }

    private Started startAfter(CyclicBarrier barrier, User player) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return start(player);
    }

    private Started start(User player) throws Exception {
        var response = mockMvc.perform(post("/api/cases/3/sessions").with(user(UserPrincipal.from(player))))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        return new Started(response.getStatus(), objectMapper.readTree(body).path("sessionId").asLong());
    }

    private static long insertUncommittedSession(Connection connection, long userId) throws SQLException {
        long sessionId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO play_session (user_id, case_id, status, score, active_flag, started_at)
                VALUES (?, 3, 'IN_PROGRESS', 100, 1, '2026-11-28 12:00:00')
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                sessionId = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO session_suspect (session_id, case_suspect_id, status, updated_at)
                SELECT ?, case_suspect_id, 'ACTIVE', '2026-11-28 12:00:00' FROM case_suspect WHERE case_id = 3
                """)) {
            statement.setLong(1, sessionId);
            statement.executeUpdate();
        }
        return sessionId;
    }

    private void assertOneSessionWithFourSuspects(User player) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM play_session WHERE user_id = ?", Integer.class, player.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM session_suspect s JOIN play_session p ON p.session_id = s.session_id
                WHERE p.user_id = ?
                """, Integer.class, player.getId())).isEqualTo(4);
    }

    private User newUser(String loginId) {
        return users.saveAndFlush(User.signUp(loginId, "not-used-in-this-test", "동시탐정",
                LocalDateTime.of(2026, 11, 28, 12, 0)));
    }
}
