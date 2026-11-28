package com.metamong.memorytrace.testdb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 DB를 docs의 스키마·샘플 데이터 상태로 다시 만든다.
 * 순서: ① SQL 전체 검사 → ② 연결한 DB와 sql_mode 확인 → ③ 삭제 → ④ 생성·입력. ①②가 끝나기 전에는 아무것도 바꾸지 않는다.
 */
final class TestDatabaseInitializer {

    static final Path SCHEMA = Path.of("..", "docs", "01_schema.sql");
    static final Path SAMPLE = Path.of("..", "docs", "02_sample.sql");

    private TestDatabaseInitializer() {
    }

    static void reset(Connection connection) throws SQLException {
        reset(connection, read(SCHEMA), read(SAMPLE));
    }

    static void reset(Connection connection, String schemaSql, String sampleSql) throws SQLException {
        List<String> statements = new ArrayList<>(SqlScript.parse(schemaSql));
        statements.addAll(SqlScript.parse(sampleSql));

        TestDatabaseGuard.verify(connection);
        TestDatabaseGuard.verifySqlMode(connection);

        try (Statement statement = connection.createStatement()) {
            // 삭제 대상은 코드에 고정한 12개 테이블뿐이다. FK 때문에 만든 순서의 반대로 지운다
            for (int i = SqlScript.TABLES.size() - 1; i >= 0; i--) {
                statement.execute("DROP TABLE IF EXISTS " + SqlScript.TABLES.get(i));
            }
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
