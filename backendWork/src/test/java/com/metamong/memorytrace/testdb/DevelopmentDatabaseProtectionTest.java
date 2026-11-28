package com.metamong.memorytrace.testdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T26: 테스트 계정은 개발 DB를 읽지도 쓰지도 못해야 한다. */
@Tag("integration")
class DevelopmentDatabaseProtectionTest {

    private static final List<Integer> ACCESS_DENIED = List.of(1044, 1142);

    /** 연결은 반드시 probe를 거친다. probe는 DB 이름을 확인한 뒤에만 쿼리를 보낸다. */
    private static DevelopmentDatabaseProbe probe() {
        TestDatabaseSettings settings = TestDatabaseSettings.fromEnvironment();
        return new DevelopmentDatabaseProbe(
                () -> DriverManager.getConnection(settings.url(), settings.username(), settings.password()));
    }

    @Test
    @DisplayName("테스트 계정에는 테스트 DB 밖의 권한이 없다")
    void accountHasNoOtherPrivileges() throws SQLException {
        List<String> grants = probe().readGrants();

        assertThat(grants).allMatch(GrantPolicy::isAllowed);
        assertThat(grants).as("테스트 DB 권한이 있어야 합니다").anyMatch(GrantPolicy::isTestDatabaseGrant);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM metamong_case.users LIMIT 1",
            // 혹시 권한이 있어도 아무 행도 바뀌지 않는 문장으로 쓰기 권한을 확인한다
            "UPDATE metamong_case.users SET nickname = nickname WHERE 1 = 0",
    })
    @DisplayName("테스트 계정은 개발 DB에 접근하면 거부된다")
    void cannotTouchDevelopmentDatabase(String sql) {
        DevelopmentDatabaseProbe probe = probe();

        SQLException denied = catchThrowableOfType(SQLException.class, () -> probe.execute(sql));

        assertThat((Throwable) denied).as("거부되어야 합니다: %s", sql).isNotNull();
        assertThat(denied.getErrorCode()).isIn(ACCESS_DENIED);
    }
}
