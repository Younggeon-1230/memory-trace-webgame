# 《오박사와 메타몽의 사라진 기억》 프로젝트 공유 문서

이 문서는 Claude와 진행한 기획·설계 결과를 정리한 것입니다. 앞으로 이 내용을 기준으로 함께 프로젝트를 진행하려고 합니다. 확정된 내용은 바꾸지 말고, 바꿔야 한다고 판단되면 이유와 함께 제안해 주세요.

> 변경 이력 (2026-11-28, 백엔드 설계 검토 반영): 6장 검증기(검사 정의·추가 검사·결과 반영), 7장 배제 요청 처리 순서와 결과 코드, 9장 일정(추론 자동 생성 시점). 남은 미정 사항은 backend_design.md 12장.
> 추가 변경 (2026-11-28): 사건의 공개 상태와 검증 상태 분리(6·7장, case_file.validation_status 추가), 현장 이미지 파일명 case0N_scene.webp로 통일(9장).
> 추가 변경 (2026-11-28): 검증되지 않은 사건의 진행 중 세션 처리(6·7장).

함께 첨부하는 파일
- erd_final.png : 최종 E-R 다이어그램
- 01_schema.sql : 테이블 생성 SQL (MariaDB 10.11에서 실행 확인, MySQL 8.0.16 이상 대상)
- 02_sample.sql : CASE 1~3 샘플 데이터 SQL (실행 확인)

---

## 1. 프로젝트 기본 정보

- 수업: 전문대학 Spring 기반 웹 프로그래밍, 기말고사 대체 프로젝트
- 스택: React + Java Spring Boot + MySQL, 외부 API PokéAPI
- 기간: 약 3~4주, 최종 발표·시연
- 교수님 요구사항: 외부 API 활용 풀스택 웹사이트, 회원가입·로그인 필수, 회원별 기록 저장·조회 필수, 참신한 아이디어, 핵심 기능 하나를 완성도 있게
- 평가 핵심(학생 판단): 참신한 아이디어 + 백엔드 DB 연동
- 제출 일정: 기획서 제출 완료(11/23). DB 설계서는 11/30(월)까지 Word 파일로 제출, E-R 다이어그램은 이미지로 삽입
- 최종 발표: 12/14(월). 그 전에 완성
- 발표 당일 시연은 사건 1개만 (CASE 3 추천). 나머지 사건은 다른 학생들이 해볼 수 있게 배포할 예정

### 개발 환경과 폴더 구조
```
C:\PokeWebgame\          ← Git 저장소 최상위, Claude Code는 여기서 실행
├─ docs\                 ← 이 문서, 01_schema.sql, 02_sample.sql, erd_final.png
├─ image\                ← 사건 현장 이미지 원본 (case03_scene.webp 등)
├─ backendWork\          ← Spring Boot 프로젝트 (IntelliJ로 열기, build.gradle이 바로 이 안에)
└─ frontendWork\         ← React + Vite 프로젝트 (VS Code로 열기, package.json이 바로 이 안에)
```
- 게임에서 쓰는 현장 이미지는 frontendWork\public\scenes\ 에 복사해 사용
- Claude Code: Pro 플랜, IntelliJ 플러그인 사용, 기본 모델 Sonnet (복잡한 판정 로직은 Opus)

## 2. 게임 개요

- 장르: 2D 포인트 앤 클릭 추리 웹게임
- 스토리: 오박사 연구소에서 사건이 일어나고, 메타몽은 자신이 마지막으로 무엇으로 변신했는지 기억하지 못한다. 플레이어는 현장 흔적을 조사해 메타몽이 변신했던 포켓몬을 밝혀낸다.
- 공통 전제: 그날 밤 현장에 있던 포켓몬은 변신한 메타몽 하나뿐이고, 사건 당시 한 번만 변신했다. 범인(정답)은 항상 1명이다.
- 핵심 메커니즘: 배제 추리. 플레이어가 (수집한 증거, 용의자)를 골라 "이 증거로 이 용의자는 불가능하다"를 제출하고 서버가 판정한다.
- 사건 3개, 사건당 용의자 4마리, 1~3세대 포켓몬(001~386)만 사용
- 두 번째 외부 API는 보류 (PokéAPI 단독)

