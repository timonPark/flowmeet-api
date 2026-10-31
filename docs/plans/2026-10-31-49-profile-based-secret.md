---
issueKey: "#49"
issueTitle: "[chore] profile-based secret management (jwt.secret + refresh.pepper)"
slug: "profile-based-secret"
author: "ParkJH0514"
createdAt: "2026-10-31 16:25"
chosenApproach: "옵션 B — profile 분리 (local/prod) + base default=local"
verifyLevel: "L1"
status: APPROVED
approvedAt: "2026-10-31 16:28"
---

# Plan — #49 profile-based secret management (jwt.secret + refresh.pepper)

## 1. 목표 (완료 조건)

- base `application.yml`에서 `change-me-...` 공개 기본값 **완전 제거**.
- `SPRING_PROFILES_ACTIVE` 기본값은 `local` — 로컬 개발 흐름(`./gradlew bootRun`) 그대로 유지.
- 운영 profile(`prod`) 활성 시 `JWT_SECRET` / `REFRESH_TOKEN_PEPPER` **환경변수 누락 → 기동 실패** (Spring placeholder 미해결 → `BeanCreationException`).
- CLAUDE.md에 "운영 배포 전 `SPRING_PROFILES_ACTIVE=prod` 필수" 가이드 추가.

## 2. 선택된 접근

**옵션 B — profile 분리 (local/prod) + base default=local**

- base `application.yml`: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` + `jwt.secret` / `refresh-token.pepper` 선언 제거.
- `application-local.yml` (신규): 로컬 전용 default (`local-only-*-32bytes` 명시적 표현).
- `application-prod.yml` (신규): `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` (default 없음).
- `build.gradle.kts` `bootRun`: `SPRING_PROFILES_ACTIVE=local` 기본으로 명시 (안전 네트).
- 테스트/Testcontainers: application-test profile 또는 local profile 재사용으로 context load 가능하게.

## 3. 영향 파일

### 신규
- `src/main/resources/application-local.yml` — 로컬 개발 전용 secret default (명시적 "LOCAL ONLY" 주석).
- `src/main/resources/application-prod.yml` — `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` (default 없음).

### 수정
- `src/main/resources/application.yml` — 공개 default 제거, `spring.profiles.active` default=`local` 지정.
- `build.gradle.kts` — `bootRun` task에 `SPRING_PROFILES_ACTIVE=local` 환경변수 기본 지정.
- `CLAUDE.md` — "환경 변수" 섹션에 profile 가이드 추가 (local 기본 / prod 배포 시 env var 필수).

### 테스트 (검토 후 추가)
- 기존 `JwtPropertiesTest` — 변경 없음 (단위 테스트, properties 직접 생성).
- 신규 context-load 테스트: `prod` profile + env var 없는 상태로 `@SpringBootTest` 로드 시 `BeanCreationException` 발생을 assert — **옵션 (T6에서 판단)**.

## 4. Task

- [ ] **T1.** `application-prod.yml` 신규 작성 — `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` (default 없음), base와 동일한 다른 설정은 상속 그대로.
      · 검증: 파일 작성만. 테스트는 T6에서.
- [ ] **T2.** `application-local.yml` 신규 작성 — 로컬 전용 default (`local-only-dev-...-32bytes`), `cookie-secure: false` 명시.
      · 검증: 파일 작성만.
- [ ] **T3.** `application.yml` 수정 — `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` 추가, `flowmeet.jwt.secret` / `flowmeet.security.refresh-token.pepper` / `flowmeet.security.refresh-token.cookie-secure` 선언 제거.
      · 검증: T4·T5와 함께 L1 통과.
- [ ] **T4.** `build.gradle.kts` 수정 — `tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") { environment("SPRING_PROFILES_ACTIVE", "local") }` 추가.
      · 검증: `./gradlew bootRun --dry-run` 또는 L1에 포함.
- [ ] **T5.** `CLAUDE.md` 환경 변수 섹션 수정 — profile 정책 문단 추가 (local 기본 / prod는 env var 필수, 누락 시 기동 실패).
      · 검증: 문구 리뷰.
- [ ] **T6.** (옵션) prod profile 기동 실패 context-load 테스트 작성 — `@SpringBootTest(properties = ["spring.profiles.active=prod"])` 로 secret env 없이 로드 시 예외 발생 assert.
      · 플랜 단계: 추가 가치 vs 복잡도 고려 후 skip 가능. **기본은 skip** — Spring 기본 동작이 명확하고 수동 검증 가능.
- [ ] **T7.** L1 검증 — `./gradlew ktlintCheck test` 통과.
      · `Verified: L1` 커밋 라인 포함.
- [ ] **T8.** 커밋 (사용자 명시 승인 후).
      · 메시지: `chore(config): split secrets into profile-local/prod (#49)`
- [ ] **T9.** PR 생성 → `_pr-flow` 위임.
      · 본문에 "Closes #49" 포함.

## 5. 검증 계획

- **레벨**: **L1** (`./gradlew ktlintCheck test`).
- **명령**:
  - `./gradlew ktlintCheck` — Kotlin 스타일.
  - `./gradlew test` — 기존 테스트(특히 JwtPropertiesTest·Context 테스트) 그대로 통과.
- **수동 검증 (선택)**:
  - `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` → `BeanCreationException` 로 기동 실패 확인.
  - `./gradlew bootRun` (profile 미지정) → local 기본, 정상 기동.
- **롤백 포인트**: 브랜치 시작 `d759dda` (origin/main).

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**:
  - `application-local.yml`의 default는 **공개 가능한 로컬 전용 값** (명시적으로 "local-only"). 운영 secret은 코드/리포에 저장하지 않음.
  - plan 본문·commit·PR 설명에 실제 운영 secret 노출 금지.
- **관련 규칙**:
  - [security-logging](../.claude/skills/security-logging/SKILL.md) — JWT·pepper 로그 금지.
  - [kotlin-style](../.claude/skills/kotlin-style/SKILL.md) — properties/yml 네이밍.
  - CLAUDE.md "환경 변수" 섹션과 일관성 유지.
- **codex 리뷰 흐름**: PR 생성 후 codex 2차 리뷰 → triage.
