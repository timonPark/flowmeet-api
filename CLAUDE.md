# CLAUDE.md

조직 지시 및 업무 흐름 관리 플랫폼 API 리포 (`flowmeet-api`). 웹 프론트엔드는 별도 리포 (`../flowmeet-web`).

## 작업 시작 규칙 (필수)

**어떤 작업이든 반드시 전용 브랜치를 만들어 진행한다. `main`에 직접 커밋 금지.**

- 작업 착수 시 가장 먼저 `main`에서 새 브랜치를 분기한다.
- 브랜치 명명 규약: `<type>/<issue-number>-<slug>`
  - `type`: `feature` / `fix` / `refactor` / `chore` / `docs` / `test`
  - 예: `feature/2-harness-skeleton`, `fix/15-login-error`, `docs/8-security-rules`
- 이슈 번호가 없는 작업은 `<type>/<slug>` (예: `chore/update-readme`).
- 작업 완료 후 PR을 생성해 `main`으로 머지 — 직접 푸시 금지.

```bash
# 올바른 착수 흐름
git checkout main && git pull
git checkout -b feature/<issue>-<slug>
# ... 작업 ...
git push -u origin feature/<issue>-<slug>
gh pr create --fill
```

## PR 리뷰·머지 규칙 (필수)

**어떤 PR도 사용자의 명시적 승인 없이 머지하지 않는다.** 자동 머지 금지. 다음 조건을 모두 만족해도 머지 조건은 성립하지 않는다 — 사용자 승인만이 유일한 머지 트리거다.

- ❌ 하지 않는다:
  - PR 생성 직후 자동으로 `gh pr merge` 실행
  - "다음 작업 진행해주세요" 같은 지시를 "앞 PR 머지 + 다음 이슈 착수"로 확대 해석
  - codex 리뷰 통과·`./gradlew build` 통과·CI 그린만으로 머지 결정
  - 이전에 한 번 "머지해줘"라고 승인받았던 것을 다른 PR에까지 재사용

- ✅ 해야 한다:
  - PR 생성 후 **"PR URL + 변경 요약 + 검증 레벨" 을 보고하고 멈춘다**.
  - 사용자가 명시적으로 "머지해줘", "완료해줘" 등을 **그 PR에 대해** 지시했을 때만 `gh pr merge` 실행.
  - 머지 지시가 모호하면(여러 PR이 열려 있거나 범위 불명확) 어떤 PR인지 확인 질문.
  - 머지 후에는 이슈·에픽 체크박스 업데이트까지만 수행하고 다음 이슈 착수는 별도 지시 대기.

> 이 규칙은 2026-10-14 PR #18 자동 머지 사고 이후 명문화됨. `.claude/skills/codex-review-triage/SKILL.md`의 "사람 리뷰 대체 금지" 원칙과 동일 축선.

## 기술 스택

- Kotlin 1.9 + Spring Boot 3.4 + Gradle Kotlin DSL
- Java 21 toolchain
- Spring Data JPA, Spring Security (JWT 기반), Validation, Actuator
- PostgreSQL 16 (docker-compose로 로컬 기동)
- JJWT 0.12 (jsonwebtoken)
- 외부 연동: Deepgram STT, Claude Headless

## 디렉터리 구조

```text
flowmeet-api/
├─ src/main/kotlin/com/flowmeet/   # 애플리케이션 코드 (base package)
├─ src/main/resources/application.yml
├─ src/main/resources/db/migration/V__N__*.sql  # Flyway migration (V1 = schema.sql 복사본)
├─ src/test/kotlin/com/flowmeet/
├─ database/schema.sql             # outputs/schema.sql과 동기화된 DDL (V1__init.sql과도 동일 유지)
├─ docker-compose.yml              # Postgres 16 (schema.sql 자동 적용)
├─ uploads/                        # 로컬 파일 저장소 (TRD §9)
├─ build.gradle.kts
└─ settings.gradle.kts
```

## 자주 쓰는 명령

```bash
# DB 기동 (최초 1회 schema.sql 자동 적용)
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun

# 테스트
./gradlew test

# 코드 스타일 검사 (build 시 자동 포함)
./gradlew ktlintCheck
./gradlew ktlintFormat   # 자동 수정

# 전체 빌드 (ktlintCheck + test 포함)
./gradlew build

# Gradle wrapper 생성 (최초 1회, gradle CLI 필요)
gradle wrapper --gradle-version 8.12
```

## 환경 변수

