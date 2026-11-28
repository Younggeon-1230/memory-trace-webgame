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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** T25: DB 없이 도는 안전장치 테스트. 가짜 연결로 "무엇을 실행했는지"를 기록해 확인한다. */
class TestDatabaseSafetyTest {

    private final List<String> executed = new ArrayList<>();
    private Statement statement;
    private String sqlMode = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES";

    private Connection fakeConnection(String selectedDatabase, String catalog) throws SQLException {
        Connection connection = mock(Connection.class);
        statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn(catalog);
        when(statement.executeQuery("SELECT DATABASE()")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn(selectedDatabase);
        ResultSet mode = mock(ResultSet.class);
        when(statement.executeQuery("SELECT @@SESSION.sql_mode")).thenReturn(mode);
        when(mode.next()).thenReturn(true);
        when(mode.getString(1)).thenAnswer(invocation -> sqlMode);
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            executed.add(invocation.getArgument(0));
            return false;
        });
        return connection;
    }

    @ParameterizedTest(name = "DATABASE()={0}, catalog={1}")
    @CsvSource(nullValues = "NULL", value = {
            "metamong_case, metamong_case",
            "NULL, NULL",
            "metamong_case_test2, metamong_case_test2",
            "old_metamong_case_test, old_metamong_case_test",
            "other_test, other_test",
            "METAMONG_CASE_TEST, METAMONG_CASE_TEST",
            "metamong_case, metamong_case_test",
            "metamong_case_test, metamong_case",
    })
    @DisplayName("허용한 DB가 아니면 삭제를 포함해 어떤 문장도 실행하지 않는다")
    void stopsBeforeAnyChange(String selected, String catalog) throws SQLException {
        Connection connection = fakeConnection(selected, catalog);

        assertThatThrownBy(() -> TestDatabaseInitializer.reset(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("아무것도 삭제하지 않았습니다");

        assertThat(executed).isEmpty();
        verify(statement, never()).executeUpdate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP DATABASE metamong_case",
            "DROP TABLE users",
            "ALTER TABLE users ADD x INT",
            "TRUNCATE TABLE users",
            "DELETE FROM users",
            "INSERT INTO metamong_case.users (login_id) VALUES ('a')",
            "INSERT INTO metamong_case . users (login_id) VALUES ('a')",
            "INSERT INTO `metamong_case`.`users` (login_id) VALUES ('a')",
            "CREATE TABLE users (id INT, FOREIGN KEY (id) REFERENCES metamong_case.pokemon (pokemon_id))",
            "CREATE TABLE users SELECT * FROM pokemon",
            "INSERT INTO users (login_id) SELECT login_id FROM users",
            "INSERT INTO unknown_table (a) VALUES (1)",
            "/*! DROP DATABASE metamong_case */",
            "# comment\nDROP TABLE users",
            "USE metamong_case DROP TABLE users",
    })
    @DisplayName("허용 밖의 문장이 하나라도 있으면 앞의 정상 문장도 실행하지 않는다")
    void rejectsWholeScript(String badStatement) throws SQLException {
        Connection connection = fakeConnection("metamong_case_test", "metamong_case_test");
        String schema = "CREATE TABLE users (id INT);\n" + badStatement + ";";

        assertThatThrownBy(() -> TestDatabaseInitializer.reset(connection, schema, ""))
                .isInstanceOf(IllegalStateException.class);

        assertThat(executed).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NO_BACKSLASH_ESCAPES",
            "'STRICT_TRANS_TABLES,NO_BACKSLASH_ESCAPES'",
            "NULL",
    })
    @DisplayName("sql_mode에 NO_BACKSLASH_ESCAPES가 있거나 확인할 수 없으면 아무것도 실행하지 않는다")
    void stopsOnNoBackslashEscapes(String mode) throws SQLException {
        Connection connection = fakeConnection("metamong_case_test", "metamong_case_test");
        sqlMode = mode;

        assertThatThrownBy(() -> TestDatabaseInitializer.reset(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_BACKSLASH_ESCAPES");

        assertThat(executed).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/metamong_case_test?allowMultiQueries=true",
            "jdbc:mysql://localhost:3306/metamong_case_test?characterEncoding=UTF-8&ALLOWMULTIQUERIES=TRUE",
            "jdbc:mysql://localhost:3306/metamong_case_test?allow%4DultiQueries=true",
            "jdbc:mysql://address=(host=localhost)(allowMultiQueries=true)/metamong_case_test",
    })
    @DisplayName("allowMultiQueries가 들어간 URL은 연결 전에 거절하고, 메시지에 URL을 담지 않는다")
    void rejectsMultiQueryUrl(String url) {
        assertThatThrownBy(() -> new TestDatabaseSettings(url, "user", "secret-value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowMultiQueries")
                .hasMessageNotContaining("jdbc:")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    @DisplayName("접속 설정을 출력해도 비밀번호가 나오지 않는다")
    void hidesPassword() {
        TestDatabaseSettings settings = new TestDatabaseSettings(
                "jdbc:mysql://localhost:3306/metamong_case_test?password=in-url", "user", "secret-value");

        assertThat(settings.toString()).doesNotContain("in-url", "secret-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--DROP TABLE users", "--x", "--"})
    @DisplayName("'--' 뒤에 공백이나 제어 문자가 없으면 주석이 아니므로 검사에서 빠지지 않는다")
    void doubleDashWithoutSpaceIsNotComment(String text) {
        assertThatThrownBy(() -> SqlScript.parse("CREATE TABLE users (id INT);\n" + text))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("'-- ', '--탭', '--줄바꿈'은 주석이고, 식 안의 '5--1'은 그대로 남는다")
    void followsMySqlCommentRule() {
        List<String> statements = SqlScript.parse(
                "-- a\n--\tb\n--\nINSERT INTO users (user_id) VALUES (5--1); -- tail");

        assertThat(statements).containsExactly("INSERT INTO users (user_id) VALUES (5--1)");
    }

    @Test
    @DisplayName("원본 SQL: CREATE DATABASE와 USE는 빼고 테이블 12개와 INSERT만 남는다")
    void parsesRealScripts() {
        List<String> schema = SqlScript.parse(TestDatabaseInitializer.read(TestDatabaseInitializer.SCHEMA));
        List<String> sample = SqlScript.parse(TestDatabaseInitializer.read(TestDatabaseInitializer.SAMPLE));

        assertThat(schema).hasSize(12).allMatch(sql -> sql.startsWith("CREATE TABLE "));
        assertThat(sample).hasSize(12).allMatch(sql -> sql.startsWith("INSERT INTO "));
        assertThat(String.join("\n", sample)).contains("니드런♀", "남겨진 발자국의 모순을 풀어라");
    }

    @Test
    @DisplayName("문자열 안의 세미콜론, 주석 기호, 점은 문장을 나누거나 거부하지 않는다")
    void keepsLiteralContent() {
        List<String> statements = SqlScript.parse(
                "INSERT INTO users (nickname) VALUES ('a;b -- c.d DROP it''s');");

        assertThat(statements).containsExactly("INSERT INTO users (nickname) VALUES ('a;b -- c.d DROP it''s')");
    }

    @Test
    @DisplayName("허용한 DB면 고정한 12개 테이블을 역순으로 지운 뒤 스크립트를 실행한다")
    void resetsAllowedDatabase() throws SQLException {
        Connection connection = fakeConnection("metamong_case_test", "metamong_case_test");

        TestDatabaseInitializer.reset(connection);

        assertThat(executed).hasSize(36);
        assertThat(executed.subList(0, 12)).allMatch(sql -> sql.startsWith("DROP TABLE IF EXISTS "));
        assertThat(executed.get(0)).isEqualTo("DROP TABLE IF EXISTS session_action");
        assertThat(executed.get(11)).isEqualTo("DROP TABLE IF EXISTS users");
        assertThat(executed).noneMatch(sql -> sql.startsWith("USE ") || sql.startsWith("CREATE DATABASE"));
    }
}
