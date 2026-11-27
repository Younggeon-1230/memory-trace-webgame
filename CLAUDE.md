# Memory Trace — Claude Code 작업 지침

## 먼저 읽을 문서
- `docs/project_context.md` — 게임 규칙, 사건 3개, 검증기, DB 결정사항 (기준 문서)
- `docs/api_spec.md` — REST API 명세 ([제안] 표시는 아직 검토 중)
- `docs/01_schema.sql`, `docs/02_sample.sql` — DB 스키마와 샘플 데이터 (DB 이름 `metamong_case`)
- 문서와 코드가 다르면 임의로 고치지 말고 먼저 알려 줄 것

## 구조
- `frontendWork/` — React 19 + TypeScript + Vite + React Router (VS Code). `npm run dev`, `npm run build`, `npm run lint`
  - `src/api/types.ts` 응답 타입, `src/api/http.ts` 실제 서버 호출, `src/api/mock/server.ts` 임시 서버(명세·규칙 그대로 구현)
  - `VITE_USE_MOCK=false`면 실제 서버 사용. 백엔드 API를 바꾸면 types.ts와 api_spec.md도 같이 고칠 것
- `backendWork/` — Spring Boot, Gradle, Java 17+ (IntelliJ). `build.gradle`이 이 폴더 바로 안에 있음
- `image/` — 현장 이미지 원본. 게임용은 `frontendWork/public/scenes/`에 복사

## 반드시 지킬 규칙
- 정답·판정 규칙·점수 계산은 서버에서만. 프론트에 정답, `rule_id`, `is_core`, `truth_text`를 보내지 않는다
- 판정은 DB 정수값(height_dm, weight_hg)으로만. 표시용 m·kg 변환은 응답 DTO에서
- 게임 중 PokéAPI를 호출하지 않는다 (DB 캐시만 사용)
- `spring.jpa.hibernate.ddl-auto=validate` — 테이블은 SQL 파일로만 만든다. 엔티티는 스키마에 맞출 것
- 게임 행동 처리 순서: requestId 중복 확인 → play_session `FOR UPDATE` → 소유자 → IN_PROGRESS → 같은 사건 → 수집·ACTIVE → dedup_key → 판정 → 갱신 → session_action → commit
- REJECTED 행은 dedup_key NULL, score_delta 0. 서버 자동 INFER는 request_id NULL
- 비밀번호·DB 계정은 `application-local.yml`에 (gitignore 되어 있음). 커밋하지 않는다

## 작업 방식
- 한 번에 기능 하나씩. 큰 변경 전에는 계획을 먼저 보여 줄 것
- 판정기·검증기는 단위 테스트를 함께 작성 (CASE 1~3 데이터로 정답이 유일하게 남는지)
- 커밋 메시지: `feat: …`, `fix: …`, `docs: …` 형식, 한국어 설명 가능
