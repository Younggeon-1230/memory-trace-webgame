package com.metamong.memorytrace.testdb;

import com.metamong.memorytrace.MemorytraceApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MySQL 통합 테스트의 공통 기반. gradlew integrationTest 에서만 실행된다.
 * 스프링 컨텍스트가 뜨기 전에 테스트 DB를 초기화한다(ddl-auto=validate가 테이블을 검사하므로).
 */
@Tag("integration")
@SpringBootTest(classes = MemorytraceApplication.class)
@ActiveProfiles("integration-test")
public abstract class IntegrationTestSupport {

    private static boolean prepared;

    /** commit한 테스트 데이터를 지우기 전에, 지울 바로 그 연결이 테스트 DB인지 확인한다 (9.4). */
    protected static void verifyTestDatabase(Connection connection) throws SQLException {
        TestDatabaseGuard.verify(connection);
    }

    /** 동적 속성은 환경 변수나 설정 파일보다 우선하므로 SPRING_DATASOURCE_* 가 있어도 테스트 DB를 쓴다. */
    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry registry) throws SQLException {
        TestDatabaseSettings settings = TestDatabaseSettings.fromEnvironment();
        prepareOnce(settings);
        registry.add("spring.datasource.url", settings::url);
        registry.add("spring.datasource.username", settings::username);
        registry.add("spring.datasource.password", settings::password);
    }

    private static synchronized void prepareOnce(TestDatabaseSettings settings) throws SQLException {
        if (prepared) {
            return;
        }
        try (Connection connection =
                     DriverManager.getConnection(settings.url(), settings.username(), settings.password())) {
            TestDatabaseInitializer.reset(connection);
        }
        prepared = true;
    }
}
