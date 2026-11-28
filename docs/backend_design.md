# 백엔드 설계서 — Memory Trace

> 작성 2026-11-28, 검토 의견과 결정 사항 반영(v3).
> 구현은 11장 순서로 진행합니다. 남은 미결정 항목은 12.1에 있습니다.
> 기준: `docs/project_context.md`, `docs/api_spec.md`(v0.2), `docs/01_schema.sql`, `docs/02_sample.sql`
> 계약: `frontendWork/src/api/types.ts`, `http.ts`, `mock/server.ts`. **목표는 `VITE_USE_MOCK=false`로 바꾸기만 하면 프론트가 동작하는 것**입니다.
> 본문의 `[D번호]`는 [12장](#12-결정-항목)을 가리킵니다. 12.1은 **미결정**, 12.2는 **확정**입니다. 미결정 부분의 본문 서술은 "가정"일 뿐 확정이 아닙니다.

---

## 0. 프로젝트 생성 방법

저장소 최상위의 `backendWork/` 바로 안에 `build.gradle`이 오게 만듭니다.

**버전 확정 `[D1]`: Spring Boot 3.5.16 고정.** [3.5.16 공식 발표](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)(2026-06-25)에 따르면 이 버전은 3.5 계열의 **마지막 OSS 릴리스**입니다. 일정과 설계 호환성을 위해 3.5.16을 쓰고 **OSS 지원 종료(EOL)를 감수합니다.** 이후 발견되는 결함·보안 문제의 OSS 패치는 없으므로, 장기 운영으로 바꾸면 4.x 전환을 먼저 검토합니다.

2026-11-28 확인: start.spring.io는 4.0 이상만 제공하고, Maven Central에는 3.5.16이 있습니다. 그래서 `build.gradle`을 직접 작성합니다.

**start.spring.io 또는 IntelliJ(New Project → Spring Boot) 공통 설정**

| 항목 | 값 |
|---|---|
| Project / Language | Gradle - Groovy / Java |
| Spring Boot | **3.5.16** (범위 지정 없이 고정) |
| Group / Artifact | `com.metamong` / `memorytrace` |
| Package name | `com.metamong.memorytrace` |
| Packaging / Java | Jar / 17 |
| Dependencies | Spring Web, Spring Data JPA, Spring Security, Validation, MySQL Driver, Lombok |
| 위치 | `memory-trace-webgame\backendWork` |

- 생성기에 3.x가 없으므로 `build.gradle`, `settings.gradle`, Gradle wrapper를 직접 구성합니다. 의존성은 1장 표를 따릅니다.
- IntelliJ에서는 `backendWork` 폴더를 열고, Project SDK와 Gradle JVM을 17 이상으로 맞춥니다.
- 이 PC의 셸에서는 `java`, `mysql` 명령이 PATH에 없었습니다. 설치 여부(JDK 17+, MySQL 8.0.16+)를 먼저 확인하세요.
- SQL은 `01_schema.sql` → `02_sample.sql` 순서로 **UTF-8(utf8mb4)** 로 실행합니다(Workbench 또는 IntelliJ Database 창).

---

## 1. 기술 구성

Java 17(컴파일 대상. 더 높은 JDK에서는 `release 17`로 빌드), Spring Boot 3.5.16, Gradle, MySQL 8.0.16 이상.

| 의존성 | 범위 | 쓰는 이유 |
|---|---|---|
| `spring-boot-starter-web` | implementation | REST 컨트롤러, Jackson JSON, 내장 Tomcat, 배포 시 정적 파일 제공 |
| `spring-boot-starter-data-jpa` | implementation | 엔티티·리포지토리, 트랜잭션, 비관적 잠금(`FOR UPDATE`) |
| `spring-boot-starter-security` | implementation | 세션 인증, BCrypt, 401 처리, 세션 고정 방어 |
| `spring-boot-starter-validation` | implementation | 요청 DTO 입력 검증 → `VALIDATION_ERROR` |
| `mysql-connector-j` | runtimeOnly | MySQL JDBC 드라이버 |
| `lombok` | compileOnly + annotationProcessor (테스트 범위 포함) | 엔티티 getter·기본 생성자 반복 제거. DTO는 Java `record`를 써서 Lombok이 필요 없음 |
| `spring-boot-starter-test` | testImplementation | JUnit 5, AssertJ, Mockito, MockMvc |
| `spring-security-test` | testImplementation | 로그인 사용자를 가정한 MockMvc 요청 |
| `junit-platform-launcher` | testRuntimeOnly | Gradle에서 JUnit 5 실행 |

Lombok 규칙: 엔티티에는 `@Getter`와 protected 기본 생성자만 씁니다. `@Data`, `@Setter`, 연관 필드를 포함한 `toString`/`equals`는 쓰지 않습니다(LAZY 로딩 오류 방지). 상태 변경은 의미 있는 메서드로 합니다.

### 1.1 설정 파일 분리

| 파일 | Git | 내용 |
|---|---|---|
| `src/main/resources/application.yml` | 커밋 | 비밀이 아닌 공통 설정 |
| `src/main/resources/application-local.yml` | 제외 (`.gitignore`에 이미 있음) | DB URL·계정·비밀번호 |
| `src/main/resources/application-local.yml.example` | 커밋 | 키 이름과 가짜 값만 |

`application.yml`의 주요 설정

| 키 | 값 | 이유 |
|---|---|---|
| `spring.config.import` | `optional:classpath:application-local.yml` | 파일이 없는 환경(배포)에서도 실행 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 테이블은 SQL 파일로만 만듦 |
| `spring.jpa.open-in-view` | `false` | 지연 로딩 범위를 서비스 트랜잭션으로 한정. 중복 키 예외 뒤 오염된 영속성 컨텍스트 재사용 방지 |
| `spring.sql.init.mode` | `never` | 서버가 SQL을 자동 실행하지 않음 |
| `spring.jackson.serialization.write-dates-as-timestamps` | `false` | 날짜를 문자열로 |
| `server.servlet.session.cookie.http-only` / `same-site` | `true` / `lax` | 쿠키 보호 (5장) |
| `server.servlet.session.timeout` | `2h` | 시연 중 로그아웃 방지 |

- `application-local.yml`: `spring.datasource.url`(`jdbc:mysql://localhost:3306/metamong_case`, `connectionTimeZone=Asia/Seoul`, `characterEncoding=UTF-8`), `username`, `password`.
- 배포에서는 `SPRING_DATASOURCE_URL` 등 환경 변수로 주입합니다.
- **최종 산출물에서 `application-local.yml`을 제외**합니다(10.2). gitignore는 Git만 막고, `resources` 안의 파일은 jar에 그대로 들어갑니다. `processResources`에서 빼면 개발용 `bootRun`과 IDE 실행이 설정을 못 읽으므로 그렇게 하지 않습니다.
- 시각: 모든 시각은 Java에서 `Clock` 빈(Asia/Seoul)으로 만들고 **초 단위로 자릅니다**. 응답 형식은 `yyyy-MM-dd'T'HH:mm:ss`(타임존·소수점 없음)로 mock과 같게 합니다. `DATETIME`에 소수 초를 넘기면 MySQL이 반올림하므로 먼저 자릅니다.

---

## 2. 패키지 구조

`case`는 Java 예약어라 패키지 이름으로 `cases`를 씁니다.

```
com.metamong.memorytrace
├─ MemorytraceApplication
├─ common    config · security · error · time
├─ auth      회원
├─ pokemon   포켓몬 캐시 (엔티티·조회만, API 없음)
├─ cases     사건 정의 + 목록 API + 검증기
├─ judge     판정기 7종 (스프링·JPA에 의존하지 않는 순수 로직)
├─ game      세션·게임 행동·결과
└─ record    마이페이지
```

각 기능 패키지 안은 `controller / service / repository / dto / entity` 하위 패키지로 나눕니다.

| 패키지 | 클래스 |
|---|---|
| `common` | `SecurityConfig`, `OriginCheckFilter`(prod 전용), `OriginProperties`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `ClockConfig`, `JacksonConfig`(날짜 형식), `SpaWebConfig`(10장), `ErrorCode`, `ApiException`와 하위 예외(8장), `GlobalExceptionHandler`, `ErrorResponse`, `PageResponse` |
| `auth` | `AuthController`, `AuthService`, `UserRepository`, `User`, `Role`, `UserPrincipal`, `CustomUserDetailsService`, `SignupRequest`, `LoginRequest`, `UserResponse` |
| `pokemon` | `Pokemon`, `PokemonTrait`, `TraitSourceType`, `PokemonRepository`, `PokemonTraitRepository` |
| `cases` | `CaseController`, `CaseQueryService`, `CaseSummaryResponse`, `CaseFile`, `ValidationStatus`, `CaseSuspect`, `EvidenceRule`, `Clue`, `CluePrerequisite`, `ClueKind`, `RuleSourceType`, 각 Repository, `validation/`: `CaseValidationGate`, `CaseValidationService`, `CaseValidationResultWriter`, `CaseValidator`, `CaseDefinition`(검증 입력 값 객체), `CaseDefinitionLoader`, `IntegrityChecks`, `LogicChecks`, `ValidationViolation` |
| `judge` | `ConditionType`, `ExclusionJudge`, `HasTypeJudge`, `NotTypeJudge`, `MaxHeightJudge`, `MaxWeightJudge`, `ShapeIsJudge`, `ColorIsJudge`, `NotTraitJudge`, `JudgeRegistry`, `PokemonProfile` |
| `game` | `SessionController`, `GameActionController`, `SessionService`, `SessionStartFacade`, `GameActionFacade`, `InvestigationService`, `ExclusionService`, `AccusationService`, `ActionGuard`(공통 검사), `ActionOutcome`(처리 완료 / 이미 처리됨), `ActionReplayService`(별도 빈), `InferenceService`, `ScorePolicy`, `ActionMessages`, `GameStateAssembler`, `ResultService`, `PlaySession`, `SessionClue`, `SessionSuspect`, `SessionAction`, Enum(`SessionStatus`, `Grade`, `SuspectStatus`, `ActionType`, `ActionResult`), 각 Repository, DTO(4장) |
| `record` | `MyRecordController`, `MyRecordService`, `MyRecordQueryRepository`, `SessionRecordResponse`, `CaseRecordSummaryResponse`, `ActionLogResponse` |

### 역할 경계

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| Controller | URL 매핑, `@Valid`, 로그인 사용자 ID 추출, HTTP 상태 코드 | 비즈니스 판단, 리포지토리 호출, 트랜잭션, 엔티티 반환 |
| Facade | 트랜잭션 **바깥**에서 서비스 호출, 중복 키 예외 후 새 트랜잭션으로 재조회 | `@Transactional` 선언 |
| Service | 트랜잭션 경계, 권한·상태 검사, 규칙 적용, DTO 조립 | HTTP 객체 다루기 |
| 도메인(Entity·judge·ScorePolicy) | 상태 전이(`exclude`, `finish` 등), 판정, 점수·등급 계산 | 리포지토리 호출 |
| Repository | 조회·저장, 잠금 쿼리, fetch join, 집계 | 비즈니스 판단 |
| DTO(record) | API 모양 그대로. dm·hg → m·kg 변환 결과를 담음 | 엔티티를 필드로 갖기 |
| Entity | 스키마 매핑 + 상태 변경 메서드 | 응답으로 직렬화되기 |

**엔티티는 어떤 경우에도 JSON으로 직접 내보내지 않습니다.** `CaseFile`·`Clue`에는 정답과 판정 정보가 들어 있습니다.

---

## 3. 엔티티 매핑

### 3.1 공통 매핑 방식

| DB | Java | 방식 / 이유 |
|---|---|---|
| `BIGINT AUTO_INCREMENT` | `Long` | `GenerationType.IDENTITY` |
| `INT` | `int` / `Integer` | NULL 가능하면 래퍼 |
| `pokemon.pokemon_id` | `Integer` | 생성 전략 없음(도감 번호) |
| `VARCHAR(n)` | `String` | `length = n` 명시 |
| `TEXT` | `String` | `columnDefinition = "text"`. 생략하면 `varchar(255)`를 기대해 validate 실패 위험 |
| `BOOLEAN` (실제 `TINYINT(1)`) | `boolean` | 기본 매핑 사용. 필드 이름은 `enabled`, `core`, `judgment`로 하고 `@Column(name="is_...")` 지정 |
| `DECIMAL(5,4)` | `BigDecimal` | `precision = 5, scale = 4`. NULL 가능. DTO에서 `double`로 변환 |
| `CHAR(36)` | `String` | `@JdbcTypeCode(SqlTypes.CHAR)`, `length = 36`. **`UUID` 타입은 쓰지 않음**(바이너리로 매핑돼 어긋남) |
| `TINYINT` (`active_flag`) | `Byte` | 진행 중 `1`, 종료 `null`. `Integer`는 validate 실패. `boolean`은 `0`이 저장될 수 있어 UNIQUE 설계가 깨짐 |
| `DATETIME` | `LocalDateTime` | DB 기본값에 맡기지 않고 Java에서 채움 |
| CHECK 값 목록 | Java `enum` | `@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`. Hibernate 6은 MySQL에서 네이티브 `ENUM`을 기대할 수 있어 VARCHAR로 고정 |

**CHECK 값 목록 → Enum** (상수 이름을 DB 문자열과 글자까지 같게)

| Enum | 값 | 컬럼 |
|---|---|---|
| `Role` | USER, ADMIN | `users.role` |
| `TraitSourceType` | POKEDEX, CASE_RULE | `pokemon_trait.source_type` |
| `ConditionType` | HAS_TYPE, NOT_TYPE, MAX_HEIGHT, MAX_WEIGHT, SHAPE_IS, COLOR_IS, NOT_TRAIT | `evidence_rule.condition_type` |
| `RuleSourceType` | API, POKEDEX, CASE_RULE | `evidence_rule.source_type` |
| `ValidationStatus` | PENDING, VALID, INVALID | `case_file.validation_status` |
| `ClueKind` | SCENE, INFERENCE | `clue.clue_kind` |
| `SessionStatus` | IN_PROGRESS, SOLVED, FAILED | `play_session.status` |
| `Grade` | S, A, B | `play_session.grade` (NULL 가능) |
| `SuspectStatus` | ACTIVE, EXCLUDED_BY_EVIDENCE, ELIMINATED_BY_WRONG_ACCUSATION | `session_suspect.status` (길이 30) |
| `ActionType` | INVESTIGATE, INFER, EXCLUDE, ACCUSE | `session_action.action_type` |
| `ActionResult` | SUCCESS, FAIL, REJECTED | `session_action.result` |

### 3.2 테이블별 매핑표

표기: `컬럼 → 필드: 타입`. `?`는 NULL 가능. 연관은 모두 `@ManyToOne(fetch = LAZY)` **단방향**입니다.

**1. `users` → `User`**
`user_id→id: Long` · `login_id→loginId: String(30)` · `password_hash→passwordHash: String(100)` · `nickname→nickname: String(20)` · `role→role: Role(10)` · `created_at→createdAt: LocalDateTime`
UNIQUE `uk_users_login_id(login_id)`

**2. `pokemon` → `Pokemon`**
`pokemon_id→id: Integer(직접 지정)` · `name_en→nameEn(30)` · `name_ko→nameKo(30)` · `height_dm→heightDm: int` · `weight_hg→weightHg: int` · `type1→type1(20)` · `type2→type2?(20)` · `color→color(20)` · `shape→shape(20)` · `sprite_path→spritePath(255)` · `synced_at→syncedAt: LocalDateTime`
UNIQUE `uk_pokemon_name_en(name_en)`

**3. `pokemon_trait` → `PokemonTrait`**
`trait_id→id: Long` · `pokemon_id→pokemon: Pokemon` · `trait_code→traitCode(40)` · `description(255)` · `source_type→sourceType: TraitSourceType` · `source_text→sourceText?: TEXT` · `source_version→sourceVersion?(30)` · `is_judgment→judgment: boolean`
UNIQUE `uk_trait_pokemon_code(pokemon_id, trait_code)`
※ 샘플은 `trait_id`를 직접 넣지만 AUTO_INCREMENT이므로 `IDENTITY`로 매핑합니다.

**4. `case_file` → `CaseFile`**
`case_id→id: Long` · `case_code→caseCode(10)` · `title(50)` · `subtitle?(100)` · `briefing: TEXT` · `scene_image_path→sceneImagePath(255)` · `answer_pokemon_id→answerPokemon: Pokemon` · `truth_text→truthText: TEXT` · `motive_text→motiveText: TEXT` · `twist_text→twistText: TEXT` · `display_order→displayOrder: int` · `is_enabled→enabled: boolean` · `validation_status→validationStatus: ValidationStatus(10)` · `validated_at→validatedAt?: LocalDateTime`
UNIQUE `uk_case_code(case_code)`
※ `enabled`는 엔티티에서 바꾸는 메서드를 두지 않습니다. 검증 결과는 `validation_status`, `validated_at` **두 컬럼만 갱신하는 쿼리**로 씁니다. 엔티티를 수정해 저장하면 Hibernate가 모든 컬럼을 UPDATE해, 그 사이 사람이 바꾼 `is_enabled`를 덮어쓸 수 있습니다.

**5. `case_suspect` → `CaseSuspect`**
`case_suspect_id→id: Long` · `case_id→caseFile: CaseFile` · `pokemon_id→pokemon: Pokemon` · `suspicion_text→suspicionText?(255)` · `display_order→displayOrder: int`
UNIQUE `uk_suspect_case_pokemon(case_id, pokemon_id)`

**6. `evidence_rule` → `EvidenceRule`**
`rule_id→id: Long` · `condition_type→conditionType: ConditionType(20)` · `condition_value→conditionValue(40)` · `basis_text→basisText(255)` · `source_type→sourceType: RuleSourceType(20)` · `source_ref→sourceRef?(100)`

**7. `clue` → `Clue`**
`clue_id→id: Long` · `case_id→caseFile: CaseFile` · `clue_code→clueCode(10)` · `clue_kind→kind: ClueKind(10)` · `name(50)` · `summary(100)` · `detail_text→detailText: TEXT` · `rebuttal_text→rebuttalText?(255)` · `rule_id→rule?: EvidenceRule` · `is_core→core: boolean` · `area_x|y|w|h→areaX|areaY|areaW|areaH?: BigDecimal(5,4)` · `display_order→displayOrder: int`
UNIQUE `uk_clue_case_code(case_id, clue_code)`

**8. `clue_prerequisite` → `CluePrerequisite`**
`prerequisite_id→id: Long` · `clue_id→clue: Clue` · `required_clue_id→requiredClue: Clue`
UNIQUE `uk_prereq_pair(clue_id, required_clue_id)`

**9. `play_session` → `PlaySession`**
`session_id→id: Long` · `user_id→user: User` · `case_id→caseFile: CaseFile` · `status: SessionStatus(12)` · `score: int` · `wrong_exclusion_count→wrongExclusionCount: int` · `accusation_count→accusationCount: int` · `bonus_awarded→bonusAwarded: boolean` · `grade?: Grade(2)` · `active_flag→activeFlag?: Byte` · `started_at→startedAt` · `ended_at→endedAt?`
UNIQUE `uk_session_active(user_id, case_id, active_flag)`
상태 변경: `start()`는 score 100·IN_PROGRESS·activeFlag 1. `finish()`는 **status, activeFlag=null, endedAt, grade를 한 번에** 바꿉니다(`ck_session_active`).

**10. `session_clue` → `SessionClue`**
`session_clue_id→id` · `session_id→session: PlaySession` · `clue_id→clue: Clue` · `collected_at→collectedAt`
UNIQUE `uk_session_clue(session_id, clue_id)`

**11. `session_suspect` → `SessionSuspect`**
`session_suspect_id→id` · `session_id→session` · `case_suspect_id→caseSuspect` · `status: SuspectStatus(30)` · `basis_clue_id→basisClue?: Clue` · `updated_at→updatedAt`
UNIQUE `uk_session_suspect(session_id, case_suspect_id)`
상태 변경: `excludeByEvidence(clue)`, `eliminateByWrongAccusation()`만 허용해 status와 basisClue를 함께 바꿉니다(`ck_ssuspect_basis`).

**12. `session_action` → `SessionAction`** (insert 전용, 수정 메서드 없음)
`action_id→id: Long` · `session_id→session` · `action_type→actionType: ActionType(12)` · `clue_id→clue?` · `case_suspect_id→caseSuspect?` · `result: ActionResult(10)` · `score_delta→scoreDelta: int` · `dedup_key→dedupKey?(60)` · `request_id→requestId?: CHAR(36)` · `created_at→createdAt`
UNIQUE `uk_action_request(request_id)`, `uk_action_dedup(session_id, dedup_key)`
생성 메서드를 `accepted(…)`, `rejected(…)`, `inferred(…)`로 나눠 `ck_action_rejected`, `ck_action_infer`를 구조적으로 지킵니다.

### 3.3 연관관계와 UNIQUE 표현

- **양방향 연관을 만들지 않습니다.** 자식 목록은 `findBy…CaseFileId` 같은 쿼리로 가져옵니다. 순환 직렬화와 의도치 않은 지연 로딩이 없고, `case_file ↔ case_suspect` 순환도 생기지 않습니다.
- cascade와 orphanRemoval은 쓰지 않고 각 리포지토리로 저장합니다.
- `open-in-view=false`이므로 응답 조립에 필요한 연관(suspect→pokemon, clue→rule, sessionClue→clue)은 fetch join 또는 `@EntityGraph`로 읽습니다.
- 복합 UNIQUE는 `@Table(uniqueConstraints = …)`에 **SQL과 같은 이름**으로 적습니다. 이는 문서 역할이고 실제 보장은 SQL이 합니다.

### 3.4 스키마–엔티티 불일치 위험 ⚠️

| # | 위험 | 대응 |
|---|---|---|
| R1 | ⚠️ **`areaX` 자동 컬럼명**: 컬럼명을 지정하지 않으면 기본 네이밍 전략이 `areax`를 만듦(2026-11-28 `SchemaMappingTest`에서 확인) | `area_x/y/w/h`를 `@Column(name)`으로 명시. 원칙적으로 모든 컬럼명 명시 |
| R2 | ⚠️ `active_flag`를 `Integer`/`Boolean`으로 매핑 | `Byte` |
| R3 | ⚠️ `TEXT` 4종 테이블(`pokemon_trait`, `case_file`, `clue`)의 타입 불일치 | `columnDefinition="text"` |
| R4 | ⚠️ Enum의 네이티브 ENUM 기대 | VARCHAR 고정 |
| R5 | ⚠️ `request_id` CHAR vs VARCHAR | `SqlTypes.CHAR` |
| R6 | `BOOLEAN`=`TINYINT(1)`: JDBC 설정(`tinyInt1isBit`)이나 DB(MariaDB)에 따라 validate 결과가 달라질 수 있음 | URL에서 그 옵션을 바꾸지 않고, 실제 MySQL에서 부팅 확인 |
| R7 | ⚠️ **`DEFAULT` 컬럼에 `null` insert**: Hibernate는 null을 그대로 보내 DB 기본값이 적용되지 않음 | `created_at`, `started_at`, `collected_at`, `updated_at`, `status`, `score` 등을 Java에서 반드시 채움 |
| R8 | ⚠️ **`validate`가 검사하지 않는 것**: UNIQUE, CHECK, FK, NOT NULL, 기본값, 길이 | `SchemaMappingTest`(**보조:** DB 없이 컬럼명·타입·길이·NULL 허용·UNIQUE를 `01_schema.sql`과 비교. 네이밍 전략을 테스트에서 직접 지정하고 CHECK·FK·기본값은 보지 않음). **최종 판단은 실제 MySQL의 `validate` 부팅과 `EntityMappingIntegrationTest`** |
| R9 | `subtitle`, `suspicion_text`는 DB에서 NULL 가능, `types.ts`는 `string` | DTO에서 null → `""` |
| R10 | `utf8mb4_unicode_ci`라 문자열 UNIQUE·비교가 대소문자를 구분하지 않음 | loginId는 소문자만 허용, requestId는 소문자로 정규화 |
| R11 | `case`는 Java 예약어 | 필드는 `caseFile`. 응답은 `@JsonProperty("case")` 지정 (4장) |

---

## 4. API별 처리 설계

### 4.0 공통

- `userId`는 항상 세션의 `UserPrincipal`에서 가져옵니다. 요청 본문·파라미터의 사용자 ID는 받지 않습니다.
- **JSON null 규칙(계약):** 전역 `non_null` 설정을 **쓰지 않습니다.** `inProgressSessionId`, `bestGrade`, `bestScore`, `grade`, `basisClueId`, `basisClueName`, `endedAt`, `lastPlayedAt`, `clueName`, `suspectName`은 값이 없으면 **`null`로 보냅니다**(`MyPage`가 `!== null`로 비교). `ActionResponse`의 `rebuttalText`, `newClueIds`, `inferredClueIds`, `bonusAwarded`만 해당 없으면 **필드를 생략**합니다.
- `requestId` 검증: `@NotBlank`, `@Pattern`(**UUID v4**: `xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx`, 대소문자 허용) 후 소문자로 정규화합니다. mock과 같은 규칙입니다. 본문의 ID는 `Long` + `@NotNull` + `@Positive`.
- 검증 메시지는 mock의 한국어 문구를 그대로 씁니다.
- 경로의 ID가 숫자가 아니면 400 `VALIDATION_ERROR`.

### 4.1 `POST /api/auth/signup` → 201

- 요청 `SignupRequest`: `loginId`(`@NotBlank`, `@Pattern ^[a-z0-9_]{4,30}$`), `password`(`@NotBlank`, `@Size 8~64`, UTF-8 72바이트 이하 커스텀 검증 `[D6]`)
  - 72바이트 초과 메시지: `비밀번호가 너무 깁니다. 한글·특수 문자는 글자당 여러 바이트를 차지합니다.`, `nickname`(`@NotBlank`, trim 후 `@Size 2~20`)
- 응답 `UserResponse`: `userId, loginId, nickname, role`
- 처리: 아이디 중복 확인 → BCrypt 해시 → 저장(role USER). 동시 가입으로 `uk_users_login_id` 위반이 나도 409 `LOGIN_ID_TAKEN`. 자동 로그인하지 않음.
- 금지: `password`, `passwordHash`

### 4.2 `POST /api/auth/login` → 200

- 요청 `LoginRequest`: `loginId`(`@NotBlank`, `@Size max 30`), `password`(`@NotBlank`, `@Size max 64`)
- 응답 `UserResponse` + 세션 쿠키. 실패 401 `UNAUTHORIZED` "아이디 또는 비밀번호가 맞지 않습니다." (5장)

### 4.3 `POST /api/auth/logout` → 204

- 본문 없음. 세션 무효화, SecurityContext 삭제, 쿠키 만료. 비로그인 401.

### 4.4 `GET /api/auth/me` → 200 `UserResponse` / 401

### 4.5 `GET /api/cases` → 200 `CaseSummaryResponse[]`

- 필드: `caseId, caseCode, title, subtitle, sceneImagePath, myStatus{ inProgressSessionId, playCount, bestGrade, bestScore }`
- **공개 중**(`enabled = true` **그리고** `validationStatus = VALID`)인 사건만, `displayOrder` 순 `[D26]`. 이 조건은 리포지토리 한 곳에 정의해 목록·새 수사 시작·요약이 같이 씁니다. 비공개 사건의 새 시작은 막습니다. 이미 만든 세션의 처리는 검증 상태에 따라 다릅니다(7.5) `[D17, D29]`.
- 계산(mock 기준): `playCount`는 **종료된** 세션 수. `bestScore`/`bestGrade`는 **SOLVED** 중 최고 점수 세션의 값(동점이면 먼저 만든 세션). S는 110점에서만 나오므로 "최고 점수의 등급"과 "최고 등급"은 일치합니다.
- 내 세션은 한 번에 읽어 사건별로 묶습니다(N+1 방지).
- **금지:** `answerPokemonId`, `briefing`, `truthText`, `motiveText`, `twistText`, `enabled`, `validationStatus`, `validatedAt`

### 4.6 `POST /api/cases/{caseId}/sessions` → 201(신규) / 200(이어하기) `GameStateResponse`

1. 사건이 없거나 공개 중이 아니면 404 (4.5의 조건)
2. 내 IN_PROGRESS 세션이 있으면 200
3. 없으면 한 트랜잭션에서 `play_session` + `session_suspect` 4행(ACTIVE) 생성 → 201
4. `SessionStartFacade`: `DataIntegrityViolationException`이 나면 생성 트랜잭션이 끝난 뒤 **별도 빈의 새 트랜잭션**에서 내 IN_PROGRESS 세션을 조회. 있으면 200, **없으면 원래 예외를 다시 던짐**. 예외 문자열의 제약 이름은 해석하지 않음 `[D25]`

### 4.7 `GET /api/sessions/{sessionId}` → 200 `GameStateResponse`

- 존재(404) → 소유자(403) 순서. 종료된 세션도 조회 가능. 읽기 전용 트랜잭션 하나에서 조립합니다.

**`GameStateResponse`** (`GameStateAssembler` 한 곳에서만 생성)

| 필드 | 내용 | 정렬 |
|---|---|---|
| `sessionId, status, score, wrongExclusionCount` | 세션 값 | |
| `accusationsLeft` | `2 - accusationCount` | |
| `case`: `CaseInfoDto{caseId, caseCode, title, briefing, sceneImagePath}` | Java 필드명은 `caseInfo`, **JSON 이름은 `case`** | |
| `sceneAreas[]`: `SceneAreaDto{clueId, x, y, w, h, collected}` | SCENE 단서 전부 | `displayOrder` |
| `clues[]`: `ClueDto{clueId, clueCode, kind, name, summary, detailText, collectedAt}` | **수집한 것만** | `displayOrder` (수집 시각 순 아님) |
| `traceDictionary[]`: `TraceEntryDto{basisText}` | 이 사건 단서에 연결된 규칙, 중복 제거(수집 여부 무관) | `ruleId` |
| `suspects[]`: `SuspectDto{caseSuspectId, pokemonId, nameKo, spritePath, heightM, weightKg, types[], color, shape, traits[], suspicionText, status, basisClueId}` | 4명 | `case_suspect.displayOrder` |
| `traits[]`: `TraitDto{traitCode, description}` | `judgment = true`만 | `traitId` |

- `heightM = heightDm ÷ 10`, `weightKg = weightHg ÷ 10`은 이 DTO를 만들 때만 계산합니다(`BigDecimal`). `types`는 `type2`가 null이면 1개.
- `spritePath`는 DB의 `sprite_path`를 그대로 보냅니다.

### 4.8 `POST /api/sessions/{sessionId}/investigations` → 200 `ActionResponse`

- 요청 `InvestigationRequest{requestId, clueId}`
- 응답 `ActionResponse{result, scoreDelta, message, rebuttalText?, newClueIds?, inferredClueIds?, bonusAwarded?, state}`
- SUCCESS: `newClueIds=[clueId]`, `inferredClueIds`(없으면 `[]`). REJECTED: 두 필드 생략.
- INFERENCE 단서 ID는 400 `TARGET_NOT_IN_CASE` "현장에서 조사할 수 있는 곳이 아닙니다." `[D9]`

- 4.8~4.10 공통: 사건의 검증 상태가 `VALID`가 아니면 409 `CASE_UNAVAILABLE` (7.5)

### 4.9 `POST /api/sessions/{sessionId}/exclusions` → 200 `ActionResponse`

- 요청 `ExclusionRequest{requestId, clueId, caseSuspectId}`
- `rebuttalText`는 **result가 FAIL이고 단서에 반박 문구가 있을 때만** 포함

### 4.10 `POST /api/sessions/{sessionId}/accusations` → 200 `ActionResponse`

- 요청 `AccusationRequest{requestId, caseSuspectId}`
- `bonusAwarded`는 **SUCCESS일 때만**(true/false 모두) 포함

**행동 메시지** (`ActionMessages`, mock 문구와 동일하게)

| 상황 | message |
|---|---|
| 조사 성공 / 추론 발생 | `새 단서를 수첩에 기록했습니다.` / `단서를 기록했습니다. 모은 단서에서 새로운 추론이 떠올랐습니다!` |
| 조사 거절 | `이미 조사한 곳입니다.` |
| 배제 거절 | `아직 수집하지 않은 단서입니다.` / `이미 용의선상에서 빠진 용의자입니다.` / `이미 제출한 추리입니다.` |
| 배제 성공 / 실패 | `{이름}은(는) 이 증거와 모순됩니다. 용의선상에서 배제했습니다.` / `이 증거로는 {이름}을(를) 배제할 수 없습니다.` |
| 지목 거절 | `용의선상에 남아 있는 용의자만 지목할 수 있습니다.` |
| 지목 정답 / 1차 오답 / 2차 오답 | `메타몽이 변신했던 모습은 {이름}였습니다!` / `{이름}은(는) 메타몽이 아니었습니다.` / `두 번째 지목도 빗나갔습니다. 수사는 실패로 끝났습니다.` |

### 4.11 `GET /api/sessions/{sessionId}/result` → 200 `SessionResultResponse`

- 존재 → 소유자 → IN_PROGRESS면 409 `SESSION_IN_PROGRESS` (`ResultPage`가 이 코드로 게임 화면에 돌려보냄)
- 필드: `sessionId, caseId, caseCode, title, status, score, grade, bonusAwarded, wrongExclusionCount, accusationCount, answer{pokemonId, nameKo, spritePath}, truthText, motiveText, twistText, exclusions[], startedAt, endedAt`
- `exclusions[]`: `ExclusionSummaryDto{caseSuspectId, nameKo, status, basisClueName}`. **정답을 뺀 3명**, 용의자 표시 순서. FAILED면 `ACTIVE`가 있을 수 있음
- 정답·진상이 나가는 **유일한 API**입니다. FAILED에도 공개 `[D5]`

### 4.12 `GET /api/me/sessions?caseId=&page=0&size=10` → 200 `PageResponse<SessionRecordResponse>`

- 검증: `caseId` 선택 `@Positive`, `page @Min(0)`, `size @Min(1) @Max(100)` `[D18]`
- `PageResponse{content, page, size, totalElements}` (Spring `Page`를 직접 직렬화하지 않음)
- `SessionRecordResponse{sessionId, caseCode, title, status, score, grade, wrongExclusionCount, collectedClueCount, totalSceneClueCount, startedAt, endedAt}`
- `sessionId` 내림차순. 두 단서 수는 **SCENE만** 셈. 페이지에 포함된 세션·사건 ID로 한 번에 집계

### 4.13 `GET /api/me/summary` → 200 `CaseRecordSummaryResponse[]`

- `{caseId, caseCode, title, playCount, solvedCount, bestGrade, bestScore, lastPlayedAt}`
- 공개 중인 사건 전부(기록이 없어도 포함. `MyPage`의 "해결한 사건 n / 전체"가 이 길이를 씀), 표시 순서. 모든 값은 종료 세션 기준, `lastPlayedAt`은 가장 늦은 `endedAt`. 비공개 사건은 내 기록이 있을 때만 포함 `[D17]`

### 4.14 `GET /api/me/sessions/{sessionId}/actions` → 200 `ActionLogResponse[]`

- 존재 → 소유자 → IN_PROGRESS면 409 `SESSION_IN_PROGRESS` `[D10]`
- `{actionType, clueName, suspectName, result, scoreDelta, createdAt}`, `actionId` 오름차순
- **금지:** `actionId`, `dedupKey`, `requestId`, `clueId`

### 4.15 응답 금지 필드 총점검

| 데이터 | 공개 시점 |
|---|---|
| `answer_pokemon_id`, `truth_text`, `motive_text`, `twist_text` | 종료 세션의 result API만 |
| `rule_id`, `condition_type`, `condition_value`, `source_type`, `source_ref` (evidence_rule) | 절대 안 보냄 |
| `is_core` | 절대 안 보냄 |
| 단서–규칙 연결 | 절대 안 보냄 (`traceDictionary`는 `basisText`만. 항목에 ID도 없음) |
| `rebuttal_text` | EXCLUDE가 FAIL인 `ActionResponse.rebuttalText`(재전송 포함)만. `ClueDto`에는 없음 |
| 미수집 단서의 이름·요약·내용 | GameState에 안 보냄 (`clueId`와 좌표만). REJECTED 메시지에도 이름을 넣지 않음 |
| 선행 조건(`clue_prerequisite`) | 절대 안 보냄 |
| `is_judgment=false` 특징, `source_text`, `source_version` | 절대 안 보냄 |
| 배제 정답표(검증기 계산 결과) | 로그에만 |
| `password_hash`, `request_id`, `dedup_key` | 절대 안 보냄 |

컨트롤러 통합 테스트에서 GameState JSON의 **키 집합이 정확히 일치하는지** 검사합니다(필드가 늘면 실패).

---

## 5. 인증

### 5.1 JSON 로그인

`formLogin`·`httpBasic`을 끄고 `AuthController`에서 처리합니다.

1. `LoginRequest` 검증
2. `AuthenticationManager`로 인증 (`CustomUserDetailsService` + `BCryptPasswordEncoder`)
3. 세션 고정 공격 방어: Spring Security의 세션 인증 전략을 쓰거나, **기존 세션이 있을 때만** `changeSessionId()`를 호출합니다(세션이 없을 때 호출하면 예외). 직접 로그인을 구현하면 자동으로 되지 않으므로 명시적으로 처리합니다
4. 새 `SecurityContext`를 만들어 `HttpSessionSecurityContextRepository`에 **명시적으로 저장**. Spring Security 6은 자동 저장하지 않아 빠뜨리면 다음 요청이 401
5. `UserResponse` 반환

인증에는 비밀번호 해시가 필요하므로, 세션에 남는 principal에서 해시가 지워지게 합니다. `UserPrincipal`이 `CredentialsContainer`를 구현해 인증 후 해시를 지우거나, 인증용 객체와 별도의 세션 principal(`userId`, `loginId`, `nickname`, `role`만)을 씁니다. 어느 쪽이든 로그인 후 세션의 principal에 해시가 없는지 테스트합니다. 엔티티는 담지 않습니다.
로그아웃은 세션 무효화, SecurityContext 삭제, 쿠키 만료를 함께 처리합니다.

### 5.2 접근 규칙과 401

| 경로 | 접근 |
|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login` | 모두 |
| 그 밖의 `/api/**` | 로그인 필요 |
| 정적 파일, SPA 경로, `/error` | 모두 |

- `JsonAuthenticationEntryPoint`: 401 `{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}` (UTF-8). 리다이렉트나 HTML 로그인 화면이 나오지 않게 합니다.
- 필터 단계 오류는 `@RestControllerAdvice`에 오지 않으므로 EntryPoint와 `AccessDeniedHandler`가 같은 `ErrorResponse` 형식으로 직접 씁니다.
- 로그아웃은 컨트롤러로 구현합니다(비로그인 401을 지키기 위해). 기본 요청 캐시는 끕니다.
- 비밀번호: BCrypt 기본 강도 10. 로그에 요청 본문을 남기지 않습니다.
- 역할은 저장만 하고 MVP에는 ADMIN 전용 API가 없습니다.
- 세션은 서버 메모리에 있어 재시작하면 모두 로그아웃됩니다(게임 진행은 DB에 남음).

### 5.3 CSRF: SameSite=Lax + 엄격한 Origin 검사 `[D7, D27]`

**전제:** React와 API를 같은 출처로 제공하는 **시연용 배포**입니다. CSRF 토큰은 쓰지 않으며 `http.ts`는 고치지 않습니다. 일반 운영 서비스로 바꾸거나 다른 출처를 허용해야 하면 토큰 방식을 다시 검토합니다.

| 프로필 | 동작 |
|---|---|
| 개발(기본·`local`) | CSRF 비활성화, **Origin 검사 비활성화** (Vite 5173과 서버 8080의 Origin이 다름) |
| `prod` | CSRF 토큰 비활성화 + 아래 Origin 검사 |

| 항목 | `prod` 규칙 |
|---|---|
| 대상 | `/api/**`의 `POST`·`PUT`·`PATCH`·`DELETE`. 로그인·가입·본문 없는 logout 포함 |
| 헤더 읽기 | `getHeader()` 하나만 보지 않고 **모든 `Origin` 헤더의 개수**를 확인 |
| 설정값 | `scheme://host[:port]`만 허용. path·query·fragment가 있으면 `prod` 시작 실패 |
| 정규화 | scheme·host는 소문자, 생략한 포트는 기본 포트(http 80, https 443)로 맞춘 뒤 비교 |
| Origin 없음 | **거절** |
| `Origin: null`, 헤더 여러 개 | **거절** |
| 허용 목록과 불일치 | **거절**. scheme·host·port가 모두 정확히 같아야 함. 부분 일치·와일드카드 없음 |
| 대체 수단 | `Referer`로 대신하지 않음 |
| 허용 목록 | 설정 `app.security.allowed-origins`(환경 변수). **`prod`에서 비어 있으면 시작 실패** |
| 비교 기준 | 설정값만. 허용 목록을 `Host`, `X-Forwarded-Host`로 만들지 않으므로 헤더 위조의 영향이 없음 |
| 응답 | `403` `{"code":"FORBIDDEN","message":"허용되지 않은 요청입니다."}` (UTF-8 JSON). 인증 필터보다 앞에서 처리 |
| 노출 금지 | 응답에 허용 Origin, 설정 이름, 받은 Origin 등 내부 값을 넣지 않음. 거절 사유는 서버 로그에만 |

- 필터가 인증보다 앞이므로 `prod`에서 Origin 없는 비로그인 POST는 401이 아니라 **403**입니다. 정상 동작이며, 401을 확인하는 T14는 개발 프로필, Origin을 확인하는 T27은 `prod` 프로필에서 실행합니다.
- 필터 단계의 거절과 Spring Security의 `AccessDeniedHandler`도 같은 `ErrorResponse` 형식을 직접 씁니다.
- 브라우저는 같은 출처의 POST `fetch`에도 Origin을 보내므로 프론트 수정은 필요 없을 것으로 봅니다(배포 환경에서 확인). `curl`·`.http`로 운영 서버를 확인할 때는 Origin 헤더를 직접 넣습니다.
- 한계: 토큰이라는 방어 수단이 없고, Origin 검사와 `SameSite` 설정에 의존합니다.

#### 쿠키·CORS·프록시

- **세션 쿠키:** `prod`에서 `server.servlet.session.cookie.secure=true`를 **명시**합니다. 프록시 헤더로 HTTPS를 추정하는 데 의존하지 않습니다. `HttpOnly`, `SameSite=Lax`도 함께 적용합니다. HTTPS가 필수입니다(D21의 조건).
- **검증:** `prod` 프로필로 실제 서버를 띄워 로그인 응답의 `Set-Cookie`를 자동 테스트(T28)하고, 배포 후 브라우저로 한 번 더 확인합니다. MockMvc는 컨테이너의 세션 쿠키를 만들지 않으므로 실제 포트로 테스트합니다.
- **CORS를 열지 않습니다.**
- **전달 헤더(`X-Forwarded-*`):** 기본값은 신뢰하지 않음(`server.forward-headers-strategy=none`). 리버스 프록시가 있을 때만 `native`로 바꾸고, `server.tomcat.remoteip.internal-proxies`를 **그 프록시의 IP만** 허용하도록 좁힙니다(Tomcat 기본값은 사설 대역을 넓게 신뢰하므로 그대로 두지 않음). 앱 포트는 프록시에서만 접근할 수 있게 하고(같은 서버면 `127.0.0.1` 바인딩, 아니면 방화벽), 프록시는 클라이언트가 보낸 전달 헤더를 덮어써야 합니다. 신뢰 범위의 실제 값은 배포 장소가 정해지면 적습니다(D21).

---

## 6. 게임 행동 처리

### 6.1 트랜잭션 구성

```
GameActionController                       (트랜잭션 없음)
  └─ GameActionFacade                      (트랜잭션 없음)
       ├─ Investigation / Exclusion / AccusationService   ← 행동 트랜잭션 (READ_COMMITTED)
       └─ ActionReplayService (별도 빈)     ← REQUIRES_NEW, 읽기 전용
```

- **재생은 항상 행동 트랜잭션이 끝난 뒤 Facade가 호출합니다.** 행동 서비스는 이미 처리된 requestId를 발견하면 아무것도 쓰지 않고 `ActionOutcome`(이미 처리됨)을 반환해 트랜잭션을 마칩니다. 트랜잭션 안에서 `REQUIRES_NEW`를 부르면 잠금을 쥔 채 두 번째 DB 연결을 요구해 연결 풀이 고갈될 수 있기 때문입니다.
- `ActionReplayService`는 **별도 빈**이어야 합니다. 같은 클래스 안에서 호출하면 스프링 프록시를 거치지 않아 `@Transactional`이 적용되지 않습니다. 독립 트랜잭션임은 T21로 증명합니다.
- Controller와 Facade에는 `@Transactional`을 붙이지 않습니다.

⚠️ **행동 트랜잭션의 격리 수준은 `READ_COMMITTED`입니다.** MySQL 기본인 `REPEATABLE_READ`에서는 1단계의 일반 SELECT가 **잠금을 얻기 전에** 스냅숏을 만듭니다. 그러면 잠금을 기다리는 동안 앞 요청이 commit한 `session_clue`, `session_suspect`, `session_action`이 이후의 일반 SELECT(잠금 직후 재조회 포함)에 보이지 않습니다(`FOR UPDATE`로 읽은 행만 최신).

### 6.2 공통 단계

| 단계 | 처리 | 실패 시 |
|---|---|---|
| 0 | 인증(필터), 입력 검증, requestId 정규화 | 401 / 400 |
| 1 | `session_action`에서 requestId 조회. 있으면 "이미 처리됨"으로 트랜잭션 종료 → **6.6 재생** | |
| 2 | `play_session`을 ID로 `PESSIMISTIC_WRITE` 조회(`SELECT … FOR UPDATE`). **이 트랜잭션에서 세션 행을 처음 읽는 조회여야 함** | 없으면 404 |
| 2-1 | **잠금 직후 같은 requestId 재조회** `[D12]`. 있으면 "이미 처리됨"으로 종료 → 6.6 | |
| 3 | `session.userId == 로그인 userId` | 403 `NOT_SESSION_OWNER` |
| 4 | `status == IN_PROGRESS` | 409 `SESSION_ENDED` |
| 4-1 | 사건의 `validation_status == VALID` (트랜잭션 안에서 DB에서 읽음, 사건 행은 잠그지 않음) `[D29]` | 409 `CASE_UNAVAILABLE` |
| 5 | 단서·용의자가 존재하고 세션의 사건 소속인지 | 400 `TARGET_NOT_IN_CASE` |
| 6 | 수집 여부 / ACTIVE 여부 | REJECTED 기록 |
| 7 | `dedup_key` 존재 여부 | REJECTED 기록 |
| 8 | 판정 | |
| 9 | 점수·세션·용의자·수집 단서 갱신 | |
| 10 | `session_action` insert | |
| 11 | commit | 예외면 전체 롤백 |

- 3~5단계(4-1 포함)는 4xx 예외이므로 아무것도 기록되지 않습니다 `[D2]`.
- 4-1이 재생(1, 2-1) 뒤에 있으므로 이미 처리한 요청의 재전송은 사건 상태와 관계없이 저장 결과를 돌려줍니다. 소유자 확인 뒤라 남의 세션으로 사건 상태를 알 수 없고, 종료 세션은 `SESSION_ENDED`입니다.
- REJECTED는 예외가 아니라 정상 반환입니다. `dedup_key = NULL`, `score_delta = 0`, `request_id`는 저장하고 commit합니다.
- 응답의 `state`는 갱신을 반영해 `GameStateAssembler`로 만듭니다.
- 잠금 대상은 `play_session` 한 행뿐이라 같은 세션의 행동은 한 줄로 처리됩니다. 잠금 쿼리에는 fetch join을 붙이지 않습니다(다른 테이블 행까지 잠기지 않게).

### 6.3 조사 (investigations)

| 단계 | 내용 |
|---|---|
| 5 | 단서가 사건 소속이고 `kind == SCENE` |
| 6 | 이미 `session_clue`에 있으면 REJECTED |
| 7 | `INV:{clueId}` |
| 8 | 판정 없음 → SUCCESS |
| 9 | `session_clue` insert |
| 10 | INVESTIGATE / SUCCESS 행을 **먼저** insert |
| 10-2 | 추론 자동 생성(6.7). INFER 행은 INVESTIGATE 행 **뒤에** insert (이 순서가 재생의 근거) |

### 6.4 배제 (exclusions)

| 단계 | 내용 |
|---|---|
| 5 | 단서와 `caseSuspectId` 모두 사건 소속 (단서는 SCENE·INFERENCE 모두 가능) |
| 6 | ① 단서 수집 → ② 용의자 ACTIVE 순서로 확인 |
| 7 | `EXC:{clueId}:{caseSuspectId}` |
| 8 | 단서에 규칙이 있고 판정기가 true → SUCCESS, 그 외(규칙 없음 포함) → FAIL |
| 9 SUCCESS | `excludeByEvidence(clue)`, 점수 변화 0 |
| 9 FAIL | `delta = -min(10, score)`, 점수 반영, `wrongExclusionCount + 1` (점수가 0이어도 증가) |
| 10 | EXCLUDE 행. `score_delta`는 **실제 깎인 값**(0점이면 0) |

### 6.5 지목 (accusations)

| 단계 | 내용 |
|---|---|
| 5 | `caseSuspectId`가 사건 소속 |
| 6 | ACTIVE가 아니면 REJECTED (`clue_id` NULL) |
| 7 | `ACC:{accusationCount + 1}` |
| 8 | `caseSuspect.pokemon.id == case.answerPokemon.id` |
| 9 정답 | `accusationCount + 1` → 나머지 3명이 모두 `EXCLUDED_BY_EVIDENCE`면 보너스 +10, `bonusAwarded=true` → `finish(SOLVED)` → 등급 |
| 9 오답 | `accusationCount + 1`, `delta = -min(30, score)`, `eliminateByWrongAccusation()`. 2회째면 `finish(FAILED)` → 점수 0, 등급 NULL |
| 10 | ACCUSE 행. `score_delta`는 정답이면 보너스(10 또는 0), 오답이면 -30(하한 적용). FAILED의 "0점 처리"는 `score_delta`에 넣지 않음 `[D16]` |

정답 여부는 서버 안에서만 비교하며, 진행 중 응답에는 정답 식별 정보를 넣지 않습니다.

### 6.6 requestId 재전송: 저장된 결과 다시 만들기

DB에 응답 저장 컬럼이 없으므로 **`session_action` 행과 그 이전 이력으로 재구성**합니다. 같은 세션의 쓰기는 잠금으로 직렬화되므로 세션 안의 `action_id` 순서가 곧 처리 순서입니다.

재생은 `ActionReplayService` 한 곳에서만 하며, 1단계·2-1단계·insert 충돌이 모두 같은 코드를 탑니다. requestId로 행을 다시 읽는 것부터 시작합니다.

**반환 전 검사 (순서 고정)**

1. 경로의 `sessionId` 세션을 조회 → 없으면 404
2. 로그인 사용자가 그 세션의 소유자인지 → 아니면 403
3. 저장된 행이 이번 요청과 같은지 → 하나라도 다르면 400 `VALIDATION_ERROR` "이미 사용된 requestId입니다." `[D11]`

| 비교 항목 | 조사 | 배제 | 지목 |
|---|---|---|---|
| `session_id` | = 경로의 sessionId | = 경로의 sessionId | = 경로의 sessionId |
| `action_type` | INVESTIGATE | EXCLUDE | ACCUSE |
| `clue_id` | = 요청 `clueId` | = 요청 `clueId` | NULL |
| `case_suspect_id` | NULL | = 요청 `caseSuspectId` | = 요청 `caseSuspectId` |

400 응답은 불일치 이유(남의 것 / 내 다른 세션 / 대상 차이)에 관계없이 **완전히 같으며** 저장된 행의 내용은 담지 않습니다. 남의 세션 경로로 오면 requestId 존재 여부와 상관없이 3번 전에 403입니다.

세 검사를 모두 통과해야 재구성합니다. IN_PROGRESS 검사는 하지 않습니다(수사를 끝낸 지목의 재전송이 성공해야 함). 읽기만 하며 점수·기록은 바뀌지 않습니다.

| 응답 필드 | 재구성 방법 |
|---|---|
| `result`, `scoreDelta` | 행의 값 그대로 |
| `newClueIds` | INVESTIGATE·SUCCESS면 `[clue_id]` |
| `inferredClueIds` | INVESTIGATE·SUCCESS면, 같은 세션에서 이 행 **바로 뒤에 연속된 INFER 행들**(다음 non-INFER 행 전까지)의 `clue_id`를 `action_id` 순으로 |
| `rebuttalText` | EXCLUDE·FAIL이면 해당 단서의 `rebuttal_text` |
| `bonusAwarded` | ACCUSE·SUCCESS면 `play_session.bonus_awarded` (종료 후 불변) |
| 조사 `message` | SUCCESS는 `inferredClueIds` 유무, REJECTED는 고정 문구 |
| 배제 `message` | SUCCESS/FAIL은 결과 + 포켓몬 이름. REJECTED는 **그 시점 이전 행**으로 사유 판정: ① 더 작은 `action_id` 중 이 단서의 INVESTIGATE/INFER SUCCESS가 없으면 "미수집" ② 이 용의자의 EXCLUDE SUCCESS 또는 ACCUSE FAIL이 있으면 "이미 빠진 용의자" ③ 그 외 "이미 제출" |
| 지목 `message` | REJECTED 고정 문구 / SUCCESS / FAIL은 `dedup_key`가 `ACC:2`면 2차 오답 문구 |
| `state` | **현재** GameState (당시 상태가 아님. mock과 동일) |

**재생의 이력 조회는 항상 `session_id`로 제한하고 `action_id` 오름차순으로 읽습니다.** `inferredClueIds`는 `session_id = ? AND action_id > ? ORDER BY action_id ASC`로 읽어 첫 non-INFER 행 앞까지 취합니다. `action_id`는 전체 테이블에서 발급되므로 다른 세션의 행을 섞지 않아야 연속성이 성립합니다.

`ActionMessages`는 최초 처리와 재생이 같이 씁니다.

### 6.7 추론 단서 자동 생성 (`InferenceService`)

1. 사건의 INFERENCE 단서(`displayOrder` 순), 선행 조건, 세션의 수집 목록을 한 번에 읽음
2. 미수집 추론 단서 중 **선행 조건이 1개 이상이고 모두 수집된** 것을 수집: `session_clue` insert + `INFER / SUCCESS / INF:{clueId} / request_id NULL / score_delta 0` 행 insert, 메모리의 수집 목록에도 추가
3. 한 바퀴에서 하나라도 새로 생기면 2를 반복, 없으면 종료
4. 생성 순서대로 ID 목록 반환

매 바퀴 미수집 단서가 줄어야 계속되므로 반드시 끝납니다. CASE 2는 I1 → I2가 연쇄됩니다.

### 6.8 동시 중복 요청

**전체 흐름 (Facade)**

1. 행동 트랜잭션 실행: 첫 조회(1) → `FOR UPDATE`(2) → 잠금 직후 재조회(2-1) → 3~10 → commit
2. 결과가 "이미 처리됨"이면 트랜잭션 종료 후 6.6 재생
3. `DataIntegrityViolationException`이 나오면, 행동 트랜잭션이 **롤백까지 완전히 끝난 뒤**(예외가 서비스 프록시 밖으로 나온 뒤) 별도 빈의 새 트랜잭션에서 requestId를 재조회
   - 있으면 6.6의 검사 3가지를 거쳐 재생(또는 400)
   - **없으면 원래 무결성 예외를 그대로 다시 던짐** → 500, 로그

**예외 문자열이나 제약 이름은 해석하지 않습니다.** 드라이버·Hibernate 버전에 따라 형식이 달라질 수 있습니다. 판단 기준은 "재조회에서 행이 나오는가" 하나입니다. 서비스 안에서는 예외를 잡지 않습니다(insert가 실패한 트랜잭션은 rollback-only). `IDENTITY` 전략이라 insert는 저장 시점에 실행되며, 롤백되면 점수·`session_clue` 변경도 함께 취소됩니다.

| 동시 상황 | 처리 경로 |
|---|---|
| 같은 세션에 같은 requestId 2건 (첫 요청이 수사를 끝낸 지목인 경우 포함) | 두 번째는 잠금 대기 후 **2-1에서 발견** → 재생. 4단계(`SESSION_ENDED`)와 insert까지 가지 않음 |
| 서로 다른 세션에 같은 requestId 2건 | 잠금이 서로 달라 둘 다 통과할 수 있음 → 늦은 쪽이 insert 충돌 → 흐름 3 → 400 |
| 다른 requestId로 같은 행동 2건 | 두 번째는 6~7단계에서 REJECTED |

잠금 대기 시간 초과나 교착은 별도로 변환하지 않고 500 `INTERNAL_ERROR`로 보냅니다(변경은 롤백됨). 잠금 대상이 세션 행 하나라 교착 가능성은 낮습니다.

### 6.9 판정기 구조 (`judge`)

- `ExclusionJudge`: "담당 `ConditionType`"과 "`PokemonProfile`과 조건 값을 받아 **배제 가능하면 true**"의 두 기능. 구현 7개.
- `PokemonProfile`: 타입 집합, `heightDm`, `weightHg`, shape, color, trait 코드 집합. **정수 dm·hg만** 가지며, trait는 `is_judgment`와 관계없이 전부 담습니다(mock과 동일).
- `JudgeRegistry`: `ConditionType → Judge` 맵. 시작할 때 7종이 모두 하나씩 등록됐는지 확인하고 아니면 실패.
- 게임(`ExclusionService`)과 검증기(`LogicChecks`)가 **같은 판정기**를 씁니다.

| 조건 | 배제 성공(true) | 경계 |
|---|---|---|
| `HAS_TYPE` | type1, type2 모두 값과 다름 | type2 NULL 허용 |
| `NOT_TYPE` | type1 또는 type2가 값과 같음 | |
| `MAX_HEIGHT` | `heightDm > 값` | 같으면 false |
| `MAX_WEIGHT` | `weightHg > 값` | 같으면 false |
| `SHAPE_IS` | `shape ≠ 값` | |
| `COLOR_IS` | `color ≠ 값` | |
| `NOT_TRAIT` | trait 코드 보유 | |

project_context의 판정 계산표, mock의 `excludes()`와 일치합니다.

**샘플 데이터 판정 기대값** (손 계산, 테스트의 정답으로 사용)

| 사건 | 단서(규칙) | 배제되는 용의자 |
|---|---|---|
| CASE 1 | E2 (`NOT_TYPE ghost`) | 고우스트, 무우마 |
| | I1 (`MAX_HEIGHT 10`) | 고우스트(16), 윤겔라(13) |
| CASE 2 | I1 (`HAS_TYPE fire`) | 꼬부기, 코일 |
| | I2 (`NOT_TRAIT CONSTANT_TAIL_FLAME`) | 파이리 |
| CASE 3 | E1 (`SHAPE_IS quadruped`) | 깨비참 |
| | E5 (`COLOR_IS brown`) | 니드런♀ |
| | I1 (`MAX_WEIGHT 100`) | 지그제구리(175) |

### 6.10 점수·등급 계산 위치 (`ScorePolicy`)

상수(시작 100, 오배제 10, 오지목 30, 보너스 10, 지목 2회, A 기준 80)와 계산을 `ScorePolicy` 한 곳에 둡니다. 서비스는 호출만 합니다.

| 계산 | 규칙 |
|---|---|
| 감점 | `실제 delta = -min(감점, 현재 점수)` |
| 보너스 | 정답 지목 시 오답 3명이 모두 `EXCLUDED_BY_EVIDENCE` |
| S | SOLVED + 틀린 배제 0 + 틀린 지목 0(`accusationCount == 1`) + 보너스 |
| A / B | S가 아닌 SOLVED 중 최종 점수 80 이상 / 미만 |
| FAILED | 점수 0, 등급 NULL |

점수가 0까지 내려가도 수사는 계속됩니다. 수사는 지목으로만 끝납니다.

### 6.11 클라이언트의 requestId 재사용 (프론트, 미구현)

**현재 문제:** `GamePage`는 버튼을 누를 때마다 `newRequestId()`를 새로 만듭니다. 서버가 처리했는데 응답만 유실되면, 다시 누른 요청이 새 행동이 됩니다. 특히 **오답 지목이 두 번 처리되어 수사가 실패**할 수 있습니다.

**규칙** (단계 2에서 공통 흐름으로 만들고, **지목을 서버에 연결하기 전에 반드시** 적용)

- 논리적 행동 하나에 `requestId`를 한 번만 만들고, 행동 종류·대상·`requestId`를 pending으로 보관합니다.
- 결과를 **알 수 없으면** pending을 유지하고, 재시도는 같은 `requestId`를 씁니다.
- pending을 지우는 것은 결과가 **확정된** 경우뿐입니다: 본문을 정상으로 읽은 2xx, 또는 4xx.
- 결과를 알 수 없는 경우: `NETWORK_ERROR`, **5xx**(프록시의 502·504는 서버가 처리한 뒤에도 날 수 있음), **2xx인데 본문을 읽지 못한 경우**. 현재 `http.ts`는 마지막 경우에 `null`을 성공으로 돌려주므로 오류로 바꿔야 합니다. (5xx와 본문 유실은 검토 의견의 "응답을 받으면 제거"보다 좁힌 것입니다. 같은 ID의 재전송은 서버가 재생하므로 안전합니다.)
- 결과를 모르는 상태에서 사용자가 **다른 행동**을 하려면 먼저 GameState를 다시 조회해 확인한 뒤 pending을 지웁니다.
- `busy` 상태 외에 `useRef`의 동기 가드로, 화면이 다시 그려지기 전의 빠른 중복 실행도 막습니다.

---

## 7. 사건 검증기

### 7.1 실행 방식: 요청을 받기 전에 실행

`ApplicationRunner`는 웹 서버가 포트를 연 **뒤에** 실행되어 검증 전 사건이 노출될 수 있으므로 쓰지 않습니다.

- `CaseValidationGate`(`SmartLifecycle`)가 `validateAll()`을 동기 실행합니다. `getPhase()`는 **`-4096`으로 명시**합니다. 끝나야 포트가 열리게 하며, 여기서 예외가 나면 **애플리케이션 시작 실패**입니다.
  - 2026-11-28 3.5.16 바이트코드 확인: phase가 낮을수록 먼저 시작합니다. 웹 서버 시작(`WebServerStartStopLifecycle`)은 `2147481599`입니다. ⚠️ `SmartLifecycle`의 **기본값은 `2147483647`(`DEFAULT_PHASE`)이라 재정의하지 않으면 웹 서버보다 늦게 실행됩니다.** (`Phased`가 아닌 단순 `Lifecycle`은 0으로 취급되어 웹 서버보다 먼저입니다.)
- 이 순서는 T24에서 **실제 서버**로 확인합니다. 가정이 깨지면 "검증 완료 전 `/api/**` 차단" 방식으로 전환하고 응답 코드를 정합니다.
- `is_enabled`와 현재 `validation_status`에 관계없이 **모든 사건**을 검사합니다. 데이터 동기화 기능이 생기면 같은 `validateAll()`을 씁니다.
- `CaseDefinitionLoader`가 DB를 값 객체 `CaseDefinition`으로 읽고, `CaseValidator`는 DB 없이 검사합니다(단위 테스트 가능).

**두 단계로 나눕니다**

| 단계 | 트랜잭션 | 내용 |
|---|---|---|
| 1. 검사 | 읽기 전용 | **모든 사건**을 읽고 검사해 위반 목록을 만듦. 아무것도 쓰지 않음 |
| 2. 반영 | `CaseValidationResultWriter`(별도 빈)의 `REQUIRES_NEW` 하나. `validation_status`, `validated_at`만 갱신 | 1단계가 전부 정상 종료하고 검사 트랜잭션이 끝난 뒤에만 실행 |

**"검증 위반"과 "예상하지 못한 예외"의 구분**

| 구분 | 예 | 처리 |
|---|---|---|
| 예상된 검증 위반 | 아래 A·B 항목 실패. 검사기가 **값(`ValidationViolation`)으로 반환** | 해당 사건 `validation_status = INVALID` |
| 예상하지 못한 예외 | DB 접속·조회 오류, 매핑 오류, 검증 코드의 버그 | **어떤 사건의 상태도 바꾸지 않고** 시작 실패. 앞에서 위반이 발견된 사건도 반영하지 않음 |

숫자가 아닌 `condition_value`처럼 **데이터가 잘못된 경우는 검사기가 위반으로 판정**하며 예외를 흘리지 않습니다. 예외를 통째로 잡아 위반으로 바꾸는 코드는 두지 않습니다. 2단계 쓰기가 실패하면 전체 롤백 후 시작 실패입니다.

### 7.2 A. 데이터 무결성 (먼저)

| 코드 | 검사 |
|---|---|
| A1 | 용의자 정확히 4명 |
| A2 | 정답 포켓몬이 용의자에 포함 |
| A3 | 선행 조건의 두 단서가 같은 사건 (한쪽이라도 이 사건인 모든 선행 조건을 검사) |
| A4 | 선행 조건 자기 참조 없음 |
| A5 | 선행 조건 순환 없음 (DFS 또는 위상 정렬) |
| A6 | SCENE 단서는 클릭 좌표 4개 모두 존재 |
| A7 | INFERENCE 단서는 클릭 좌표 모두 NULL |
| A8 | 사건 단서에 연결된 규칙의 `condition_type`·`condition_value` 유효 |
| A9 | INFERENCE 단서는 선행 조건 1개 이상 `[D15]` |
| A10 | SCENE 단서는 선행 조건 없음 `[D15]` |
| A11 | 클릭 영역이 이미지 안: `x + w ≤ 1`, `y + h ≤ 1` (`BigDecimal` 비교, 좌표가 있을 때) `[D15]` |

A8의 "유효" 정의

| 조건 | 허용 값 |
|---|---|
| HAS_TYPE, NOT_TYPE | PokéAPI 18개 타입 이름 |
| MAX_HEIGHT, MAX_WEIGHT | 1 이상의 정수 문자열 |
| SHAPE_IS | PokéAPI `pokemon-shape` 이름 목록 (코드 상수) |
| COLOR_IS | PokéAPI `pokemon-color` 10개 |
| NOT_TRAIT | `pokemon_trait`에 있고 `is_judgment = TRUE`인 `trait_code` `[D13-4]` |

A의 항목은 모두 검사해 위반을 모읍니다. 하나라도 있으면 B는 건너뜁니다.

### 7.3 B. 사건 논리 (A 통과 사건만) `[D13-1]`

"전체 규칙" = 사건 단서에 연결된 규칙 전부.

| 코드 | 이름 | 통과 조건 |
|---|---|---|
| B1 | 정답 유일성 | 전체 규칙을 적용하면 남는 용의자가 정확히 1명이고 정답 |
| B2 | 단일 단서 결정 금지 | 어느 단서 하나도 오답 3명을 모두 배제하지 않음 |
| B3 | 오답마다 배제 근거 | 오답 각각을 배제하는 단서가 1개 이상 |
| B4 | 정답 무결성 | 어떤 규칙도 정답을 배제하지 않음 |
| B5 | 미끼·보조 일관성 | 규칙이 없는 단서는 `is_core = FALSE` |
| B6 | 핵심 추리 필수성 | `is_core` 단서가 1개 이상 있고, **각** 핵심 단서마다 그 단서와 그것에 직접·간접으로 의존하는 추론을 함께 제거하면 남는 용의자 2명 이상 `[D14]` |

샘플 데이터 확인: mock 데이터(`02_sample.sql`을 옮긴 것)에 A8의 NOT_TRAIT 조건, A9~A11, B1~B6을 임시 스크립트로 적용해 세 사건 모두 통과함을 확인했습니다(A1~A7은 이 스크립트에서 검사하지 않음). B6 제거 후 남는 수는 CASE 1: 2 / CASE 2: I1 제거 4, I2 제거 2 / CASE 3: 2. Java 검증기와 실제 DB로는 9.2에서 다시 확인합니다.

### 7.4 상태 분리, 결과 반영, 로그 `[D26]`

| 컬럼 | 의미 | 바꾸는 주체 |
|---|---|---|
| `is_enabled` | 사람이 정하는 공개 여부 | 사람(SQL). **검증기는 절대 바꾸지 않음** |
| `validation_status` | `PENDING`(검증 전, 기본값) / `VALID` / `INVALID` | 검증기 |
| `validated_at` | **마지막 검증 시각** (VALID·INVALID 모두) | 검증기 |

**공개 중 = `is_enabled = TRUE AND validation_status = 'VALID'`.** 사건 목록과 새 수사 시작에 적용합니다.

| 검증 결과 | `validation_status` | `validated_at` | `is_enabled` |
|---|---|---|---|
| 통과 | `VALID` | 현재 시각 | 불변 |
| 예상된 데이터 위반 | `INVALID` | 현재 시각 | 불변 |
| 예상하지 못한 시스템 예외 | 불변 | 불변 | 불변 (애플리케이션 시작 실패) |

- 데이터를 고치고 재시작하면 자동으로 `VALID`가 됩니다. 수동으로 끈 사건(`is_enabled = FALSE`)은 통과해도 계속 비공개입니다.
- 시작할 때 상태를 `PENDING`으로 먼저 되돌리지 않습니다(예외 시 상태 불변 원칙).
- 검증을 한 번도 거치지 않은 새 사건은 `PENDING`이라 노출되지 않습니다. 검증기를 만들기 전 개발 단계의 처리는 11장의 `[D28]` 조건을 따릅니다.

```
INFO  [CaseValidator] CASE03 PASS (A 11/11, B 6/6)
ERROR [CaseValidator] CASE02 FAIL B1 정답 유일성: 남은 용의자=[4, 155], 기대=[155]
ERROR [CaseValidator] CASE02 FAIL B3 배제 근거 없음: pokemonId=4
WARN  [CaseValidator] CASE02 INVALID, 위반 2건
INFO  [CaseValidator] CASE01 VALID (is_enabled=FALSE 이므로 비공개)
INFO  [CaseValidator] 검증 완료: 전체 3, 통과 2, 실패 1
ERROR [CaseValidator] 검증 중단: 예상하지 못한 오류. 사건 상태를 변경하지 않고 시작을 중단합니다 (+ 스택)
```

위반 형식: `[CaseValidator] {caseCode} FAIL {검사코드} {이름}: {원인과 관련 ID}`, 1건당 1줄. 상태 변경·"검증 완료" 로그는 2단계 commit 후에 남깁니다. 통과 사건의 배제표는 DEBUG 로그로만 남깁니다.

### 7.5 공개 중이 아닌 사건의 기존 세션 `[D17, D29]`

| 사건 상태 | 새 수사·이어하기 API | 기존 세션 조회 | 조사·배제·지목 | 종료 세션의 결과·타임라인·기록 |
|---|---|---|---|---|
| enabled + VALID | 허용 | 허용 | 허용 | 허용 |
| disabled + VALID (사람이 비공개) | 404 | 허용 | 허용 | 허용 |
| PENDING 또는 INVALID (enabled 무관) | 404 | 허용 | 409 `CASE_UNAVAILABLE` | 허용 |

- 재검증으로 `VALID`가 되면 별도 조작 없이 진행이 재개됩니다. 세션 데이터는 바꾸지 않습니다.
- 응답 메시지: "지금은 이 사건의 수사를 진행할 수 없습니다. 나중에 다시 확인해 주세요." `PENDING`·`INVALID` 구분이나 실패한 검사는 보내지 않습니다.
- 차단과 재생의 순서는 6.2의 4-1입니다.
- 프론트(`GamePage`, 반영함): `CASE_UNAVAILABLE`을 받으면 그 화면의 조사·배제·지목 버튼을 잠급니다. 새 API 필드(`playable`)는 두지 않으며, 새로고침하면 다시 시도할 수 있습니다.
- 사건 목록에는 안 보이고 이어하기 API는 404이므로, 이런 진행 중 세션에는 마이페이지의 "이어 하기"(`/play/{id}`)로 들어갑니다.

**조회가 성공해야 하는 범위:** 스키마가 허용하지만 검증에서 위반이 되는 데이터까지입니다.

- 용의자가 4명이 아니어도 **있는 만큼** 반환합니다. 용의자 목록은 세션의 `session_suspect` 행을 기준으로 조립합니다(세션 생성 뒤 사건 용의자가 추가돼도 실패하지 않음).
- 이미지 밖으로 나가는 좌표(`x + w > 1`)는 그대로 직렬화합니다.
- 잘못된 규칙 값은 행동이 차단되므로 판정에 쓰지 않습니다. 조회는 `basis_text`만 읽습니다.
- FK 파손, 매핑 오류, DB 장애는 숨기지 않습니다. 정상적으로 500(시작 시점이면 시작 실패)입니다.

실행 중 재검증(데이터 동기화)은 **MVP에서 제외**합니다. 그래서 4-1의 상태 읽기와 재검증의 경합은 없습니다. 나중에 추가하면 이 부분을 다시 설계합니다.

---

## 8. 오류 처리

응답 형식: `ErrorResponse{code, message}`. `ErrorCode` enum이 HTTP 상태·코드·기본 메시지를 가지고, `GlobalExceptionHandler`(`@RestControllerAdvice`)가 `ApiException`을 변환합니다.

| HTTP | code | 예외 클래스 | 발생 위치 |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `InvalidRequestException`, 아래 프레임워크 예외 | 입력 검증, requestId 오용 |
| 400 | `TARGET_NOT_IN_CASE` | `TargetNotInCaseException` | 행동 5단계 |
| 401 | `UNAUTHORIZED` | `LoginFailedException` / EntryPoint | 로그인 실패 / 비로그인 |
| 403 | `NOT_SESSION_OWNER` | `NotSessionOwnerException` | 세션 접근 전부 |
| 404 | `NOT_FOUND` | `ResourceNotFoundException` | 사건·세션 없음, 비공개 사건 시작 |
| 409 | `LOGIN_ID_TAKEN` | `LoginIdTakenException` | 가입 |
| 409 | `SESSION_ENDED` | `SessionEndedException` | 행동 4단계 |
| 409 | `SESSION_IN_PROGRESS` | `SessionInProgressException` | 결과·타임라인 |
| 409 | `CASE_UNAVAILABLE` | `CaseUnavailableException` | 행동 4-1단계 |
| 500 | `INTERNAL_ERROR` | (핸들러의 기본 처리) | 예상하지 못한 오류 |
| 403 | `FORBIDDEN` | `OriginCheckFilter`, `JsonAccessDeniedHandler` (필터 단계에서 직접 응답) | Origin 검사 거절 등. message "허용되지 않은 요청입니다." `[D27]` |

| 프레임워크 예외 | 응답 |
|---|---|
| `MethodArgumentNotValidException` | 400, 첫 필드 오류 메시지 |
| `HandlerMethodValidationException`, `ConstraintViolationException` | 400 (쿼리 파라미터 검증) |
| `HttpMessageNotReadableException` | 400 (깨진 JSON, 타입 불일치) |
| `MethodArgumentTypeMismatchException` | 400 |
| `AuthenticationException` (로그인) | 401, 아이디·비밀번호를 구분하지 않는 문구 |
| `NoResourceFoundException` (`/api/**`) | 404 `NOT_FOUND` |
| 지원하지 않는 메서드·Content-Type | 405 / 415, code `VALIDATION_ERROR` |
| 그 밖의 `Exception` (재조회에 행이 없던 무결성 예외 포함) | 500, code `INTERNAL_ERROR` `[D8]`. 스택은 로그에만, 응답에는 일반 문구 |

없는 `/api/**` 경로는 로그인 상태에서 JSON 404, 비로그인이면 401입니다. 어느 경우에도 `index.html`을 반환하지 않습니다.

일반 `Exception` 핸들러가 Spring Security 예외와 405 같은 표준 상태를 500으로 삼키지 않게 위 항목을 따로 처리합니다. `NETWORK_ERROR`는 프론트 전용이라 서버에서 쓰지 않습니다. REJECTED는 오류가 아니라 200입니다.

---

## 9. 테스트 계획

### 9.1 판정기 단위 테스트 (스프링 없음)

| 대상 | 케이스 |
|---|---|
| HasType | type1 일치 false, type2 일치 false, 둘 다 불일치 true, type2 NULL |
| NotType | type1 일치 true, type2 일치 true, 불일치 false |
| MaxHeight | 9·10 false, 11 true |
| MaxWeight | 99·100 false, 101 true |
| ShapeIs / ColorIs | 같으면 false, 다르면 true |
| NotTrait | 보유 true, 미보유·빈 목록 false |
| JudgeRegistry | 7종 전부 등록 |
| ScorePolicy | 0점 하한, S/A/B 경계(80·79), 보너스, FAILED |

### 9.2 검증기 테스트

- **실제 데이터:** 테스트 DB에 `01_schema.sql` + `02_sample.sql`을 넣고 `CaseDefinitionLoader`로 CASE 1~3을 읽어 ① 모두 통과 ② 사건마다 남는 용의자가 정답 1명 ③ 단서별 배제 집합이 6.9 표와 일치. 데이터를 Java에 복사하지 않아 SQL이 바뀌면 바로 드러납니다.
- **실패 케이스:** 작은 `CaseDefinition`을 하나씩 망가뜨려 A1~A11, B1~B6(B6은 핵심 단서 0개 포함)이 각각 해당 코드로 실패하는지. DB CHECK가 막아 만들 수 없는 상태(A4, A6, A7 등)도 여기서 확인합니다.
- 위반 시 `INVALID`가 되어 목록 API에서 사라지고, 데이터를 고쳐 다시 검증하면 `VALID`로 돌아오는지. 한 사건의 위반이 다른 사건에 영향 없는지.

### 9.3 게임 행동 통합 테스트 (`@SpringBootTest` + MockMvc + 실제 DB)

| # | 시나리오 | 확인 |
|---|---|---|
| T1 | CASE 3 완주 (S) | 110점, S, `active_flag` NULL |
| T2 | CASE 2 완주 | 조사 한 번으로 I1→I2 연쇄, INFER 행의 request_id NULL |
| T3 | 같은 requestId 순차 재전송 (조사·배제 FAIL·배제 REJECTED 3종·지목) | 응답의 `state` 외 필드 동일, 감점·행 추가 없음 |
| T4 | 수사를 끝낸 지목 재전송 | 409가 아닌 원래 SUCCESS/FAIL |
| T5 | 같은 requestId **동시** 2건 (조사, 배제 FAIL, 마지막 지목 각각, 반복 실행) | 행 1개, 감점 1회, 둘 다 200·같은 결과 |
| T6 | 다른 requestId로 같은 배제 동시 | 하나 FAIL, 하나 REJECTED, -10 한 번 |
| T7 | **남의 세션** 조회·행동·결과·타임라인 | 403, 행 추가 없음 |
| T8 | 내 requestId를 다른 세션에, 남의 requestId를 내 세션에 | 400, 상대 정보 미노출. 남의 세션 경로면 403 |
| T9 | **종료된 세션**에 새 requestId 행동 | 409 `SESSION_ENDED`, 기록 없음 |
| T10 | 다른 사건의 단서·용의자, INFERENCE 조사 | 400, 기록 없음 |
| T11 | 틀린 배제를 반복해 0점 | 음수 없음, `score_delta` 합 확인 |
| T12 | 지목 2회 실패 | FAILED, 0점, 등급 null |
| T13 | 세션 시작 동시 2건 | 진행 세션 1개, 양쪽 같은 `sessionId` |
| T14 | 비로그인 모든 보호 API (개발 프로필) | 401 JSON |
| T15 | GameState·목록 JSON 키 집합 | 4.15의 금지 필드 없음, null 필드 존재 |
| T16 | 결과·타임라인을 진행 중에 호출 | 409 `SESSION_IN_PROGRESS` |

#### 추가 시나리오

| # | 분류 | 시나리오 | 확인 |
|---|---|---|---|
| T17 | requestId | 같은 세션에서 같은 requestId를 **다른 행동 종류**, **다른 clueId**, **다른 caseSuspectId**로 재사용 | 모두 400, 점수·행 불변 |
| T18 | 정보 노출 | 남의 requestId를 추측해 ① 내 세션 경로 ② 남의 세션 경로로 전송 | ① 400이며 본문이 "내 requestId를 잘못 재사용"한 경우와 동일 ② 403이며 존재하지 않는 requestId와 동일. 상대의 단서·결과·상태 미노출 |
| T19 | 잠금 | 첫 요청이 잠금을 쥔 상태에서 같은 requestId의 두 번째가 1단계를 통과해 대기 (조사 / 배제 FAIL / 수사를 끝내는 지목) | 첫 commit 뒤 두 번째가 **2-1에서 발견해 재생**. 두 번째의 insert 시도 없음, `SESSION_ENDED` 아님 |
| T20 | 충돌 | ① 다른 세션에 같은 requestId 동시 전송 ② 재조회에 행이 없는 무결성 예외(Facade 단위 테스트) | ① 하나 성공, 하나 400 ② **원래 예외가 그대로** 전달 |
| T21 | 트랜잭션 | 재생 빈의 독립성 | AOP 프록시임. 바깥 트랜잭션 안에서 호출하면 바깥의 **미commit 행이 보이지 않고**, 바깥이 rollback-only여도 재생이 성공 |
| T22 | 검증기 | 첫 사건은 위반, 다음 사건 검사 중 예외 발생 | 시작 실패. **모든** 사건의 `validation_status`·`validated_at`·`is_enabled` 불변 |
| T23 | 검증기 | 위반 / 통과(`is_enabled` TRUE·FALSE 각각) / 반영 중 쓰기 실패 | INVALID / VALID, 두 경우 모두 `is_enabled` 불변, `validated_at` 갱신 / 전체 롤백 |
| T24 | 시작 순서 | 실제 서버를 띄우고 검증을 일부러 멈춘 상태에서 고정 포트에 접속 | 검증이 멈춘 동안 **TCP 연결과 HTTP 요청이 모두 성립하지 않음**. 검증 예외면 시작 실패 |
| T25 | 테스트 DB 안전 | 허용 밖 DB / 허용 밖 SQL 문장 / `allowMultiQueries`가 든 URL / `NO_BACKSLASH_ESCAPES` / 공백 없는 `--` | 삭제를 포함해 **어떤 변경 문장도 실행하기 전에** 중단. URL은 연결 전에 거절. 공백 없는 `--`는 주석으로 건너뛰지 않음 |
| T26 | 개발 DB 보호 | 테스트 계정의 `SHOW GRANTS` 전체, `metamong_case` 조회·쓰기 시도 | 허용: `USAGE ON *.*`, 정확히 테스트 DB(`_` 이스케이프) 권한. **실패:** `WITH GRANT/ADMIN OPTION`, 역할 부여, PROXY, 전역·동적 권한, 다른 DB·테이블 권한. 개발 DB 접근은 거부. 판단 규칙은 DB 없는 단위 테스트(`GrantPolicyTest`)로도 확인하고, 출력 형식은 실제 MySQL 8.0.46으로 검증 |
| T27 | Origin | `prod` POST: 누락, `null`, 다른 scheme·host·port, 하위 도메인, 위조한 `X-Forwarded-Host` / 허용 값 / 개발 프로필 | 허용 값만 통과. 나머지는 403 `FORBIDDEN`, 본문에 허용 Origin 없음, DB 불변. 로그인·logout 포함. 허용 목록이 비면 시작 실패. 개발 프로필은 검사 안 함 |
| T28 | 쿠키 | `prod` 프로필 실제 서버에서 로그인 | `Set-Cookie`에 `Secure`, `HttpOnly`, `SameSite=Lax` |
| T29 | SPA | 알 수 없는 `/api/xxx`, `/api/sessions/1/xxx` (GET·POST) / `/play/3` / 없는 이미지 | API는 JSON 404(비로그인 401)이며 HTML 아님 / `index.html` / 404 |
| T30 | 산출물 | jar 항목 검사, 개발 실행 | jar에 `application-local.yml`·`.properties` 없음 / `processResources` 결과에는 남아 `bootRun` 가능 |
| T31 | 산출물 | `-PwithFrontend`로 빌드한 JS 검사 | mock 표식 문자열 없음 (10.3) |
| T32 | 공개 조건 (단계 1) | `is_enabled` 2가지 × `validation_status` 3가지로 목록·새 수사·기존 세션 조회 | (TRUE, VALID)만 목록에 노출되고 새 수사 가능. 나머지는 목록에 없고 시작 404. 모든 상태에서 기존 세션 조회 성공(7.5의 허용 범위 데이터 포함, 500 없음) |
| T33-a | 행동 차단 (단계 2 조사, 3-3 배제, 3-4 지목) | disabled+VALID / PENDING / INVALID 세션의 행동 | disabled+VALID는 처리. PENDING·INVALID는 409 `CASE_UNAVAILABLE`, 두 상태의 응답이 동일, 행·점수 불변. 처리했던 requestId 재전송은 저장 결과. 남의 세션은 403, 종료 세션은 `SESSION_ENDED` |
| T33-b | 진행 재개 (3-2 이후) | INVALID로 만든 사건의 데이터를 고쳐 다시 검증 | VALID가 되고 같은 세션의 행동이 처리됨 |
| T33-c | 기록 접근 (단계 4) | 비공개·INVALID 사건의 결과·타임라인·내 기록, 마이페이지 "이어 하기" | 종료 세션 정보 조회 성공, 진행 중 세션은 링크로 진입 |
| T34 | requestId 재사용 (6.11, 단계 2·3) | 서버 처리 후 응답 유실, 5xx, 본문 없는 2xx 뒤 재시도. 빠른 두 번 클릭 | 같은 requestId 전송, 오답 지목이 한 번만 반영, 요청 중복 발사 없음 |

T7, T8, T17, T18은 응답 본문까지 비교합니다.

**T19를 확실하게 재현하는 방법:** 테스트에서 첫 요청을 "잠금 획득 후 commit 전"에 래치로 멈추고, 두 번째 요청이 1단계를 지나 잠금 조회를 호출한 것을 확인한 뒤 첫 요청을 풉니다. 두 요청을 동시에 출발시키는 방식만으로는 이 순서가 보장되지 않습니다.

동시성 테스트(T5, T6, T13, T19, T20)는 스레드가 각자 commit해야 하므로 테스트에 `@Transactional`을 붙이지 않고, 끝나면 플레이 테이블 4개를 FK 순서대로 비웁니다(이 삭제도 9.4의 DB 확인을 거친 뒤에만). 경합 테스트는 반복 실행합니다.

### 9.4 테스트 실행 분리와 DB 안전장치

#### 실행 분리

| Gradle 태스크 | 대상 | 필요한 것 |
|---|---|---|
| `test` | 순수 단위 테스트 (판정기, `ScorePolicy`, 검증 규칙, Facade, DB 안전장치의 가짜 연결 테스트). `integration` 태그 제외 | **JDK만. DB가 없어도 실행되고 통과해야 함** |
| `integrationTest` | `integration` 태그 (MySQL이 필요한 모든 테스트, 컨텍스트 부팅 테스트 포함) | D19에 따른 MySQL |

`build`·`check`에는 `test`만 연결합니다. 각 구현 단계의 완료 조건과 배포 전에는 **둘 다** 실행합니다. `integrationTest`는 DB 설정이 없으면 조용히 건너뛰지 않고, 필요한 설정을 안내하며 실패합니다.

#### 테스트 DB `[D19]`: 로컬 MySQL `metamong_case_test` + 전용 계정

- 전용 계정은 **`metamong_case_test`에만 권한**이 있습니다(코드 검사가 실패해도 DB가 막음).
- 접속 정보는 환경 변수 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`. 준비·실행 방법은 `backendWork/README.md`에 적습니다.

#### 안전장치

1. **무엇이든 삭제·생성하기 전에**, 초기화에 쓸 **바로 그 연결**에서 `SELECT DATABASE()`와 JDBC `getCatalog()`를 확인합니다. 둘 다 `metamong_case_test`와 **정확히** 같아야 합니다. 접미사 검사나 URL 문자열 검사로 대신하지 않고, 허용 이름은 환경 변수로 바꿀 수 없는 상수입니다. 다르거나 NULL이면 즉시 중단합니다.
2. **SQL은 실행 전에 전체를 먼저 검사합니다.** 실행 허용은 테이블 이름에 DB 접두어가 없는 `CREATE TABLE`, `INSERT INTO`뿐입니다. 원본의 `CREATE DATABASE`, `USE`는 알려진 문장으로 인식해 실행하지 않습니다. 그 밖의 문장(`DROP`, `ALTER`, `다른DB.테이블` 등)이 하나라도 있으면 **아무것도 실행하지 않고** 중단합니다. 두 문장만 문자열로 지우고 나머지를 그대로 실행하는 방식은 쓰지 않습니다.
3. 테이블 삭제·비우기는 코드에 고정한 12개 이름만 대상으로 하고, 1번 확인을 거친 연결에서만 합니다.
4. 접속 URL에 `allowMultiQueries`가 있으면 값과 관계없이 **연결 전에** 거절합니다(여러 문장이 한 번에 실행되지 않게). 삭제 전에 `SELECT @@SESSION.sql_mode`를 확인해 `NO_BACKSLASH_ESCAPES`가 있거나 확인할 수 없으면 중단합니다. `--`는 MySQL처럼 뒤에 공백·제어 문자가 있을 때만 주석으로 봅니다.
5. SQL 검사기는 MySQL 파서가 아니므로 **보조 방어**입니다. 보안 경계는 1번(실제 DB 이름 확인)과 테스트 DB 밖의 권한이 없는 전용 계정입니다.
6. 스프링이 SQL을 자동 실행하지 않게 합니다(`spring.sql.init.mode=never`). 통합 테스트 프로필은 `application-local.yml`의 DB 설정을 물려받지 않습니다.

원본 SQL 파일은 UTF-8로 읽고 테스트를 위해 따로 고치지 않습니다. 스키마에 `IF NOT EXISTS`가 없으므로 3번의 방식으로 먼저 삭제합니다.

---

## 10. 배포 구조

하나의 Spring Boot jar가 API와 React 화면을 함께 제공합니다(같은 출처, CORS 불필요).

### 10.1 빌드 순서

1. `frontendWork`에서 `npm ci` → `npm run build`
2. `frontendWork/dist/`를 **빌드 출력 폴더**의 `static/`으로 복사 (`src/main/resources/static`에 복사해 커밋하지 않음)
3. `bootJar` → 산출물 검사

Gradle 태스크로 묶되 `-PwithFrontend`를 줄 때만 프론트를 빌드합니다(백엔드만 개발할 때 Node 불필요). Windows에서는 `npm.cmd`를 실행합니다. **배포용 jar는 반드시 `-PwithFrontend`로 만듭니다.**

### 10.2 비밀 파일 차단

- `application-local*.{yml,yaml,properties}`, `application-secret.*`는 **`bootJar` 산출물에서만** 제외합니다. `processResources`는 건드리지 않습니다.
- 같은 파일이 들어갈 수 있는 일반 `jar` 태스크(`-plain.jar`)는 끕니다.
- `verifyBootJar`가 만들어진 jar를 직접 검사합니다(`bootJar` 뒤 자동 실행).
  - `BOOT-INF/classes`의 클래스가 아닌 파일은 **허용 목록**(`application.yml`, `application-local.yml.example`)이거나 `static/`, `META-INF/` 아래여야 함. `secrets.yml`, `.env` 등 목록에 없는 파일은 실패. `application-prod.yml`을 만들면 목록에 추가해야 함
  - 허용 폴더 안의 `.env*`, `*secret*`, `application-local*` 이름도 실패
  - 허용한 설정 파일에 `password`·`secret`·`token` 값이 직접 적혀 있으면 실패(`${환경_변수}`는 허용, 값은 출력하지 않음)
- 한계: 민감 키 검사는 "키: 값" 한 줄 형태만 봅니다. `static/` 파일의 내용은 검사하지 않습니다.

### 10.3 mock 빌드 차단

- `-PwithFrontend`의 npm 빌드 태스크가 **`VITE_USE_MOCK=false`를 직접 주입**합니다. 사용자의 `.env*` 파일이나 셸 환경에 의존하지 않습니다(프로세스 환경 변수가 `.env` 파일보다 우선). `.env.development*`는 `npm run build`에 적용되지 않고, 루트 `.gitignore`가 `.env.*`를 제외해 `.env.production`도 커밋되지 않습니다.
- `verifyFrontendBundle` 태스크가 빌드된 JS에서 mock 표식을 검색해 하나라도 있으면 실패시킵니다(T31). 표식: 저장 키 `memory-trace-mock-db-v1`, 속성 이름 `answerPokemonId`, 임시 계정 비밀번호, CASE 3 진상 문구 일부. mock 코드나 데이터 문구를 바꾸면 표식도 확인합니다.
- **왜 필수인가:** 2026-11-28에 직접 빌드해 확인했습니다. `VITE_USE_MOCK=false`로 빌드하면 위 표식 4개가 번들에 **하나도 없었고**, 플래그 없이 빌드하면 **4개 모두 들어갔습니다.** 즉 빠뜨리면 임시 서버로 동작할 뿐 아니라 **정답과 진상이 배포 JS에 공개됩니다.** 확인 당시 코드에서는 플래그만 맞으면 제외되므로 프론트 구조는 바꾸지 않았지만, 번들러 동작에 의존하므로 매 빌드에서 검사합니다.

`public/`의 `scenes/`, `sprites/`는 `dist`에 포함되어 DB의 `/scenes/…`, `/sprites/…` 경로가 그대로 맞습니다.

### 10.4 SPA 새로고침

React 라우트: `/`, `/login`, `/signup`, `/cases`, `/play/{id}`, `/result/{id}`, `/mypage`. `SpaWebConfig`의 리소스 리졸버로 처리합니다.

| 요청 | 응답 |
|---|---|
| `/api`, `/api/**` | 컨트롤러. **리졸버에서 명시적으로 제외.** 없으면 JSON 404 |
| 실제로 있는 정적 파일 | 그 파일 |
| 없는 파일인데 확장자가 있음 (`/scenes/x.webp`) | 404 (이미지 대체 표시가 동작하도록) |
| 그 밖의 GET | `index.html` → React Router가 처리 (모르는 경로는 `/`로) |

라우트를 하나씩 나열하지 않아 화면이 추가돼도 서버를 고치지 않습니다. Security는 `/api/**`만 인증을 요구합니다. 화면 접근 제어는 프론트의 `RequireAuth`가, 데이터 보호는 API 인증이 담당합니다. 테스트(T29)는 테스트용 `index.html`을 두어 프론트 빌드 없이 실행합니다.

### 10.5 실행 환경

- `prod` 프로필: DB 접속 정보와 허용 Origin은 환경 변수, `Secure` 쿠키 고정, 전달 헤더는 5.3 규칙.
- **HTTPS가 전제**입니다. HTTP로 배포하면 `Secure` 쿠키가 전송되지 않아 로그인이 유지되지 않습니다.
- 배포 DB에도 SQL 파일을 직접 실행합니다.
- 서버는 1대로 둡니다(메모리 세션).
- 배포 장소 `[D21 보류]`: 학교 제공 서버가 있는지 확인하고, 없으면 클라우드 VM을 씁니다.
- 배포 DB에서는 `02_sample.sql`의 회원·플레이 기록 부분을 실행하지 않고, 시연 계정은 가입 API로 만듭니다 `[D20]`.

---

## 11. 구현 순서

규칙: **작은 단위(기능 하나)로 구현·커밋하고, 그 단위의 테스트가 통과한 뒤 다음으로 갑니다.** 각 단계의 완료 조건은 `test`와 `integrationTest` 모두 통과입니다.
프론트 연결 확인은 `frontendWork/.env.development.local`에 `VITE_USE_MOCK=false`를 넣고 합니다(README 방식, gitignore 대상). 아직 없는 API를 쓰는 화면은 오류가 나는 것이 정상입니다.

### 시작 조건

D1, D7, D19, D26, D28, D29는 확정되었습니다. **단계 1은 단계 0의 완료 조건(아래)을 모두 충족한 뒤 시작합니다.** DB 설계서(11/30 제출)에는 `case_file.validation_status` 컬럼과 CHECK를 반영해야 합니다.

**검증기 전의 임시 절차 `[D28-b]`** — 사건 목록(1-3)이 검증기(3-2)보다 먼저이므로 일정을 유지하되 다음을 지킵니다.

1. `validation_status`를 `VALID`로 바꾸는 임시 UPDATE는 **로컬 개발 DB에서만** 손으로 실행합니다.
2. 그 UPDATE는 **커밋하는 파일이나 배포 스크립트에 넣지 않습니다.** 원본·샘플 SQL의 사건은 계속 `PENDING`입니다.
3. 목록·세션 테스트는 필요한 사건만 **테스트 안에서** `VALID`로 만듭니다.
4. 검증기가 완성되기 전에는 배포하지 않습니다.
5. 3-1·3-2가 끝나면 즉시 이 절차를 없애고 이 문단을 삭제합니다.

### 단계 0 — 11/28~11/30: 뼈대

| 순서 | 작업 | 확인 방법 |
|---|---|---|
| 0-1 | 프로젝트 생성, 설정 파일 분리, `test`/`integrationTest` 분리 | DB를 끈 상태에서 `test` 통과. `git status`에 `application-local.yml`이 안 보임 |
| 0-2 | jar 비밀 파일 차단, `verifyBootJar` | T30 |
| 0-3 | 테스트 DB 안전장치를 **먼저** 만들고 그다음 초기화 코드 | T25, T26 |
| 0-4 | 개발 DB 생성, SQL 2개 실행 | 사건 3, 용의자 12, 단서 25건. 한글 정상 |
| 0-5 | 엔티티 12개 + Enum + 리포지토리 | `ddl-auto=validate`로 부팅, 12개 엔티티 조회 테스트 (3.4의 위험 해소) |

**진행 상황 (2026-11-28) — 단계 0은 미완료입니다**

| 단계 | 상태 |
|---|---|
| 0-1, 0-2 | 코드와 DB 없는 확인 완료 |
| 0-3 | 코드 완료, T25와 권한 판단 규칙 통과. **T26 실제 실행 안 함** |
| 0-4 | **미실행** |
| 0-5 | 코드 완료, DB 없는 보조 비교 통과. **실제 MySQL 확인 안 함** |

단계 0 완료 조건 (모두 충족해야 완료)

- [ ] `01_schema.sql`, `02_sample.sql` 실행
- [ ] `application-local.yml` 작성
- [ ] 테스트 DB와 전용 계정 생성
- [x] T26 연결이 다른 DB를 가리키면 아무 쿼리도 보내지 않음 (DB 없는 테스트, 2026-11-28)
- [ ] `integrationTest` 통과 (실제 `SHOW GRANTS` 출력으로 T26 포함)
- [ ] 실제 MySQL에서 12개 엔티티 조회 성공 (`BOOLEAN`/`TINYINT(1)`, `DATETIME`, `CHAR(36)` 호환 확인)
- [ ] `ddl-auto=validate` 상태로 `bootRun` 성공
- [ ] Java 17 런타임 확인: **`build`·단위 테스트는 확인함**(Temurin 17.0.21, 2026-11-28). `integrationTest`·`bootRun`은 남음

비밀번호는 코드·문서·채팅에 남기지 않고 로컬 설정과 IntelliJ 실행 구성에만 입력합니다.

### 단계 1 — 12/1~12/3: 인증·사건 목록·세션

| 순서 | 작업 | 확인 방법 |
|---|---|---|
| 1-1 | 오류 처리 공통, Security 설정 | T14, T29의 API 부분 |
| 1-2 | signup·login·logout·me | 가입 → 로그인 → me → 로그아웃 → me 401. 72바이트 경계, DB 해시가 `$2a$`로 시작 |
| 1-3 | 사건 목록 | 3건, 정답 필드 없음 (T15의 목록 부분) |
| 1-4 | 세션 시작·이어하기, GameState | 201 후 200, `session_suspect` 4행, T13, T15, T32 |
| 1-5 | 프론트 연결 | 가입 → 로그인 → 목록 → 사건 시작 → 게임 화면, 새로고침 유지 |

### 단계 2 — 12/4~12/6: 게임 화면 연동 (CASE 3 조사 + 추론) `[D22]`

각 테스트는 이 시점에 있는 API(조사)에 해당하는 시나리오만 통과시키고, 배제·지목 시나리오는 3-3, 3-4에서 마저 통과시킵니다.

| 순서 | 작업 | 확인 방법 |
|---|---|---|
| 2-1 | 행동 공통 흐름 (`ActionGuard`, 잠금, 2-1 재조회, 4-1), 조사 API | T7, T9, T10, T33-a(조사) |
| 2-2 | 추론 자동 생성 | T2의 추론 연쇄, CASE 3 I1 |
| 2-3 | 재생 빈과 Facade | T3, T5, T8, T17, T18, T19, T20, T21의 조사 시나리오 |
| 2-4 | 프론트의 requestId 재사용 공통 흐름 (6.11, `GamePage`·`http.ts`) | T34(조사) |
| 2-5 | 프론트 연결 | CASE 3 현장 5곳 수집, I1 생성, 수첩·용의자 파일 표시 |

### 단계 3 — 12/7~12/9: 판정·검증기 (1차 개발 마감)

| 순서 | 작업 | 확인 방법 |
|---|---|---|
| 3-1 | 판정기 7종 | 9.1 |
| 3-2 | 검증기, 결과 반영, 시작 게이트. D28 임시 절차 제거 | 9.2, T22~T24, T33-b. 시작 로그에 3건 PASS |
| 3-3 | 배제 + 재생 | 6.9 표대로 성공, T6, T11, 배제의 T3·T5·T17·T19·T33-a |
| 3-4 | 지목·점수·등급, 결과 API. **지목 연결 전에 6.11 적용 확인** | T1, T4, T12, T16, 지목의 T3·T5·T19·T33-a·T34 |
| 3-5 | 전체 확인 | 프론트에서 CASE 3 완주 → 결과 → 다시 하기. 지금까지의 테스트 전부 통과 |

### 단계 4 — 12/10: 기록·테스트 배포 (D21 필요)

| 순서 | 작업 | 확인 방법 |
|---|---|---|
| 4-1 | `/api/me/*` 3개 | 요약·목록·타임라인, 계정 2개로 서로 안 보임, 진행 중 타임라인 409, 페이지 크기 검증, T33-c |
| 4-2 | 운영 보안 설정 (`prod` Origin 검사, 쿠키) | T27, T28 |
| 4-3 | 빌드 통합, SPA 처리 | T29~T31 |
| 4-4 | 배포 | HTTPS에서 쿠키 속성, `/play/{id}` 새로고침, `/api` 실호출, CASE 3 완주 |

### 이후

12/11 CASE 1·2 이미지·좌표(D24. SQL 수정 후 재시작 → 검증 통과 확인. 검증에서 꺼졌다면 7.4 복구 절차. CASE 1·2 좌표는 실제 이미지에 맞는지 확인 필요) / 12/12 전체 QA 후 기능 동결 / 12/13 시연 준비.

---

## 12. 결정 항목

### 12.1 미결정

| # | 정할 것 | 선택지 | 기한 |
|---|---|---|---|
| **D21** (보류) | 배포 장소 (HTTPS 필요, 프록시가 있으면 신뢰 IP 확정) | 학교 제공 서버 유무 확인 대기. **없으면 클라우드 VM**(결정됨) | 12/10 전 |

### 12.2 확정 (2026-11-28)

| # | 결정 | 반영 |
|---|---|---|
| D1 | Spring Boot **3.5.16** 고정, EOL 감수 | 0·1장 |
| D2 | 소유자·상태·사건 불일치는 4xx(기록 없음), 수집·ACTIVE·중복만 REJECTED | api_spec, project_context 7장 |
| D3 | 행동 응답에 GameState 전체 포함 | api_spec |
| D4 | 흔적 사전의 단서–규칙 연결 비공개 | api_spec |
| D5 | FAILED에도 정답·진상 공개 | api_spec |
| D6 | 가입 규칙 + 비밀번호 UTF-8 72바이트 이하 | api_spec, mock, `SignupPage` |
| D7 | 같은 출처 시연 배포: SameSite=Lax + 엄격한 Origin 검사(`prod`), 개발은 검사 안 함 | 5.3, api_spec |
| D8 | `{code, message}`, `SESSION_IN_PROGRESS`·`INTERNAL_ERROR` | api_spec, `types.ts`, `http.ts` |
| D9 | INFERENCE 단서 조사는 400 `TARGET_NOT_IN_CASE` | api_spec |
| D10 | 행동 타임라인은 종료 세션만 | api_spec, mock |
| D11 | requestId의 세션·행동 종류·대상이 다르면 400 | api_spec, project_context, mock |
| D12 | 잠금 직후 requestId 재조회 | api_spec, project_context, `CLAUDE.md` |
| D13-1 | B1·B4·B5 정의(7.3) | project_context 6장 |
| D13-2, D13-3 | **D26으로 대체:** 검증기는 `is_enabled`를 바꾸지 않고, `validated_at`은 마지막 검증 시각 | |
| D13-4 | NOT_TRAIT은 `is_judgment = TRUE`만 유효 | project_context 6장 |
| D14 | 핵심 단서별로 검사, 핵심 단서 0개면 실패 | project_context 6장 |
| D15 | 추가 검사 A9~A11 | project_context 6장 |
| D16 | FAILED의 0점 처리는 `score_delta`에 넣지 않음 | api_spec |
| D17 | 비공개 사건은 새 시작 차단, 요약은 내 기록이 있으면 포함. 기존 세션 처리는 **D29로 보완** | api_spec, 7.5 |
| D18 | `page` 0 이상, `size` 1~100 | api_spec, mock |
| D19 | 로컬 MySQL `metamong_case_test` + 전용 계정, 통합 테스트는 별도 태스크 | 9.4 |
| D20 | 시연 계정은 가입 API로 생성 | 10.5 |
| D22 | 추론 자동 생성은 12/4~6 | project_context 9장 |
| D23 | 사건 한 줄 소개는 project_context 문구 | `02_sample.sql`, mock, `MainPage` |
| D24 | 현장 이미지 이름은 `case0N_scene.webp` | project_context 9장 |
| D25 | 세션 시작 동시 요청은 기존 세션을 200으로 | api_spec |
| D26 | 공개 상태(`is_enabled`)와 검증 상태(`validation_status`) 분리 | `01_schema.sql`, `02_sample.sql`, project_context, api_spec, 3·4·7장 |
| D27 | 403 `FORBIDDEN` "허용되지 않은 요청입니다." | api_spec, `types.ts`, 5.3 |
| D28 | **b:** 일정 유지, 조건부 임시 UPDATE | 11장 |
| D29 | PENDING·INVALID 사건의 진행 중 세션은 조회만 허용, 행동은 409 `CASE_UNAVAILABLE`. `playable` 필드 없음 | api_spec, project_context, `CLAUDE.md`, `types.ts`, `GamePage`, 4·6·7.5·8장, T32~T33 |

api_spec 3장의 "필드별 공개 규칙"도 확정입니다(4.7·4.15).

참고: project_context의 저장소 경로 `C:\PokeWebgame\`는 현재 경로와 다릅니다. `docs/erd_final.png`에는 `validation_status`가 아직 없으므로 DB 설계서 제출 전에 다시 내보내야 합니다.