## 3. 게임 규칙 (확정)

- 시작 100점, 틀린 배제 -10, 틀린 지목 -30, 점수 하한 0
- 지목 최대 2회. 틀린 지목 대상은 ELIMINATED_BY_WRONG_ACCUSATION으로 자동 제외
- 오답 3명을 모두 증거로 배제(EXCLUDED_BY_EVIDENCE)한 뒤 정답 지목 시 +10 (틀린 지목으로 제외된 용의자는 보너스 조건에 포함하지 않음)
- 등급: S = 감점 없이 전원 배제 후 정답 / A = 정답 + 80점 이상 / B = 정답 + 80점 미만 / 실패 = 지목 2회 모두 틀림, 점수 0으로 기록
- 다시 하기 허용, 이전 기록은 모두 보존. 진행 중 세션은 회원·사건당 1개
- 정답과 판정 규칙은 서버에서만 관리, 점수 계산도 서버에서
- 흔적 사전 = 플레이어에게 공개된 판정 규칙. 서버 판정과 공개 규칙이 항상 같아야 함
- 미끼 단서는 미끼라고 표시하지 않음. 규칙이 없고, 다른 원인을 보여 주는 증거로 설명됨
- 추론 증거: 선행 단서를 모두 모으면 서버가 자동으로 수첩에 추가
- PokéAPI 데이터는 개발 단계에서 DB에 캐시, 이미지도 서버에 저장. 게임 실행 중 외부 API 호출 없음

## 4. 화면 설계 (확정)

- 전체 흐름: 메인 → 로그인/회원가입 → 사건 목록 → 게임 화면(브리핑·조사·추리·지목·결과 모두 한 화면) → 마이페이지
- 게임 화면: 고정 사이드 패널 레이아웃. 상단 바(사건명, 점수, 남은 지목, 지목 버튼) / 왼쪽 사건 현장 이미지와 클릭 영역 / 오른쪽 증거 수첩·흔적 사전 탭 / 하단 용의자 카드 4장
- 용의자 카드: 이미지, 이름, 배제 상태, 파일 버튼. 키·몸무게·타입·색상·특징은 용의자 파일 팝업에서 확인
- 배제 조작: 수첩에서 증거 선택 → 용의자 카드의 배제 버튼 → 확인 창 (드래그 앤 드롭은 확장)
- 클릭 영역은 이미지 기준 0~1 비율 좌표로 저장, 사건 이미지는 같은 비율로 제작
- 결과 팝업: 정답 → 진상 → 변신 동기 → 반전 → 점수 내역 → 등급
- 마이페이지: 사건별 최고 점수, 전체 플레이 기록(실패 포함). 전체 회원 랭킹은 MVP에서 제외

## 5. 사건 설계 (최종 확정)

판정 규칙이 "없음"인 단서는 판정에 쓰이지 않는 오해 유도·추론 재료·보조 단서다.

### CASE 1 — 자정의 연구소
정답 고라파덕(54) / 용의자 고우스트(93), 윤겔라(64), 무우마(200), 고라파덕(54) / 동기: 호기심

진상: 오박사가 아끼는 녹음기가 궁금했던 메타몽이 몰래 꺼내 보다가, 붙어 있던 사진 속 고라파덕(오박사의 옛 파트너)으로 변신했다. 녹음기를 낮은 배수 점검로에 떨어뜨려 찾으러 들어갔는데, 경보음에 두통을 느끼며 의도치 않은 힘이 터져 기록실 물건들이 튀었다.

