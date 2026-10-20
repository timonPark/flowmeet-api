---
issueKey: "#32"
issueTitle: "[S1-E1.2] Flyway 도입 + V1 baseline"
slug: "flyway-v1-baseline"
author: "ParkJH0514"
createdAt: "2026-10-20 13:00"
chosenApproach: "옵션 A — 공존 + 동기화 규칙 (schema.sql 유지, V1__init.sql 복사본, Flyway baseline-on-migrate: true + baseline-version: 1 — docker init으로 V1이 이미 반영된 로컬 DB를 baseline으로 수용)"
verifyLevel: "L1"
status: APPROVED
approvedAt: "2026-10-20 13:02"
---

# Plan — #32 [S1-E1.2] Flyway 도입 + V1 baseline

## 1. 목표

- Flyway로 스키마 버전 관리 시작 — 이후 S1-E2.1 (User), S1-E3.1 (Team) 등 모든 스키마 변경이 V2+로 이관.
- `database/schema.sql`과 `src/main/resources/db/migration/V1__init.sql`을 **동일 내용으로 공존** — docker-compose init은 그대로, Spring Boot 기동은 Flyway로 적용.
- 기존 로컬 Postgres 볼륨(`flowmeet-pgdata`)에 schema가 이미 적용된 상태에서도 `baseline-on-migrate: true`로 안전하게 수용.
- CLAUDE.md에 "두 파일 동기화 규칙" 섹션 추가 — 신규 스키마 변경 시 양쪽 수정 누락 방지.
- 로컬 검증: `./gradlew build` + `docker compose up -d` + 애플리케이션 기동 로그에서 `Flyway Community Edition ... by Redgate` + `baseline` 라인 확인.

## 2. 선택된 접근

**옵션 A — 공존 + 동기화 규칙** (brainstormer 결과).

- `database/schema.sql` 유지 → docker-compose Postgres entrypoint가 최초 1회 적용.
- `src/main/resources/db/migration/V1__init.sql` 신규 (schema.sql 내용 그대로 복사).
- `application.yml` 에 `spring.flyway.baseline-on-migrate: true` + `baseline-version: 0` → 기존 DB에 flyway_schema_history 없이 schema 있어도 V1을 skip, V2+부터 적용.
- CLAUDE.md 코드 규칙 섹션에 동기화 규칙 명문화.

**옵션 B(단일 소스)·C(심볼릭)는 scope 확대·호환성 위험으로 skip.**

## 3. 영향 파일

### 신규

- `src/main/resources/db/migration/V1__init.sql` — `database/schema.sql`과 **바이트 수준 동일** 복사본. Flyway V1 baseline.

### 수정

- `build.gradle.kts` — `implementation("org.flywaydb:flyway-core")` + `implementation("org.flywaydb:flyway-database-postgresql")` 추가 (Spring Boot 3.4가 관리하는 Flyway 10.x는 Postgres 모듈 분리 필요).
- `src/main/resources/application.yml` — `spring.flyway` section 추가 (enabled: true, baseline-on-migrate: true, baseline-version: 1, locations: classpath:db/migration).
- `CLAUDE.md` — "## 데이터베이스 스키마 동기화" 섹션 추가 (schema.sql ↔ V1__init.sql 동기화 규칙 + 이후 V2+는 V__ 파일만 추가).

### 영향 범위 (레이어)

- infrastructure (설정·마이그레이션) + 문서. **코드 로직 변경 없음** — 레이어링 규칙 영향 없음.
- DB 스키마 자체 변경 없음 (내용 동일 복사). JPA entity·Repository 영향 없음.

## 4. Task

- [ ] T1. `build.gradle.kts`에 Flyway 의존성 추가
      · 파일: `build.gradle.kts`
      · 변경: `implementation("org.flywaydb:flyway-core")` + `implementation("org.flywaydb:flyway-database-postgresql")` (Spring Boot BOM이 관리하는 버전 사용)
      · 검증: `./gradlew dependencies --configuration runtimeClasspath | grep flyway` 로 두 모듈 포함 확인

- [ ] T2. `V1__init.sql` 생성 (schema.sql 복사)
      · 파일: `src/main/resources/db/migration/V1__init.sql` (신규)
      · 액션: `database/schema.sql`을 **그대로** 복사. 내용 변경·재정렬 금지. 라인 수·바이트 수 동일 확인.
      · 검증: `diff database/schema.sql src/main/resources/db/migration/V1__init.sql` → 출력 없음

- [ ] T3. `application.yml`에 flyway 설정 추가
      · 파일: `src/main/resources/application.yml`
      · 변경: `spring.flyway.{enabled: true, baseline-on-migrate: true, baseline-version: 1, locations: classpath:db/migration, validate-on-migrate: true}` 추가. `baseline-version: 1`로 두어야 docker init(schema.sql = V1 내용)으로 이미 적용된 상태를 V1 완료로 간주하고 V2+부터 적용. 0으로 두면 V1 재실행 → "table already exists" 실패.
      · 선행: T1 완료 (의존성 없이는 설정 무효)
      · 검증: T6에서 bootRun 시 Flyway 적용 로그 확인

- [ ] T4. `CLAUDE.md`에 동기화 규칙 섹션 추가
      · 파일: `CLAUDE.md`
      · 변경: 기존 "## 코드 규칙" 아래 또는 디렉터리 구조 섹션 근처에 "## 데이터베이스 스키마 변경 규칙" 섹션 신설
      · 내용: ①V1 초기 스키마는 schema.sql과 V1__init.sql이 동일 ②V2 이후부터는 **V__N 파일만 추가** (schema.sql은 참조용으로 유지하되 수정 금지) ③로컬 DB 초기화 커맨드 (`docker compose down -v && up -d`) 안내
      · 검증: 리뷰 시점 — 사람 눈으로 섹션 가독성 확인

