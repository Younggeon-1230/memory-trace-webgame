package com.metamong.memorytrace.testdb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * 테스트 DB를 지우거나 만들기 전에, "지금 이 연결"이 어느 DB에 붙어 있는지 확인한다.
 * URL 문자열이 아니라 서버와 드라이버가 알려 주는 실제 값을 본다.
 */
final class TestDatabaseGuard {

    /** 환경 변수로 바꿀 수 없게 상수로 둔다. */
    static final String ALLOWED_DATABASE = "metamong_case_test";

    private TestDatabaseGuard() {
    }

    /** 허용된 테스트 DB가 아니면 예외를 던진다. 읽기 외에는 아무것도 실행하지 않는다. */
    static void verify(Connection connection) throws SQLException {
        String selected;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            selected = result.next() ? result.getString(1) : null;
        }
        String catalog = connection.getCatalog();

        if (!ALLOWED_DATABASE.equals(selected) || !ALLOWED_DATABASE.equals(catalog)) {
            throw new IllegalStateException(
                    "테스트 DB가 아니므로 중단합니다. 아무것도 삭제하지 않았습니다. "
                            + "허용: " + ALLOWED_DATABASE
                            + ", SELECT DATABASE(): " + selected
                            + ", getCatalog(): " + catalog);
        }
    }

    /**
     * SQL 검사기는 문자열 안의 \' 를 이스케이프로 읽는다. NO_BACKSLASH_ESCAPES에서는 MySQL이 다르게 읽으므로
     * 스크립트를 실행하기 전에 중단한다.
     */
    static void verifySqlMode(Connection connection) throws SQLException {
        String sqlMode;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
            sqlMode = result.next() ? result.getString(1) : null;
        }
        if (sqlMode == null || sqlMode.toUpperCase(Locale.ROOT).contains("NO_BACKSLASH_ESCAPES")) {
            throw new IllegalStateException(
                    "sql_mode를 확인할 수 없거나 NO_BACKSLASH_ESCAPES가 켜져 있어 중단합니다. "
                            + "아무것도 삭제하지 않았습니다. sql_mode: " + sqlMode);
        }
    }
}