| ID | 단서 | 종류 | 선행 | 규칙 | 핵심 |
|---|---|---|---|---|---|
| E1 | 날아간 금속 트레이 | 현장 | | 없음 | |
| E2 | 영체 반응 없음 [사건설정: 감식 장비] | 현장 | | NOT_TYPE ghost | |
| E3 | 경보음 기록 (01:17:41 경보, 01:17:42 진동 반복) | 현장 | | 없음 | |
| E4 | CCTV: 점검로 입구를 중심으로 바깥쪽으로 동시에 튐 | 현장 | | 없음 | |
| E5 | 점검로 입구 높이 1.0m | 현장 | | 없음 | |
| E6 | 점검로 바닥 압력 센서: 01:17:41 내부 압력 감지 (수치 비표시) | 현장 | | 없음 | |
| E7 | 점검로 안의 젖은 자국 | 현장 | | 없음 (보조) | |
| E8 | 젖은 녹음기와 사진 | 현장 | | 없음 (동기) | |
| I1 | 사건 순간 변신체는 높이 1.0m 점검로 안에 있었다 | 추론 | E3,E4,E5,E6 | MAX_HEIGHT 10 | O |

배제: 고우스트 E2·I1 / 무우마 E2 / 윤겔라 I1

### CASE 2 — 불타버린 실험실
정답 브케인(155) / 용의자 꼬부기(7), 코일(81), 파이리(4), 브케인(155) / 동기: 선의·보호

진상: 보온함 전원이 꺼지자 메타몽은 알을 데우려고 변신했다. 꼬리 불꽃이 늘 켜진 파이리 대신 평소 불꽃이 꺼져 있는 브케인을 골랐다. 전원 자동 복구 때 노후 릴레이에서 아크와 큰 소리가 나자 놀라 등에서 불꽃이 솟았고 단열포에 옮겨 붙었다. 이후 소화 장치가 물을 뿌렸다.

| ID | 단서 | 종류 | 선행 | 규칙 | 핵심 |
|---|---|---|---|---|---|
| E1 | 바닥의 물 | 현장 | | 없음 (함정) | |
| E2 | 소화 장치 작동 02:13:35 (화염 감지 이후) | 현장 | | 없음 | |
| E3 | 배전반 아크 흔적 | 현장 | | 없음 | |
| E4 | 점검 로그: 릴레이 오류, 자동 재투입 실패, 보온함 전원 계속 꺼짐 | 현장 | | 없음 | |
| E5 | 최초 발화: 단열포. 전기 설비·화학물질·발열 장치·기계적 발화 등 다른 점화원 없음 | 현장 | | 없음 | |
| E6 | 단열포: 서서히 그을린 흔적 없이 순간 착화 [사건설정] | 현장 | | 없음 | |
| E7 | 단열포 쪽 외벽 온도 센서: 02:12:40부터 약 25초 낮은 열, 02:13:08 순간 고온 | 현장 | | 없음 | |
| I0 | 물은 화재 이후 소화 장치에서 생겼다 | 추론 | E1,E2 | 없음 (설명) | |
| I1 | 불은 포켓몬의 불꽃에서 시작됐다 | 추론 | E3,E4,E5 | HAS_TYPE fire | O |
| I2 | 단열포 옆에 머문 범인에게 계속 타는 불꽃은 없었다 | 추론 | I1,E6,E7 | NOT_TRAIT CONSTANT_TAIL_FLAME | O |

배제: 꼬부기·코일 I1 / 파이리 I2

### CASE 3 — 들어간 발자국만 있는 창고
정답 이브이(133) / 용의자 이브이(133), 니드런♀(29), 지그제구리(263), 깨비참(21) / 동기: 동경·정체성

진상: 진화하지 않는 메타몽은 진짜로 달라질 수 있는지 알고 싶어 진화의 상징인 이브이로 변신해 진화의 돌을 만져 봤다. 아무 일도 없었고, 들어온 경로를 그대로 되짚어 나갔다. 결말: 상자 아래 오박사의 연구 노트 "메타몽은 진화하지 않는다. 그러나 무엇이든 될 수 있다는 것 역시 이 아이만의 가능성이다."

