package com.metamong.memorytrace.testdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T26의 판단 규칙을 DB 없이 확인한다. 실제 MySQL 출력 형식은 통합 테스트가 확인한다. */
class GrantPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "GRANT USAGE ON *.* TO `metamong_test`@`localhost`",
            "GRANT ALL PRIVILEGES ON `metamong\\_case\\_test`.* TO `metamong_test`@`localhost`",
            "GRANT SELECT, INSERT, CREATE TEMPORARY TABLES ON `metamong\\_case\\_test`.* TO `metamong_test`@`localhost`",
    })
    void allows(String grant) {
        assertThat(GrantPolicy.isAllowed(grant)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // WITH 절
            "GRANT ALL PRIVILEGES ON `metamong\\_case\\_test`.* TO `metamong_test`@`localhost` WITH GRANT OPTION",
            "GRANT USAGE ON *.* TO `metamong_test`@`localhost` WITH GRANT OPTION",
            "GRANT `dev_role`@`%` TO `metamong_test`@`localhost` WITH ADMIN OPTION",
            // 역할, PROXY
            "GRANT `dev_role`@`%` TO `metamong_test`@`localhost`",
            "GRANT PROXY ON ``@`` TO `metamong_test`@`localhost`",
            // 전역·동적 권한
            "GRANT SELECT ON *.* TO `metamong_test`@`localhost`",
            "GRANT ALL PRIVILEGES ON *.* TO `metamong_test`@`localhost`",
            "GRANT BACKUP_ADMIN,SYSTEM_VARIABLES_ADMIN ON *.* TO `metamong_test`@`localhost`",
            // 다른 DB, 이스케이프하지 않은 와일드카드, 넓은 패턴, 테이블 단위
            "GRANT SELECT ON `metamong\\_case`.* TO `metamong_test`@`localhost`",
            "GRANT ALL PRIVILEGES ON `metamong_case_test`.* TO `metamong_test`@`localhost`",
            "GRANT ALL PRIVILEGES ON `metamong%`.* TO `metamong_test`@`localhost`",
            "GRANT SELECT ON `metamong_case`.`users` TO `metamong_test`@`localhost`",
            // 알 수 없는 형태
            "REVOKE INSERT ON `mysql`.* FROM `metamong_test`@`localhost`",
            "",
    })
    void rejects(String grant) {
        assertThat(GrantPolicy.isAllowed(grant)).isFalse();
    }
}
