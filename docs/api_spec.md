# REST API 명세 (초안 v0.2)

> v0.2 (11/27): 프론트 화면 구현에 맞춰 결과·기록 응답에 caseId/caseCode/title, 틀린 배제 수, 조사한 단서 수 필드 추가.
> 프론트의 임시 서버 `frontendWork/src/api/mock/server.ts`가 이 명세를 그대로 구현하고 있으니 Spring 구현 때 참고.

> 기준 문서: `docs/project_context.md`, `docs/01_schema.sql`
> 상태 표시: **[확정]** 이미 합의된 규칙을 API로 옮긴 것 / **[제안]** 이번에 새로 정한 것, 검토 필요

## 0. 공통 규칙

- Base URL: `/api` (개발 시 Vite proxy `/api → http://localhost:8080`)
- 형식: JSON, UTF-8. 필드 이름은 camelCase
- 인증: Spring Security 세션 (쿠키 `JSESSIONID`). 비밀번호는 BCrypt **[확정]**
- 로그인 필요 API에 비로그인 요청 → `401`
- 게임 행동 요청(조사·배제·지목)은 `requestId`(UUID v4, 클라이언트 생성) 필수 **[확정]**
  - 같은 `requestId`가 다시 오면 처리하지 않고 저장된 결과를 그대로 반환 (네트워크 재전송 대비)
- CSRF: 개발 단계에서는 `/api/**` 비활성화, 배포 전 쿠키 기반 CSRF 토큰 적용 여부 결정 **[제안]**

### 오류 응답 형식 **[제안]**