| ID | 단서 | 종류 | 선행 | 규칙 | 핵심 |
|---|---|---|---|---|---|
| E1 | 네 발 보행 흔적 | 현장 | | SHAPE_IS quadruped | |
| E2 | 출구 흔적 없음 (지붕창·환기구 먼지 그대로) | 현장 | | 없음 | |
| E3 | 크기에 비해 깊은 발자국 | 현장 | | 없음 (함정) | |
| E4 | 겹친 압흔 | 현장 | | 없음 | |
| E5 | 열린 상자 경첩의 갈색 체표 잔류물 [도감 대표 색 기반] | 현장 | | COLOR_IS brown | |
| I1 | 되짚어 나간 경로, 보정 감식 결과 10kg 이하 | 추론 | E2,E4 | MAX_WEIGHT 100 | O |

배제: 깨비참 E1 / 지그제구리 I1 / 니드런♀ E5

### 세 사건 비교
| | CASE 1 | CASE 2 | CASE 3 |
|---|---|---|---|
| 핵심 추리 | 초자연 현상의 발생 위치 | 화재 원인과 화염 형태 | 발자국 재해석 |
| 판정 흐름 | NOT_TYPE → MAX_HEIGHT | HAS_TYPE → NOT_TRAIT | SHAPE_IS → MAX_WEIGHT → COLOR_IS |
| 동기 | 호기심 | 선의·보호 | 동경·정체성 |

### 사건 설계 원칙 (확정)
- 정답만 가진 고유 특징을 판정 규칙으로 쓰지 않는다. 특징 규칙은 오답 쪽 특징을 NOT_TRAIT으로 판정한다.
- 고라파덕의 두통, 브케인의 놀람 화염은 판정용이 아니라 용의자 파일·결말 설명용이다.
- CASE_FACT(사건별 배제 대상 직접 지정)는 쓰지 않는다.
- 배제표는 사람이 쓰지 않고 자동 검증기가 규칙으로 계산한다.
- 특징 출처는 PokéAPI pokemon-species.flavor_text_entries의 본편 도감 문구를 우선한다.

## 6. 사건 검증기 (확정)

서버 시작 시(요청을 받기 전)와 데이터 동기화 직후 실행.

공개 상태와 검증 상태
- is_enabled: 사람이 정하는 공개 여부. 검증기는 바꾸지 않음
- validation_status: PENDING(검증 전, 기본값) / VALID / INVALID. 검증기가 기록
- validated_at: 마지막 검증 시각 (VALID·INVALID 모두)
- 사건 목록 노출과 새 수사 시작 조건: is_enabled = TRUE 이고 validation_status = VALID
- 예상된 데이터 위반은 INVALID. 검증 중 예상하지 못한 예외가 나면 어떤 사건의 상태도 바꾸지 않고 서버 시작을 실패시킴
- 진행 중 세션: 사람이 비공개한 VALID 사건은 계속 플레이 가능. PENDING·INVALID 사건은 조회만 가능하고 조사·배제·지목은 409 CASE_UNAVAILABLE(구체적인 검증 상태는 알리지 않음). 종료 세션의 결과·기록은 항상 허용. 다시 VALID가 되면 진행 재개

A. 데이터 무결성 (먼저 실행)
1. 용의자 정확히 4명
2. 정답 포켓몬이 용의자에 포함
3. 선행 조건 두 단서가 같은 사건
4. 선행 조건 자기 참조 없음
5. 선행 조건 순환 없음 (DAG)
6. SCENE 단서는 클릭 좌표 존재
7. INFERENCE 단서는 클릭 좌표 NULL
8. condition_type·condition_value 유효 (NOT_TRAIT은 is_judgment = TRUE인 특징만 유효)
9. INFERENCE 단서는 선행 조건 1개 이상
10. SCENE 단서는 선행 조건 없음
11. 클릭 영역이 이미지 안에 있음 (area_x + area_w ≤ 1, area_y + area_h ≤ 1)

B. 사건 논리 (A 통과 사건만)
1. 정답 유일성: 모든 규칙을 적용하면 남는 용의자가 정답 1명
2. 단일 단서 결정 금지 (단서 하나로 오답 3명 동시 배제 금지)
3. 오답마다 배제 근거 존재
4. 정답 무결성: 어떤 규칙도 정답을 배제하지 않음
5. 미끼·보조 단서 일관성: 규칙이 없는 단서는 is_core = FALSE
6. 핵심 추리 필수성: is_core 단서마다, 그 단서와 그 단서에 의존하는 추론을 함께 제거하면 용의자 2명 이상 남아야 함. is_core 단서가 없는 사건은 실패

