package com.metamong.memorytrace.testdb;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 통합 테스트 DB 접속 정보. 환경 변수에서만 읽고, 개발 DB 설정(application-local.yml)은 쓰지 않는다. */
record TestDatabaseSettings(String url, String username, String password) {

    /** 어떤 경로로 만들든 연결하기 전에 URL을 검사한다. */
    TestDatabaseSettings {
        rejectMultiQueries(url);
    }

    /**
     * 한 번의 실행으로 여러 문장이 처리되면 SQL 검사기의 해석이 틀렸을 때 숨은 문장이 실행될 수 있다.
     * 표기 방식(대소문자, URL 인코딩, 속성 위치)에 기대지 않도록 값과 관계없이 이름이 있으면 거절한다.
     * 오류 메시지에는 URL을 넣지 않는다(URL에 비밀번호가 들어 있을 수 있다).
     */
    private static void rejectMultiQueries(String url) {
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (decoded.contains("allowmultiqueries")) {
            throw new IllegalStateException(
                    "TEST_DB_URL에 allowMultiQueries 설정을 넣을 수 없습니다. 연결하지 않고 중단합니다.");
        }
    }

    static TestDatabaseSettings fromEnvironment() {
        List<String> missing = new ArrayList<>();
        String url = require("TEST_DB_URL", missing);
        String username = require("TEST_DB_USERNAME", missing);
        String password = require("TEST_DB_PASSWORD", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "통합 테스트에 필요한 환경 변수가 없습니다: " + String.join(", ", missing)
                            + ". 준비 방법은 backendWork/README.md를 보세요. DB 없이 실행하려면 gradlew test를 쓰세요.");
        }
        return new TestDatabaseSettings(url, username, password);
    }

    private static String require(String name, List<String> missing) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
        return value;
    }

    @Override
    public String toString() {
        // 비밀번호는 출력하지 않는다. URL의 ? 뒤에도 비밀번호가 올 수 있어 잘라 낸다
        int query = url.indexOf('?');
        return "TestDatabaseSettings[url=" + (query < 0 ? url : url.substring(0, query)) + ", username=" + username + "]";
    }
}