```json
{ "code": "SESSION_ENDED", "message": "이미 종료된 수사입니다." }
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 입력 형식 오류 (빈 값, 길이 초과, requestId 누락 등) |
| 400 | `TARGET_NOT_IN_CASE` | 단서·용의자가 세션의 사건에 속하지 않음 |
| 401 | `UNAUTHORIZED` | 로그인 필요 / 로그인 실패 |
| 403 | `NOT_SESSION_OWNER` | 다른 회원의 세션 |
| 404 | `NOT_FOUND` | 사건·세션 없음, 비활성(is_enabled=FALSE) 사건 |
| 409 | `LOGIN_ID_TAKEN` | 아이디 중복 |
| 409 | `SESSION_ENDED` | 세션이 IN_PROGRESS가 아님 |

### 게임 행동 결과 3종 **[확정]**

| result | 의미 | 감점 | 기록 |
|---|---|---|---|
| `SUCCESS` | 정상 처리, 추리 맞음 | 없음 | dedup_key 저장 |
| `FAIL` | 정상 요청이지만 추리 틀림 | 배제 -10 / 지목 -30 | dedup_key 저장 |
| `REJECTED` | 규칙상 처리 불가 (미수집 단서, ACTIVE 아닌 용의자, 이미 한 행동) | 없음 | dedup_key NULL |

REJECTED는 오류가 아니라 **200 응답 + `result: "REJECTED"`** 로 돌려준다.

> **[제안] 기존 결정과 다른 부분:** 기존 처리 순서에서는 3~7단계 실패를 모두 REJECTED로 기록했다.
> 이 명세에서는 **3~5단계(소유자·상태·사건 불일치)는 4xx 오류로 응답하고 session_action에 기록하지 않으며**,
> **6~7단계(미수집 단서·ACTIVE 아닌 용의자·중복 행동)만 REJECTED로 기록**한다.
> 이유: 다른 회원이 보낸 요청이 피해자 세션의 행동 기록에 남는 것을 막고, 프론트가 오류와 게임상 거절을 구분하게 하기 위함.

---

## 1. 인증

### `POST /api/auth/signup` — 회원가입

```json
// 요청
{ "loginId": "detective01", "password": "pass1234!", "nickname": "꼬마탐정" }
// 201 응답
{ "userId": 2, "loginId": "detective01", "nickname": "꼬마탐정", "role": "USER" }
```

검증 **[제안]**: loginId 4~30자 영문 소문자·숫자·`_`, password 8~64자, nickname 2~20자. 가입 후 자동 로그인하지 않음.

### `POST /api/auth/login` — 로그인

요청 `{ "loginId", "password" }` → `200` 사용자 정보 (위와 같음) + 세션 쿠키 발급. 실패 시 `401` (아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않음).

### `POST /api/auth/logout` — 로그아웃 → `204`

### `GET /api/auth/me` — 현재 로그인 사용자

`200` 사용자 정보 / `401`. 프론트는 첫 화면 진입 시 이 API로 로그인 상태를 확인한다.

---

## 2. 사건

### `GET /api/cases` — 사건 목록 (로그인 필요)

`is_enabled = TRUE`인 사건만, `display_order` 순서. 정답·진상 정보는 포함하지 않는다.

```json
[
  {
    "caseId": 3,
    "caseCode": "CASE03",
    "title": "들어간 발자국만 있는 창고",
    "subtitle": "남겨진 발자국의 모순을 풀어라",
    "sceneImagePath": "/scenes/case03_scene.webp",
    "myStatus": {
      "inProgressSessionId": null,
      "playCount": 1,
      "bestGrade": "A",
      "bestScore": 100
    }
  }
]
```

---

## 3. 플레이 세션

### `POST /api/cases/{caseId}/sessions` — 수사 시작 또는 이어하기

- 해당 회원·사건의 IN_PROGRESS 세션이 있으면 그 세션을 반환 → `200`
- 없으면 새로 만들고 (score 100, 용의자 4명 모두 ACTIVE로 session_suspect 생성) → `201`
- 동시에 두 번 눌러도 UNIQUE(user_id, case_id, active_flag)가 막아 주므로, 중복 키 예외가 나면 기존 세션을 다시 조회해 `200`으로 반환 **[제안]**
- 응답 본문은 아래 **게임 상태(GameState)**

### `GET /api/sessions/{sessionId}` — 게임 상태 조회

새로고침·재접속 시 사용. 본인 세션만 (`403`). 종료된 세션도 조회 가능 (결과 화면 재진입용).

### 게임 상태 (GameState) 응답 구조

```json
{
  "sessionId": 15,
  "status": "IN_PROGRESS",
  "score": 90,
  "accusationsLeft": 2,
  "wrongExclusionCount": 1,
  "case": {
    "caseId": 3, "caseCode": "CASE03",
    "title": "들어간 발자국만 있는 창고",
    "briefing": "…",
    "sceneImagePath": "/scenes/case03_scene.webp"
  },
  "sceneAreas": [
    { "clueId": 20, "x": 0.514, "y": 0.638, "w": 0.150, "h": 0.133, "collected": true },
    { "clueId": 21, "x": 0.338, "y": 0.000, "w": 0.232, "h": 0.160, "collected": false }
  ],
  "clues": [
    {
      "clueId": 20, "clueCode": "E1", "kind": "SCENE",
      "name": "네 발 보행 흔적", "summary": "…", "detailText": "…",
      "collectedAt": "2026-12-05T14:03:11"
    }
  ],
  "traceDictionary": [
    { "basisText": "…" }
  ],
  "suspects": [
    {
      "caseSuspectId": 9,
      "pokemonId": 21, "nameKo": "깨비참", "spritePath": "/sprites/21.png",
      "heightM": 0.3, "weightKg": 2.0,
      "types": ["normal", "flying"], "color": "brown", "shape": "wings",
      "traits": [ { "traitCode": "…", "description": "…" } ],
      "suspicionText": "…",
      "status": "ACTIVE",
      "basisClueId": null
    }
  ]
}
```

필드별 공개 규칙 **[제안]**

| 필드 | 규칙 |
|---|---|
| `sceneAreas` | SCENE 단서의 클릭 영역만. 미수집 단서는 이름·내용 없이 좌표만 공개 |
| `clues` | **수집한 단서만** (SCENE + 자동 생성된 INFERENCE). `rule_id`, `is_core`, `rebuttal_text`는 절대 보내지 않음 |
| `traceDictionary` | 이 사건 단서들에 연결된 evidence_rule의 `basis_text` 목록. **어느 단서에 어떤 규칙이 붙었는지는 보내지 않음** (규칙 없는 함정 단서가 드러나지 않도록) |
| `suspects.traits` | `is_judgment = TRUE`인 특징만 |
| `heightM`, `weightKg` | DB의 dm·hg를 ÷10 해서 표시용으로 변환. 판정은 서버에서 정수 dm·hg로만 |
| 정답, `truth_text` 등 | 진행 중에는 절대 포함하지 않음 → 결과 API에서만 |

---

## 4. 게임 행동

모든 행동 API 공통:
- 처리 순서 **[확정]**: requestId 중복 확인 → `play_session` `SELECT … FOR UPDATE` → 소유자 → IN_PROGRESS → 같은 사건 → 수집·ACTIVE 여부 → dedup_key → 판정 → 갱신 → session_action 기록 → commit
- 응답 공통 필드: `result`, `scoreDelta`, `message`, 그리고 갱신된 `state`(GameState 전체) **[제안]**
  - 매번 전체 상태를 돌려주면 프론트가 부분 갱신 로직 없이 화면을 다시 그리기만 하면 됨

### `POST /api/sessions/{sessionId}/investigations` — 현장 조사

```json
// 요청
{ "requestId": "8f1c…", "clueId": 22 }
// 200 응답
{
  "result": "SUCCESS",
  "scoreDelta": 0,
  "message": "새 단서를 수첩에 기록했습니다.",
  "newClueIds": [22],
  "inferredClueIds": [25],
  "state": { "…": "GameState" }
}
```

- SCENE 단서만 가능 (INFERENCE 단서 id → `400 TARGET_NOT_IN_CASE`와 같은 취급 **[제안]**)
- 이미 수집한 단서 → `REJECTED`
- 수집 직후 서버가 선행 조건을 검사해, 조건이 다 갖춰진 INFERENCE 단서를 자동 수집
  - 자동 수집마다 session_action에 `INFER` / `SUCCESS` / `dedup_key = INF:단서ID` / `request_id = NULL` 기록 **[확정]**
  - 추론이 또 다른 추론의 선행 조건이면 더 생기지 않을 때까지 반복 (CASE 2의 I1 → I2)

### `POST /api/sessions/{sessionId}/exclusions` — 증거로 용의자 배제

```json
// 요청
{ "requestId": "3b7a…", "clueId": 25, "caseSuspectId": 11 }
// 200 응답 (성공)
{
  "result": "SUCCESS", "scoreDelta": 0,
  "message": "지그제구리는 이 증거와 모순됩니다. 배제했습니다.",
  "state": { "…": "GameState" }
}
// 200 응답 (실패)
{
  "result": "FAIL", "scoreDelta": -10,
  "message": "이 증거로는 지그제구리를 배제할 수 없습니다.",
  "rebuttalText": "압흔이 겹쳤는지 확인하지 않은 깊이로는 판단할 수 없네.",
  "state": { "…": "GameState" }
}
```

판정 **[확정]**
- 단서에 규칙이 없음 → `FAIL` (반박 문구가 있으면 `rebuttalText`로 전달)
- 규칙이 있고 용의자가 조건을 **만족하지 못함** → `SUCCESS`, session_suspect를 `EXCLUDED_BY_EVIDENCE` + `basis_clue_id` 저장
- 규칙이 있고 용의자가 조건을 만족함 → `FAIL`, -10, `wrong_exclusion_count + 1`
- 점수는 0 미만으로 내려가지 않음
- dedup_key `EXC:단서ID:사건용의자ID` → 같은 조합을 다시 내면 `REJECTED` (실패한 조합도 다시 감점하지 않음)
- 미수집 단서, ACTIVE 아닌 용의자 → `REJECTED`

### `POST /api/sessions/{sessionId}/accusations` — 최종 지목

```json
// 요청
{ "requestId": "c04d…", "caseSuspectId": 12 }
// 200 응답 (정답)
{
  "result": "SUCCESS", "scoreDelta": 10,
  "message": "메타몽이 변신했던 모습은 이브이였습니다.",
  "bonusAwarded": true,
  "state": { "…": "GameState, status = SOLVED" }
}
```

판정 **[확정]**
- ACTIVE 용의자만 지목 가능 (아니면 `REJECTED`)
- 정답 → `SOLVED`. 오답 3명이 모두 `EXCLUDED_BY_EVIDENCE`이면 보너스 +10 (`bonus_awarded = TRUE`, 이때 `scoreDelta = 10`)
- 오답 → -30, 그 용의자는 `ELIMINATED_BY_WRONG_ACCUSATION`, `accusation_count + 1`
- 오답 2회 → `FAILED`, 점수 0
- 종료 시 `ended_at` 기록, `active_flag = NULL`, 등급 계산
  - S: 감점 없음 + 오답 3명 모두 증거로 배제 / A: 최종 점수 80 이상 / B: 80 미만 / FAILED는 등급 NULL
- dedup_key `ACC:지목회차` (1 또는 2)

### `GET /api/sessions/{sessionId}/result` — 결과 (종료된 세션만)

IN_PROGRESS면 `409 SESSION_ENDED`가 아니라 `409 SESSION_IN_PROGRESS` **[제안]**

```json
{
  "sessionId": 15, "caseId": 3, "caseCode": "CASE03", "title": "들어간 발자국만 있는 창고",
  "status": "SOLVED", "score": 110, "grade": "S",
  "bonusAwarded": true, "wrongExclusionCount": 0, "accusationCount": 1,
  "answer": { "pokemonId": 133, "nameKo": "이브이", "spritePath": "/sprites/133.png" },
  "truthText": "…", "motiveText": "…", "twistText": "…",
  "exclusions": [
    { "caseSuspectId": 9, "nameKo": "깨비참", "status": "EXCLUDED_BY_EVIDENCE", "basisClueName": "네 발 보행 흔적" }
  ],
  "startedAt": "…", "endedAt": "…"
}
```

FAILED 세션도 정답·진상을 공개할지 **[제안: 공개]** — 다시 하기 의욕이 떨어질 수 있으면 비공개로 바꿀 수 있음.

---

## 5. 마이페이지 (회원별 기록)

### `GET /api/me/sessions?caseId=&page=0&size=10` — 내 플레이 기록

최신순. 진행 중·종료 모두 포함.

```json
{
  "content": [
    { "sessionId": 15, "caseCode": "CASE03", "title": "…", "status": "SOLVED",
      "score": 110, "grade": "S", "wrongExclusionCount": 0,
      "collectedClueCount": 5, "totalSceneClueCount": 5,
      "startedAt": "…", "endedAt": "…" }
  ],
  "page": 0, "size": 10, "totalElements": 4
}
```

### `GET /api/me/summary` — 사건별 요약

```json
[
  { "caseId": 3, "caseCode": "CASE03", "title": "들어간 발자국만 있는 창고", "playCount": 4, "solvedCount": 3,
    "bestGrade": "S", "bestScore": 110, "lastPlayedAt": "…" }
]
```

### `GET /api/me/sessions/{sessionId}/actions` — 행동 타임라인

본인 세션만, 종료된 세션만 **[제안]** (진행 중 공개해도 정보 누출은 없지만 화면 단순화를 위해).

```json
[
  { "actionType": "INVESTIGATE", "clueName": "네 발 보행 흔적", "suspectName": null,
    "result": "SUCCESS", "scoreDelta": 0, "createdAt": "…" },
  { "actionType": "EXCLUDE", "clueName": "깊은 발자국", "suspectName": "지그제구리",
    "result": "FAIL", "scoreDelta": -10, "createdAt": "…" }
]
```

발표 포인트: 이 API가 "회원별 기록 저장·조회" 요구사항과 session_action 테이블 설계를 그대로 보여 준다.

---

## 6. API 목록 요약

| 메서드 | 경로 | 설명 | 로그인 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | ✕ |
| POST | `/api/auth/login` | 로그인 | ✕ |
| POST | `/api/auth/logout` | 로그아웃 | ○ |
| GET | `/api/auth/me` | 현재 사용자 | ○ |
| GET | `/api/cases` | 사건 목록 + 내 진행 상태 | ○ |
| POST | `/api/cases/{caseId}/sessions` | 수사 시작/이어하기 | ○ |
| GET | `/api/sessions/{sessionId}` | 게임 상태 | ○ |
| POST | `/api/sessions/{sessionId}/investigations` | 현장 조사 | ○ |
| POST | `/api/sessions/{sessionId}/exclusions` | 증거 배제 | ○ |
| POST | `/api/sessions/{sessionId}/accusations` | 최종 지목 | ○ |
| GET | `/api/sessions/{sessionId}/result` | 결과 | ○ |
| GET | `/api/me/sessions` | 내 기록 목록 | ○ |
| GET | `/api/me/summary` | 사건별 요약 | ○ |
| GET | `/api/me/sessions/{sessionId}/actions` | 행동 타임라인 | ○ |

관리자 API(사건 검증 재실행 등)는 만들지 않는다. 검증기는 서버 시작 시 자동 실행 **[확정]**, 결과는 로그로 확인.

## 7. 검토가 필요한 항목 모음

1. 3~5단계 실패를 REJECTED 기록 대신 4xx 오류로 처리 (위 0장)
2. 모든 행동 응답에 GameState 전체 포함
3. 흔적 사전에 단서–규칙 연결을 노출하지 않음
4. FAILED 세션에서도 정답·진상 공개
5. 회원가입 입력 길이 규칙
6. 배포 전 CSRF 처리 방식