- [ ] T5. L0 검증 — ktlint + 컴파일
      · 명령: `./gradlew ktlintCheck build -x test`
      · 통과 조건: BUILD SUCCESSFUL (test는 T6에서 수행)

- [ ] T6. L1 검증 — docker compose 재기동 + bootRun 시 Flyway 적용 확인
      · 명령 순서:
        1. `docker compose down -v` (기존 볼륨 삭제 — 깨끗한 상태에서 schema.sql 재적용 검증)
        2. `docker compose up -d && sleep 5`
        3. `./gradlew bootRun` 를 백그라운드로 띄우고 로그에서 `Flyway Community Edition` + `Successfully baselined schema with version: 0` 또는 `Current version of schema "public": 0, migrating to version "1"` 중 하나 확인 (볼륨 삭제 직후면 baseline, 이미 있으면 skip)
        4. actuator health 확인: `curl -sS http://localhost:8080/actuator/health | jq .status` → `UP`
        5. bootRun 종료
      · 통과 조건: Flyway 로그에 에러 없음 + health UP
      · **민감 정보 금지**: 로그 캡처 시 JWT_SECRET·DB 비밀번호 라인은 발췌에서 제외

- [ ] T7. `./gradlew test` (기존 테스트 regression 확인)
      · 명령: `./gradlew test`
      · 통과 조건: 기존 58 테스트 모두 PASS. Flyway는 testcontainers에서도 자동 실행될 수 있으므로 slice test·@SpringBootTest 영향 모니터링
      · 실패 시: Testcontainers Postgres에 V1 migration이 자동 적용되는지, build.gradle.kts의 `test { resources { srcDir("database") } }`와 중복되는지 조사 → T3 설정 조정 (예: test profile에서 flyway.locations 조정)

- [ ] T8. ktlintFormat (있으면) + 최종 build
      · 명령: `./gradlew ktlintFormat build`
      · 통과 조건: BUILD SUCCESSFUL + 58 tests (or 더 많이) passed

- [ ] T9. 커밋 — Conventional Commits
      · 메시지: `chore(db): introduce Flyway + V1 baseline (schema.sql mirror) (#32)` + body에 `Verified: L1` 라인
      · **사용자 명시 승인 후에만 실행**

- [ ] T10. PR 생성 → `_pr-flow` 위임
      · 명령: `gh pr create --repo timonPark/flowmeet-api --base main --head chore/32-flyway-v1-baseline ...` + `Closes #32`
      · 이후 라벨 전환 `in-progress` → `in-review`
      · 머지는 **사용자 명시 승인 후만** (CLAUDE.md "PR 머지 규칙")

## 5. 검증 계획

- **레벨**: **L1** (ktlint + build + docker compose 재기동 + Flyway 적용 로그 + health UP)
- 명령 요약:
  - L0: `./gradlew ktlintCheck`
  - L1: `./gradlew build` + `docker compose down -v && up -d` + `./gradlew bootRun` + `curl /actuator/health`
- 롤백 포인트: `9849cfb` (브랜치 시작 — main HEAD)
- 체크리스트:
  - [ ] `V1__init.sql` ↔ `schema.sql` bytes diff empty
  - [ ] Flyway runtime 로그에 ERROR 없음
  - [ ] health UP
  - [ ] 기존 테스트 regression 없음

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**: 플랜 본문·V1__init.sql·로그에 비밀번호·토큰·회의 내용 **없음**. schema.sql 자체도 DDL만 포함.
- **ApiException/ErrorCode 영향**: 없음 — 설정·마이그레이션만 추가.
- **JPA/N+1**: 영향 없음 — 쿼리 변경 없음.
- **보안·로깅**: Flyway는 자체 로그에 DDL을 출력하지만 민감 데이터 아님. logback 레벨 `info` 유지.
- 상위 규칙 참조: [spring-layering](../../.claude/skills/spring-layering/SKILL.md), [jpa-query-guard](../../.claude/skills/jpa-query-guard/SKILL.md), [security-logging](../../.claude/skills/security-logging/SKILL.md), [tdd-service](../../.claude/skills/tdd-service/SKILL.md)

## 7. 알려진 리스크 / 가정

- **R1**: Flyway 10.x는 Postgres driver가 별도 모듈 (`flyway-database-postgresql`). 누락 시 "No database found to handle jdbc:postgresql" 런타임 에러. → T1에서 함께 추가.
- **R2**: Testcontainers 환경에서 Flyway가 자동 실행되면 `test { resources { srcDir("database") } }` 로 주입된 schema.sql과 중복될 가능성. → T7에서 regression 확인하고 필요 시 test profile에서 `spring.flyway.enabled: false` 또는 locations 조정 — 단, 이 결정은 **T7 결과에 따라 재승인 받음**.
- **R3**: `baseline-on-migrate: true` + `baseline-version: 0`는 이미 schema가 적용된 기존 DB에서 안전하지만, **V1을 skip**하므로 "V1에만 있는 변경"은 기존 DB에 반영되지 않는다. 이번은 schema.sql과 V1이 동일하므로 문제없음.
- **A1**: Spring Boot 3.4.1 BOM이 관리하는 Flyway 버전 사용 (명시적 버전 핀 금지, BOM 신뢰).
