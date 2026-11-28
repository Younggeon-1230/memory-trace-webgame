package com.metamong.memorytrace.testdb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * T26이 보내는 쿼리를 모아 둔다. 모든 쿼리는 "테스트 DB에 붙어 있음"을 확인한 연결에서만 나간다.
 * TEST_DB_URL을 실수로 개발 DB로 지정하면 권한 조회나 접근 시도도 하지 않고 중단한다.
 */
final class DevelopmentDatabaseProbe {

    /** 연결을 여는 방법. DB 없는 테스트에서는 가짜 연결을 넣는다. */
    @FunctionalInterface
    interface ConnectionOpener {
        Connection open() throws SQLException;
    }

    private final ConnectionOpener opener;

    DevelopmentDatabaseProbe(ConnectionOpener opener) {
        this.opener = opener;
    }

    List<String> readGrants() throws SQLException {
        try (Connection connection = openVerified();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            List<String> grants = new ArrayList<>();
            while (result.next()) {
                grants.add(result.getString(1));
            }
            return grants;
        }
    }

    /**
     * 문장을 실행해 보고, <b>그 문장의 실행이</b> 실패했으면 오류를 돌려준다. 성공하면 비어 있다.
     * 접속, DB 확인, 문장 준비 단계의 오류는 돌려주지 않고 그대로 던진다. 접속하다가 난 권한 오류(1044 등)를
     * "개발 DB 접근 거부"로 착각해 문장을 보내지도 않고 통과하는 일을 막는다.
     */
    Optional<SQLException> executionError(String sql) throws SQLException {
        try (Connection connection = openVerified();
             Statement statement = connection.createStatement()) {
            try {
                statement.execute(sql);
                return Optional.empty();
            } catch (SQLException error) {
                return Optional.of(error);
            }
        }
    }

    /** 확인에 실패하면 연결을 닫고 예외를 전달한다. */
    private Connection openVerified() throws SQLException {
        Connection connection = opener.open();
        try {
            TestDatabaseGuard.verify(connection);
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return connection;
    }
}
