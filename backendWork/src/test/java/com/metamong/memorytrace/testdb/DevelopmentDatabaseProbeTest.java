package com.metamong.memorytrace.testdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** T26의 연결이 다른 DB를 가리킬 때를 DB 없이 확인한다. 서버로 나간 모든 문장을 기록한다. */
class DevelopmentDatabaseProbeTest {

    private final List<String> sent = new ArrayList<>();
    private Connection connection;
    private Statement statement;

    private DevelopmentDatabaseProbe probe(String selectedDatabase, String catalog) throws SQLException {
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        ResultSet database = mock(ResultSet.class);
        ResultSet grants = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn(catalog);
        when(database.next()).thenReturn(true);
        when(database.getString(1)).thenReturn(selectedDatabase);
        when(grants.next()).thenReturn(false);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            sent.add(sql);
            return sql.equals("SELECT DATABASE()") ? database : grants;
        });
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return false;
        });
        return new DevelopmentDatabaseProbe(() -> connection);
    }

    @ParameterizedTest(name = "DATABASE()={0}, catalog={1}")
    @CsvSource(nullValues = "NULL", value = {
            "metamong_case, metamong_case",
            "NULL, NULL",
            "metamong_case_test, metamong_case",
    })
    @DisplayName("테스트 DB가 아니면 SHOW GRANTS, SELECT, UPDATE를 보내지 않고 연결을 닫는다")
    void sendsNothingToOtherDatabase(String selected, String catalog) throws SQLException {
        DevelopmentDatabaseProbe probe = probe(selected, catalog);

        assertThatThrownBy(probe::readGrants).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> probe.executionError("SELECT * FROM metamong_case.users LIMIT 1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> probe.executionError("UPDATE metamong_case.users SET nickname = nickname WHERE 1 = 0"))
                .isInstanceOf(IllegalStateException.class);

        // 서버로 간 것은 확인용 쿼리뿐이다
        assertThat(sent).containsExactly("SELECT DATABASE()", "SELECT DATABASE()", "SELECT DATABASE()");
        verify(statement, never()).executeUpdate(anyString());
        verify(connection, org.mockito.Mockito.times(3)).close();
    }

    @Test
    @DisplayName("테스트 DB면 확인한 뒤에 쿼리를 보낸다")
    void queriesAfterVerification() throws SQLException {
        DevelopmentDatabaseProbe probe = probe("metamong_case_test", "metamong_case_test");

        probe.readGrants();
        Optional<SQLException> error = probe.executionError("SELECT 1");

        assertThat(error).as("성공한 문장은 오류가 없다").isEmpty();
        assertThat(sent).containsExactly(
                "SELECT DATABASE()", "SHOW GRANTS FOR CURRENT_USER()", "SELECT DATABASE()", "SELECT 1");
    }

    @ParameterizedTest
    @ValueSource(ints = {1044, 1142, 1045})
    @DisplayName("접속 단계의 오류는 코드가 1044·1142여도 '거부됨'으로 돌려주지 않고 던진다")
    void connectionErrorIsNotDenial(int errorCode) {
        SQLException connectFailure = new SQLException("connect failed", "42000", errorCode);
        DevelopmentDatabaseProbe probe = new DevelopmentDatabaseProbe(() -> {
            throw connectFailure;
        });

        assertThatThrownBy(() -> probe.executionError("SELECT * FROM metamong_case.users LIMIT 1"))
                .isSameAs(connectFailure);
    }

    @Test
    @DisplayName("DB 확인이나 문장 준비 단계의 오류도 던지며, 대상 문장은 보내지 않는다")
    void errorBeforeStatementIsNotDenial() throws SQLException {
        SQLException failure = new SQLException("denied early", "42000", 1142);

        DevelopmentDatabaseProbe duringVerify = probe("metamong_case_test", "metamong_case_test");
        when(connection.getCatalog()).thenThrow(failure);
        assertThatThrownBy(() -> duringVerify.executionError("SELECT 1")).isSameAs(failure);

        DevelopmentDatabaseProbe duringPrepare = probe("metamong_case_test", "metamong_case_test");
        when(connection.createStatement()).thenReturn(statement).thenThrow(failure);
        assertThatThrownBy(() -> duringPrepare.executionError("SELECT 1")).isSameAs(failure);

        assertThat(sent).doesNotContain("SELECT 1");
    }

    @Test
    @DisplayName("확인을 통과한 뒤 대상 문장의 실행이 거부되면 그 오류를 돌려준다")
    void returnsErrorFromStatementItself() throws SQLException {
        DevelopmentDatabaseProbe probe = probe("metamong_case_test", "metamong_case_test");
        SQLException denied = new SQLException("SELECT command denied", "42000", 1142);
        when(statement.execute(anyString())).thenThrow(denied);

        assertThat(probe.executionError("SELECT * FROM metamong_case.users LIMIT 1")).containsSame(denied);
    }
}