## 7. DB 설계 (최종 확정, 12개 테이블)

MySQL 8.0.16 이상, utf8mb4, InnoDB. 모든 테이블 단일 대리 PK, 연결 테이블 중복은 UNIQUE로 방지. 상세 컬럼·제약은 01_schema.sql 참고.

| 영역 | 테이블 | 주요 컬럼 |
|---|---|---|
| 회원 | users | user_id PK, login_id UK, password_hash(BCrypt), nickname, role(USER/ADMIN), created_at |
| 포켓몬 | pokemon | pokemon_id PK(전국도감 번호), name_en UK, name_ko, height_dm, weight_hg, type1, type2, color, shape, sprite_path, synced_at |
| 포켓몬 | pokemon_trait | trait_id PK, pokemon_id FK, trait_code, description, source_type(POKEDEX/CASE_RULE), source_text, source_version, is_judgment / UK(pokemon_id, trait_code) |
| 사건 | case_file | case_id PK, case_code UK, title, subtitle, briefing, scene_image_path, answer_pokemon_id FK→pokemon, truth_text, motive_text, twist_text, display_order, is_enabled, validation_status(PENDING/VALID/INVALID), validated_at |
| 사건 | case_suspect | case_suspect_id PK, case_id FK, pokemon_id FK, suspicion_text, display_order / UK(case_id, pokemon_id) |
| 사건 | evidence_rule | rule_id PK, condition_type(7종), condition_value, basis_text(공개 판정 기준), source_type(API/POKEDEX/CASE_RULE), source_ref |
| 사건 | clue | clue_id PK, case_id FK, clue_code, clue_kind(SCENE/INFERENCE), name, summary, detail_text, rebuttal_text, rule_id FK NULL, is_core, area_x/y/w/h, display_order / UK(case_id, clue_code) |
| 사건 | clue_prerequisite | prerequisite_id PK, clue_id FK, required_clue_id FK / UK(clue_id, required_clue_id), CHECK 자기참조 금지 |
| 플레이 | play_session | session_id PK, user_id FK, case_id FK, status(IN_PROGRESS/SOLVED/FAILED), score, wrong_exclusion_count, accusation_count(0~2), bonus_awarded, grade(S/A/B), active_flag(진행 중 1, 종료 NULL), started_at, ended_at / UK(user_id, case_id, active_flag) |
| 플레이 | session_clue | session_clue_id PK, session_id FK, clue_id FK, collected_at / UK(session_id, clue_id) |
| 플레이 | session_suspect | session_suspect_id PK, session_id FK, case_suspect_id FK, status(ACTIVE/EXCLUDED_BY_EVIDENCE/ELIMINATED_BY_WRONG_ACCUSATION), basis_clue_id FK, updated_at / UK(session_id, case_suspect_id) |
| 플레이 | session_action | action_id PK, session_id FK, action_type(INVESTIGATE/INFER/EXCLUDE/ACCUSE), clue_id FK, case_suspect_id FK, result(SUCCESS/FAIL/REJECTED), score_delta, dedup_key, request_id UK, created_at / UK(session_id, dedup_key) |

조건 유형 7종: HAS_TYPE, NOT_TYPE, MAX_HEIGHT, MAX_WEIGHT, SHAPE_IS, COLOR_IS, NOT_TRAIT

