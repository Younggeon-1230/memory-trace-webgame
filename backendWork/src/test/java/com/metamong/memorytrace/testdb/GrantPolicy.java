package com.metamong.memorytrace.testdb;

import java.util.regex.Pattern;

/**
 * 테스트 계정의 SHOW GRANTS 한 줄이 허용 범위인지 판단한다. 허용 목록 방식이라 모르는 형태는 모두 실패한다
 * (전역 권한, 동적 권한, 역할 부여, PROXY, 테이블·컬럼 권한, 부분 취소 REVOKE 등).
 */
final class GrantPolicy {

    /** 줄이 계정 이름으로 끝나야 하므로 뒤에 WITH ... 절이 붙으면 맞지 않는다. */
    private static final String ACCOUNT_AT_END = " TO `[^`]*`@`[^`]*`$";

    private static final Pattern USAGE_ONLY = Pattern.compile("^GRANT USAGE ON \\*\\.\\*" + ACCOUNT_AT_END);

    private static final String PRIVILEGE = "[A-Z]+(?: [A-Z]+)*";

    /** DB 이름의 _ 가 \_ 로 이스케이프된, 정확히 테스트 DB 전체에 대한 권한 */
    private static final Pattern TEST_DATABASE_ONLY = Pattern.compile(
            "^GRANT " + PRIVILEGE + "(?:, " + PRIVILEGE + ")*"
                    + " ON `metamong\\\\_case\\\\_test`\\.\\*" + ACCOUNT_AT_END);

    private GrantPolicy() {
    }

    static boolean isAllowed(String grant) {
        if (grant.contains("WITH GRANT OPTION") || grant.contains("WITH ADMIN OPTION")
                || grant.startsWith("GRANT PROXY ")) {
            return false;
        }
        return USAGE_ONLY.matcher(grant).matches() || isTestDatabaseGrant(grant);
    }

    static boolean isTestDatabaseGrant(String grant) {
        return TEST_DATABASE_ONLY.matcher(grant).matches();
    }
}