| 변수 | 기본값 / 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` (기본). 운영 배포 시 `prod` 필수 |
| `JWT_SECRET` | `prod` profile 전용. **미설정 시 기동 실패** (32 bytes↑ 랜덤) |
| `REFRESH_TOKEN_PEPPER` | `prod` profile 전용. **미설정 시 기동 실패** (32 bytes↑ 랜덤) |
| `REFRESH_COOKIE_SECURE` | `prod` 기본 `true`, `local` 기본 `false` |
| `DEEPGRAM_API_KEY` | Deepgram STT 키 |
| `CLAUDE_API_KEY` | Claude Headless 키 |

로컬 DB 접속 정보는 `application.yml`에 하드코딩 (flowmeet / local-password).

### Profile 정책 (secret 보호)

`application.yml`에는 secret 공개 기본값을 두지 않는다. 두 profile로 분리:

- `application-local.yml` — 로컬 전용 더미 secret 포함. `./gradlew bootRun` 은 기본으로 이 profile 활성 (build.gradle.kts 에서 `SPRING_PROFILES_ACTIVE=local` 지정).
- `application-prod.yml` — `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` 를 default 없이 선언. 운영에서 env var 누락 시 Spring placeholder 미해결로 `BeanCreationException` → **기동 실패**.

운영 배포 체크:

```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET='<32bytes↑ random>'
export REFRESH_TOKEN_PEPPER='<32bytes↑ random>'
./gradlew bootJar && java -jar build/libs/*.jar
```

둘 중 하나라도 빠지면 기동 즉시 실패한다 (의도된 동작).

## 설계 원칙 (TRD 발췌)

- **회의 상태**와 **processingStage**는 분리. 상태는 `SCHEDULED`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`만 사용. (TRD §4.1)
- **권한 검사는 반드시 백엔드에서**. 역할: 관리자/임원/팀장/팀원. (TRD §6)
- **비동기 작업 상태 머신**: `WAITING → PROCESSING → SPEAKER_MAPPING_REQUIRED → ANALYZING → REVIEW_REQUIRED → COMPLETED`. 자동 재시도 3회(1분/5분/15분). (TRD §5)
- **AI 제공자 교체 가능**하도록 Deepgram·Claude는 인터페이스로 추상화. (TRD §13)
- **파일 저장소**는 로컬 `uploads/` → 운영 시 S3로 교체 가능하도록 인터페이스 분리. 파일 접근 시 회의 권한 검증 필수.
- **보안**: 비밀번호 해시 저장, 음성 파일 URL 직접 노출 금지, 로그에 음성 원문·민감 회의 내용 금지. (TRD §11)

## 데이터베이스 스키마 변경 규칙

Flyway로 스키마를 버전 관리한다 (`src/main/resources/db/migration/V__{N}_{name}.sql`). 애플리케이션 기동 시 Flyway가 미적용 migration을 순서대로 적용.

- **V1 (baseline)**: `V1__init.sql` = `database/schema.sql`의 **바이트 수준 복사본**. 두 파일은 항상 동일 내용으로 유지한다.
  - 로컬 Postgres는 `docker-compose.yml`이 `database/schema.sql`을 entrypoint로 적용 → V1 skip (baseline-on-migrate).
  - Testcontainers·CI 등 깨끗한 DB에서는 Flyway가 V1을 그대로 적용.
- **V2 이후**: **신규 `V__{N}_{name}.sql` 파일만 추가**. `database/schema.sql`과 V1__init.sql은 **수정 금지** — baseline은 고정이다.
  - 예: `V2__add_navflags_to_users.sql`.
  - 신규 테이블·컬럼은 반드시 V__N 파일로. "SELECT 1" 수준의 변경이라도 Flyway history에 기록되도록.
- **로컬 DB 재초기화**: 스키마가 꼬였거나 baseline을 다시 적용하려면 `docker compose down -v && docker compose up -d` 로 볼륨 재생성.
- **설정 포인트**: `spring.flyway.baseline-on-migrate: true` + `baseline-version: 1` → 기존 로컬 DB(schema.sql = V1 내용이 docker init으로 적용된 상태)에 flyway_schema_history가 없을 때 "V1까지 완료된 상태"로 baseline을 기록하고 V2+부터 적용. 깨끗한 DB(Testcontainers 등)는 baseline이 불필요하므로 V1부터 그대로 적용.

## API 네임스페이스

`/api/auth/*`, `/api/meetings/*`, `/api/tasks/*`, `/api/dashboard`, `/api/locations/*` 등. 상세는 TRD §8 참고.

## 참고 문서

- TRD: `../outputs/TRD_조직지시_업무흐름관리플랫폼.md`
- PRD: `../outputs/PRD_조직지시_업무흐름관리플랫폼.md`
- 스키마: `../outputs/schema.sql` (`database/schema.sql`과 동기화 유지)

## 코드 규칙

- Kotlin 공식 코딩 컨벤션 (4-space indent, trailing comma 허용).
- 스타일·네이밍·패키징 상세는 [.claude/skills/kotlin-style/SKILL.md](./.claude/skills/kotlin-style/SKILL.md) 참고 — ktlint가 자동 강제.
- Spring Boot 레이어 책임(Controller는 Entity 반환 금지, 트랜잭션 경계는 Application만, 조회는 Projection 반환)은 [.claude/skills/spring-layering/SKILL.md](./.claude/skills/spring-layering/SKILL.md) 참고 — 패키지 구조(BC-first)·네이밍 등 상위 규칙이 상충하면 kotlin-style이 우선.
- 에러 처리는 `ApiException` + `ErrorCode` + `GlobalExceptionHandler`로만. 상세는 [.claude/skills/error-handling/SKILL.md](./.claude/skills/error-handling/SKILL.md).
- JPA/PostgreSQL 쿼리는 N+1 방지·`readOnly` 트랜잭션·인덱스 적중·cursor 페이지네이션. 신규 Repository 메서드 추가 시 [.claude/skills/jpa-query-guard/SKILL.md](./.claude/skills/jpa-query-guard/SKILL.md) 체크리스트 적용.
- 테스트는 JUnit 5 + MockK + Testcontainers. 레이어별 테스트 유형 선택과 Red→Green→Refactor 사이클은 [.claude/skills/tdd-service/SKILL.md](./.claude/skills/tdd-service/SKILL.md).
- 보안·로깅은 TRD §11 성문화 — JWT·비밀번호·음성 원문·회의 세부 내용은 응답/로그에 노출 금지. 마스킹 유틸(`SensitiveDataMasker`)과 상세는 [.claude/skills/security-logging/SKILL.md](./.claude/skills/security-logging/SKILL.md).
- Spring Boot 설정은 `application.yml` 사용 (properties 금지).
- 엔티티는 `@Entity` + `data class` 사용 지양 → 일반 `class`로 작성 (JPA 호환).
- 테스트는 JUnit 5 + Spring Security Test. `@SpringBootTest`는 최소화하고 slice test 우선.
- 외부 API 호출(Deepgram, Claude)은 인터페이스 → 구현체 분리 (`infrastructure/`).

## Claude Code 하네스

이 레포는 `.claude/` 하네스를 포함한다. 로컬 스킬·에이전트·커맨드 정의 위치는 다음과 같다 (SSOT).

### 작업 진입점: work-manager

GitHub Issue URL / `#N` / PR URL 로 작업을 시작할 때는 [.claude/skills/work-manager/SKILL.md](./.claude/skills/work-manager/SKILL.md) 를 진입점으로 사용. 투명성 원칙(`▶` 시작 / `✅` 완료 / `🚧` 병목)을 모든 하위 에이전트가 따른다.

**복잡 작업(FEAT·REFACTOR·PERF)**은 전문 에이전트 호출 전에 `brainstormer`(접근 2~3개 trade-off) → `plan-writer`(docs/plans/에 Task 쪼개서 저장) → 사용자 승인 게이트를 거친다. DOC·간단한 CHORE는 skip.


```text
.claude/
├─ skills/<이름>/SKILL.md     # 로컬 스킬 (예: work-manager)
├─ agents/<이름>.md           # 전문 에이전트 (예: feat, fix, refactor, doc)
├─ commands/<이름>.md         # 슬래시 커맨드 (선택)
└─ settings.json              # 공유 허용 툴·env (팀 기본값, 추적)

# 개인 오버라이드 (gitignore)
.claude/settings.local.json
```

### 스킬 추가 규칙

- 새 스킬은 반드시 `.claude/skills/<kebab-case-이름>/SKILL.md` 로 생성.
- SKILL.md 상단에 `name`, `description` frontmatter 필수 (Claude Code가 색인).
- 스킬은 하나의 책임만 — 복잡해지면 쪼갠다.

### work_history

- 위치: `work_history/` (**git 추적**)
- 각 작업은 Phase 2 포팅 후 `work-manager` 스킬이 자동 기록.
- 민감 정보(음성·회의 내용·토큰)는 로그에 쓰지 않는다 (TRD §11).
- `*.tmp`, `*.local.md`는 gitignore — 임시/개인 메모 전용.

### 하네스 자가 수정 주의

`.claude/` 아래 파일은 에이전트 자가 수정 대상으로 분류돼 자동 승인에서 종종 차단된다. 해당 경로를 수정하는 PR은 반드시 사람이 리뷰한다.

## 외부 코드 리뷰 (codex headless)

PR 생성 후 **codex CLI**를 헤드리스로 호출해 2차 리뷰를 받고, Claude가 각 지적을 원문 대조·평가(triage)해 유효한 것만 반영한다. 사람 리뷰와 병행하는 보조 레인.

- 상세 흐름·프롬프트·추출 스크립트: [.claude/skills/codex-review-triage/SKILL.md](./.claude/skills/codex-review-triage/SKILL.md)
- 산출물 경로: `codereview/<YYYYMMDD>/<slug>.md` (slug = 브랜치명의 `/`를 `-`로 치환) — **`.gitignore` 적용, 원격에 push되지 않는 로컬 전용 아카이브**.
- **반영 후 서버 검증 필수** (L0 ktlintCheck / L1 build / L2 bootRun+health). 커밋 메시지에 `Verified: L<N>` 라인 포함.
- 금지: Jira·외부 티켓 경로 사용 / codex의 `-s read-only` 외 샌드박스 / Claude triage 생략 / 서버 검증 생략한 push.