### DB 관련 확정 결정
- 세션 영역은 pokemon이 아니라 case_suspect를 참조한다.
- case_file.answer_pokemon_id는 pokemon 참조 + 검증기로 용의자 포함 여부 확인 (case_suspect 참조로 바꾸면 순환 참조가 생김)
- type1·type2 컬럼 방식 유지, 행동 기록은 session_action 하나로 통합
- result: SUCCESS(정상 성공) / FAIL(정상 요청이지만 추리 틀림, 감점) / REJECTED(규칙상 처리 불가, 감점 없음)
- REJECTED 행은 dedup_key = NULL, score_delta = 0 (CHECK로 강제)
- dedup_key는 SUCCESS·FAIL 게임 행동에만 사용. 형식 INV:단서ID, INF:단서ID, EXC:단서ID:사건용의자ID, ACC:지목회차
- request_id는 NULL 허용. 클라이언트 요청은 Service에서 필수 검사, 서버 자동 INFER는 NULL (CHECK로 강제)
- 세션과 단서·용의자의 사건 일치는 Spring Service에서 검사
- 동시 요청은 play_session 행을 SELECT ... FOR UPDATE로 잠가 순서대로 처리
- CHECK: 고정값 컬럼 허용값, 점수·횟수 범위, 클릭 좌표 0~1, SCENE/INFERENCE별 좌표 유무, 세션 상태와 active_flag·ended_at 일관성, 증거 배제 시 basis_clue_id 필수

### 판정 계산 (용의자가 이 경우면 배제 성공)
| 조건 | 배제 조건 |
|---|---|
| HAS_TYPE | type1, type2 모두 값과 다름 |
| NOT_TYPE | type1 또는 type2가 값과 같음 |
| MAX_HEIGHT | height_dm > 값 |
| MAX_WEIGHT | weight_hg > 값 |
| SHAPE_IS | shape ≠ 값 |
| COLOR_IS | color ≠ 값 |
| NOT_TRAIT | pokemon_trait에 해당 trait_code 있음 |

### 배제 요청 처리 순서
1. request_id 중복 확인 (처리된 요청이면 저장된 결과 반환)
2. play_session FOR UPDATE, 잠금 직후 같은 request_id를 다시 확인
3. 세션 소유자 확인
4. 세션 상태 IN_PROGRESS 확인
   - 이어서 사건의 validation_status가 VALID인지 확인 (아니면 409 CASE_UNAVAILABLE, 기록 없음)
5. 단서·용의자가 세션과 같은 사건인지 확인
6. 단서 수집 여부, 용의자 ACTIVE 확인
7. dedup_key 확인
8. 규칙 판정 (규칙 없으면 FAIL + 반박 문구)
9. 점수·session_suspect 갱신
10. session_action 기록
11. commit
- 3~5에서 걸리면 4xx 오류로 응답하고 session_action에 기록하지 않음
- 6~7에서 걸리면 REJECTED로 기록, 감점 없음
- 저장된 결과를 반환할 때도 세션 소유자를 확인하고, 저장된 행동의 세션·행동 종류·대상 ID가 요청과 같아야 함 (다르면 400)

## 8. PokéAPI 값 검증 결과 (2026-11-24 완료)

PokéAPI 원본 데이터 저장소(PokeAPI/api-data)에서 12마리를 확인했다. 02_sample.sql은 이 값으로 갱신 완료.

| id | 이름 | height_dm | weight_hg | types | color | shape |
|---|---|---|---|---|---|---|
| 54 | 고라파덕 | 8 | 196 | water | yellow | upright |
| 64 | 윤겔라 | 13 | 565 | psychic | brown | upright |
| 93 | 고우스트 | 16 | 1 | ghost/poison | purple | arms |
| 200 | 무우마 | 7 | 10 | ghost | gray | ball |
| 7 | 꼬부기 | 5 | 90 | water | blue | upright |
| 81 | 코일 | 3 | 60 | electric/steel | gray | arms |
| 4 | 파이리 | 6 | 85 | fire | red | upright |
| 155 | 브케인 | 5 | 79 | fire | yellow | humanoid |
| 133 | 이브이 | 3 | 65 | normal | brown | quadruped |
| 29 | 니드런♀ | 4 | 70 | poison | blue | quadruped |
| 263 | 지그제구리 | 4 | 175 | normal | brown | quadruped |
| 21 | 깨비참 | 3 | 20 | normal/flying | brown | wings |

세 사건의 판정 결과는 예상과 같다. 처음 예상과 다른 값은 브케인 shape(humanoid) 하나이며 판정에는 영향 없음.

