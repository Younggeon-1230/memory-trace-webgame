package com.metamong.memorytrace.testdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docs의 SQL 파일을 테스트 DB에서 실행하기 전에 <b>전체를 먼저</b> 검사한다.
 * 실행을 허용하는 것은 정해진 테이블의 CREATE TABLE, INSERT INTO 뿐이다.
 * 원본의 CREATE DATABASE, USE 는 알아보고 실행하지 않는다. 그 밖의 문장이 하나라도 있으면 전체를 거부한다.
 *
 * <p>MySQL의 파서를 그대로 옮긴 것이 아니므로 <b>보조 방어</b>다. 보안 경계는 두 가지다:
 * 연결한 DB가 정확히 테스트 DB인지의 확인과, 테스트 DB 밖의 권한이 없는 전용 계정.
 */
final class SqlScript {

    /** FK 때문에 만드는 순서대로 적는다. 삭제는 이 목록의 역순이다. */
    static final List<String> TABLES = List.of(
            "users", "pokemon", "pokemon_trait", "case_file", "case_suspect", "evidence_rule",
            "clue", "clue_prerequisite", "play_session", "session_clue", "session_suspect", "session_action");

    private static final Pattern SKIPPED = Pattern.compile(
            "^(CREATE\\s+DATABASE\\b|USE\\s+\\w+\\s*$).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 테이블 이름 바로 뒤에는 공백이나 여는 괄호만 올 수 있다. 따라서 db.table 형태는 맞지 않는다. */
    private static final Pattern ALLOWED = Pattern.compile(
            "^(?:CREATE\\s+TABLE|INSERT\\s+INTO)\\s+([A-Za-z_]\\w*)(?=\\s|\\().*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 문자열 밖의 "이름.이름". 0.514 같은 숫자는 숫자로 시작하므로 해당하지 않는다. */
    private static final Pattern QUALIFIED_NAME = Pattern.compile("(?<![\\w])[A-Za-z_]\\w*\\s*\\.\\s*[A-Za-z_]");

    /** 다른 곳을 읽거나 한 문장으로 여러 일을 하게 만드는 단어 */
    private static final Pattern FORBIDDEN_WORD = Pattern.compile(
            "\\b(SELECT|DROP|ALTER|TRUNCATE|DELETE|GRANT|RENAME|LOAD_FILE)\\b", Pattern.CASE_INSENSITIVE);

    private SqlScript() {
    }

    /** 실행할 문장 목록을 돌려준다. 하나라도 허용되지 않으면 예외를 던지고 아무것도 돌려주지 않는다. */
    static List<String> parse(String sql) {
        List<String> executable = new ArrayList<>();
        for (Statement statement : split(sql)) {
            String text = statement.text();
            String bare = statement.withoutLiterals();
            if (SKIPPED.matcher(bare).matches()) {
                continue;
            }
            Matcher allowed = ALLOWED.matcher(bare);
            if (!allowed.matches()
                    || !TABLES.contains(allowed.group(1).toLowerCase(Locale.ROOT))
                    || bare.indexOf('`') >= 0
                    || bare.indexOf('"') >= 0
                    || QUALIFIED_NAME.matcher(bare).find()
                    || FORBIDDEN_WORD.matcher(bare).find()) {
                throw new IllegalStateException("테스트 DB에서 실행할 수 없는 SQL 문장입니다: " + preview(text));
            }
            executable.add(text);
        }
        return List.copyOf(executable);
    }

    private static String preview(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    /** text는 실행할 원문(주석 제외), withoutLiterals는 작은따옴표 문자열을 ''로 바꾼 검사용 문장 */
    private record Statement(String text, String withoutLiterals) {
    }

    private static List<Statement> split(String sql) {
        List<Statement> statements = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        StringBuilder bare = new StringBuilder();
        int length = sql.length();
        int i = sql.startsWith("﻿") ? 1 : 0;

        while (i < length) {
            char c = sql.charAt(i);
            char next = i + 1 < length ? sql.charAt(i + 1) : '\0';
            if (c == '-' && next == '-' && i + 2 < length && isSpaceOrControl(sql.charAt(i + 2))) {
                // MySQL은 "--" 뒤에 공백이나 제어 문자가 있을 때만 주석으로 본다. "5--1"은 식이다
                int end = sql.indexOf('\n', i);
                i = end < 0 ? length : end;
            } else if (c == '#') {
                throw new IllegalStateException("'#' 주석은 지원하지 않습니다.");
            } else if (c == '/' && next == '*') {
                // /*! ... */ 는 MySQL이 실제로 실행하므로 주석으로 취급해 지나치지 않고 모든 블록 주석을 거부한다
                throw new IllegalStateException("블록 주석은 지원하지 않습니다.");
            } else if (c == '\'') {
                int end = endOfLiteral(sql, i);
                text.append(sql, i, end);
                bare.append("''");
                i = end;
            } else if (c == ';') {
                addIfNotBlank(statements, text, bare);
                i++;
            } else {
                text.append(c);
                bare.append(c);
                i++;
            }
        }
        addIfNotBlank(statements, text, bare);
        return statements;
    }

    private static boolean isSpaceOrControl(char c) {
        return c <= ' ' || c == 0x7f;
    }

    /**
     * 작은따옴표 문자열이 끝난 다음 위치. '' 와 \' 를 문자열 안의 따옴표로 본다.
     * 세션의 sql_mode에 NO_BACKSLASH_ESCAPES가 있으면 MySQL의 해석이 달라지므로 {@link TestDatabaseGuard}가 막는다.
     */
    private static int endOfLiteral(String sql, int start) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        throw new IllegalStateException("닫히지 않은 문자열이 있습니다.");
    }

    private static void addIfNotBlank(List<Statement> statements, StringBuilder text, StringBuilder bare) {
        if (!text.toString().isBlank()) {
            statements.add(new Statement(text.toString().strip(), bare.toString().strip()));
        }
        text.setLength(0);
        bare.setLength(0);
    }
}
