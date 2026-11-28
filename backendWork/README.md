# Memory Trace 백엔드

Spring Boot 3.5.16 · Java 17(컴파일 대상) · Gradle 9.9.3(wrapper) · MySQL 8.0.16 이상
설계는 [`../docs/backend_design.md`](../docs/backend_design.md)를 따릅니다.

> 현재 구현: 11장 **1-1**(공통 오류 처리, Security 설정)까지. 업무 API는 아직 없습니다.

## 1. JDK

JDK 17 이상이 필요합니다. IntelliJ에서는 `backendWork` 폴더를 열고 Project SDK와 Gradle JVM을 지정합니다.
터미널에서 JDK를 따로 설치하지 않았다면 IntelliJ에 포함된 런타임을 쓸 수 있습니다(설치 경로는 본인 버전에 맞게).

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.3.0.1\jbr"
```

## 2. 개발 DB (`metamong_case`)

SQL 파일에 한글이 있으므로 UTF-8로 실행합니다. MySQL Workbench에서 `docs/01_schema.sql` → `docs/02_sample.sql` 순서로 실행하거나, 저장소 최상위에서 다음처럼 실행합니다.
(PowerShell에서 파일을 파이프로 넘기면 한글이 깨질 수 있으므로 `source`를 씁니다.)

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --default-character-set=utf8mb4 -u root -p
```

```sql
source docs/01_schema.sql
source docs/02_sample.sql
```

**2026-11-28 이전의 SQL로 이미 만든 DB가 있다면** 다시 만들거나, 데이터를 유지한 채 다음을 실행합니다.

```sql
USE metamong_case;
ALTER TABLE case_file
    ADD COLUMN validation_status VARCHAR(10) NOT NULL DEFAULT 'PENDING' AFTER is_enabled,
    ADD CONSTRAINT ck_case_validation CHECK (validation_status IN ('PENDING', 'VALID', 'INVALID'));
UPDATE case_file SET subtitle = '초자연 현상의 정체를 밝혀라' WHERE case_code = 'CASE01';
UPDATE case_file SET subtitle = '화재의 시작점을 추적하라'     WHERE case_code = 'CASE02';
UPDATE case_file SET subtitle = '남겨진 발자국의 모순을 풀어라' WHERE case_code = 'CASE03';
```

접속 정보는 `src/main/resources/application-local.yml.example`을 같은 폴더의 `application-local.yml`로 복사해 채웁니다.
이 파일은 Git에서 제외되며 `bootJar` 산출물에도 들어가지 않습니다.

## 3. 테스트 DB (`metamong_case_test`)

통합 테스트는 **테이블을 지우고 다시 만듭니다.** 전용 DB와 전용 계정을 씁니다.

1. MySQL 관리자 계정으로 [`db/setup_test_db.sql`](db/setup_test_db.sql)을 한 번 실행합니다. `CHANGE_ME`는 본인이 정한 비밀번호로 바꾸고, 바꾼 내용은 저장하거나 커밋하지 않습니다.
2. 환경 변수를 설정합니다. IntelliJ에서는 Gradle 실행 구성의 환경 변수에 넣습니다.

```powershell
$env:TEST_DB_URL = "jdbc:mysql://localhost:3306/metamong_case_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Seoul"
$env:TEST_DB_USERNAME = "metamong_test"
$env:TEST_DB_PASSWORD = "1번에서 정한 비밀번호"
```

`TEST_DB_URL`에는 `allowMultiQueries`를 넣을 수 없습니다(연결 전에 거절). 비밀번호는 URL에 넣지 말고 `TEST_DB_PASSWORD`에만 넣습니다. 비밀번호는 코드·문서·채팅에 남기지 마세요.

안전장치: 보안 경계는 두 가지입니다. ① 전용 계정에는 테스트 DB 밖의 권한이 없습니다(`integrationTest`가 `SHOW GRANTS`로 확인). ② 초기화 도구는 삭제 전에, 실제 연결의 `SELECT DATABASE()`와 catalog가 정확히 `metamong_case_test`인지 확인합니다.
보조 방어로 `sql_mode`에 `NO_BACKSLASH_ESCAPES`가 있으면 중단합니다.
SQL 파일은 실행 전에 전체를 검사하며, 정해진 테이블의 `CREATE TABLE`·`INSERT INTO` 외의 문장이 있으면 아무것도 실행하지 않습니다.
원본의 `CREATE DATABASE`와 `USE`는 실행하지 않습니다.

## 4. 명령

`backendWork`에서 실행합니다.

| 명령 | 내용 | MySQL |
|---|---|---|
| `.\gradlew.bat test` | 단위 테스트 | 필요 없음 |
| `.\gradlew.bat build` | 컴파일, 단위 테스트, jar, 비밀 파일 검사 | 필요 없음 |
| `.\gradlew.bat integrationTest` | 통합 테스트 | 3번 준비 필요. 설정이 없으면 실패 |
| `.\gradlew.bat bootRun` | 서버 실행(8080) | 2번 준비 필요 |

`test`와 `build`는 통합 테스트를 실행하지 않습니다. 각 구현 단계를 마칠 때와 배포 전에는 `test`와 `integrationTest`를 **둘 다** 실행합니다.