도감 근거 (pokemon-species.flavor_text_entries, 한국어)
- 파이리 CONSTANT_TAIL_FLAME (판정용, 버전 y): "태어날 때부터 꼬리의 불꽃이 타오르고 있다. 불꽃이 꺼지면 그 생명이 다하고 만다."
- 고라파덕 HEADACHE_SURGE (설명용, 버전 x): "항상 두통에 시달리고 있다. 이 두통이 심해지면 이상한 힘을 쓰기 시작한다." / 버전 brilliant-diamond: "그때의 일은 기억하지 못한다." (메타몽의 기억 상실 설정과 연결 가능)
- 브케인 STARTLE_FLARE (설명용, 버전 scarlet): "평소에는 몸을 웅크리고 있다. 화가 났을 때와 놀랐을 때 등에서 불꽃을 뿜어낸다."

## 9. 개발 일정 (발표 12/14 기준)

원칙: CASE 3 하나를 처음부터 끝까지 완성한 뒤, CASE 1·2는 현장 이미지만 추가한다. 기능은 화면과 서버를 하나씩 끝까지 연결하며 진행한다. 사건 검증기는 판정 기능과 함께 만들고, 처음부터 세 사건 데이터 전체에 돌려 7종 판정 조건을 모두 확인한다.

| 날짜 | 할 일 | 완료 기준 |
|---|---|---|
| 11/25~11/30 | DB 설계서 제출(11/30), Spring·React·MySQL 구축, SQL 실행, 12마리 PokéAPI 값 검증(샘플 SQL·설계서 수정), REST API 명세 | DB 설계서 제출 + 백·프론트 실행 |
| 12/1~12/3 | 회원가입·로그인·인가, 사건 목록, 세션 생성·재개 | 로그인 → 사건 시작 가능 |
| 12/4~12/6 | CASE 3 게임 화면, 현장 조사, 추론 자동 생성, 증거 수첩, 용의자 파일 | CASE 3 단서 수집 가능 (추론 포함) |
| 12/7~12/9 | 7종 판정기, 배제·지목·점수·등급, 사건 검증기(세 사건 데이터 전체 대상) | CASE 3 완주 + 세 사건 검증 통과 (1차 개발 마감) |
| 12/10 | 마이페이지·기록 조회, CASE 3 집중 QA, 테스트 배포 | 배포 환경에서 CASE 3 완주 |
| 12/11 | CASE 1·2 현장 이미지·클릭 좌표 적용 | 세 사건 모두 실행 가능 |
| 12/12 | 전체 QA (회원별 저장, 재접속, 중복 요청, 점수) | 기능 동결 |
| 12/13 | 발표 자료, 발표용 계정·데이터, 시연 리허설, 백업 영상 | 발표 준비 완료 |
| 12/14 | 발표 (CASE 3 시연) | |

- 현장 이미지 3장은 12/4 이후 저녁 시간에 미리 준비한다 (AI 토큰 불필요).
- [완료 11/25] CASE 3 현장 이미지 확정: case03_scene.webp, 세 사건 모두 case0N_scene.webp (1920×1080, 16:9). 클릭 좌표는 02_sample.sql에 반영 (E1 0.514/0.638/0.150/0.133, E2 0.338/0.000/0.232/0.160, E3 0.167/0.887/0.063/0.085, E4 0.434/0.776/0.055/0.064, E5 0.769/0.383/0.107/0.157). 이미지 규칙: 16:9, 포켓몬·실루엣 없음, 공식 상표 문양 최소화, 게임용은 WebP
- 사건 목록 한 줄 소개: 자정의 연구소 — 초자연 현상의 정체를 밝혀라 / 불타버린 실험실 — 화재의 시작점을 추적하라 / 들어간 발자국만 있는 창고 — 남겨진 발자국의 모순을 풀어라
- 늦어질 때 우선순위: CASE 3 완성도 > 회원별 기록 > 배포 안정성 > CASE 1·2 > 시각적 꾸밈. 회원 기능, 배제 판정, 기록 저장은 필수.
