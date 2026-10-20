---
issueKey: "#33"
issueTitle: "[S1-E2.1] User 엔티티·Repository + BCrypt 비번 저장"
slug: "user-repository-bcrypt"
author: "ParkJH0514"
createdAt: "2026-10-20 13:48"
chosenApproach: "A/A/A — Spring Data 인터페이스 직접 (Role 패턴 일관) + 전체 컬럼 매핑(team/role/manager는 nullable UUID로 유지, @ManyToOne 지연) + Spring PasswordEncoder bean 직접 사용 (#31에서 이미 등록됨)"
verifyLevel: "L1"
status: APPROVED
approvedAt: "2026-10-20 13:50"
---

# Plan — #33 [S1-E2.1] User 엔티티·Repository + BCrypt 비번 저장

## 1. 목표

- `users` 테이블에 대응하는 `@Entity class User` 생성 (전체 12 컬럼 매핑 (id 포함), data class 금지).
- `UserJpaRepository : JpaRepository<User, UUID>` 에 `findByEmail(email): User?` 제공.
- 도메인 레벨 팩토리 `User.create(name, email, rawPassword, encoder, systemPermission, …)`로 **비밀번호가 해시 상태로만 저장**되도록 보장.
- Repository slice test: `@DataJpaTest` + Testcontainers(PostgreSQL 16) + `withInitScript("schema.sql")` — Role 테스트와 동일 패턴.
- Team/Role Entity는 **S1-E3.1(#37) 이후** 추가. User는 team_id/role_id/manager_id를 nullable UUID 컬럼으로만 유지.

## 2. 선택된 접근

**A/A/A** (brainstormer 결과).

- **Repository 패턴 A**: `UserJpaRepository` 단일 Spring Data 인터페이스. 기존 `RoleJpaRepository`와 일관.
- **Entity 범위 A**: schema.sql 전체 컬럼 매핑. ddl-auto=validate 안전성 ↑, 후속 작업에서 FK 승격(@ManyToOne)만 하면 됨.
- **Hasher A**: Spring `PasswordEncoder` bean 직접 사용 (#31에서 이미 `SecurityConfig.kt:43-44`에 `BCryptPasswordEncoder` bean 등록됨). 신규 설정 불필요. 도메인 접점은 `User.create(..., encoder)` 팩토리로 캡슐화 — "비번 저장 전 해싱 유틸"의 역할을 이 팩토리가 수행.

## 3. 영향 파일

### 신규

- `src/main/kotlin/com/flowmeet/auth/domain/User.kt` — `@Entity class User`, 전체 12 컬럼 매핑 (id 포함), `User.create(…)` 팩토리 (companion object). primary constructor는 `private` — 팩토리 우회 금지.
- `src/main/kotlin/com/flowmeet/auth/infrastructure/persistence/UserJpaRepository.kt` — `interface UserJpaRepository : JpaRepository<User, UUID>` with `findByEmail(email: String): User?`.
- `src/test/kotlin/com/flowmeet/auth/infrastructure/persistence/UserJpaRepositoryTest.kt` — slice test (아래 "검증 계획" 참조).

### 수정

- **없음** (PasswordEncoder bean은 #31에서 이미 등록).

### 레이어

- domain(`User`) + infrastructure/persistence(`UserJpaRepository`). Application/API 레이어는 #34 login에서 추가.
- 설정·보안 bean 추가 없음. SecurityConfig 변경 없음.

## 4. Task

- [ ] T1. `User` 엔티티 작성 — `src/main/kotlin/com/flowmeet/auth/domain/User.kt`
      · schema.sql 12 컬럼 1:1 매핑: id (UUID @Id), name (VARCHAR(100), `length = 100` 명시), email (VARCHAR(255) unique, `length = 255` 명시), passwordHash (TEXT), teamId (nullable UUID), roleId (nullable UUID), managerId (nullable UUID), systemPermission (enum @Enumerated(STRING)), temporaryPassword (bool, default TRUE), active (bool, default TRUE), createdAt (Instant), updatedAt (Instant).
      · primary constructor는 `private` — 모든 생성은 `User.create(…)` 팩토리를 거쳐 비밀번호 해싱 불변식을 강제.
      · `enum class SystemPermission { ADMIN, USER }` 를 **같은 파일 또는 User.kt 하단**에 정의 — Postgres `system_permission` enum과 매핑 (`@Enumerated(EnumType.STRING)` + `@Column(columnDefinition = "system_permission")` + `@org.hibernate.annotations.JdbcTypeCode(SqlTypes.NAMED_ENUM)` 또는 그에 상응하는 수단).
      · `companion object` 에 `User.create(name, email, rawPassword, encoder, systemPermission = USER, teamId = null, roleId = null, managerId = null, temporaryPassword = true): User` — `encoder.encode(rawPassword)`로 해시해 저장. **rawPassword는 변수에 담지 않고 즉시 encode 호출**.
      · `@Entity class` (not data class) + 기본 생성자 상호 운용 — Role 패턴 따름 (`val id = UUID.randomUUID()` default).
      · **민감 정보 보호**: `toString()`에서 passwordHash 제거(필요 시 `@Transient` 또는 수동 override). 로그에 User 그대로 찍지 않음 — security-logging §.

- [ ] T2. `UserJpaRepository` 작성 — `src/main/kotlin/com/flowmeet/auth/infrastructure/persistence/UserJpaRepository.kt`
      · `interface UserJpaRepository : JpaRepository<User, UUID>`.
      · 메서드: `fun findByEmail(email: String): User?`.
      · 추가 쿼리는 이번 범위 밖 (#34 login 시점에 필요하면 추가).
      · jpa-query-guard: 메서드 1개, 인덱스 적중 OK (email UNIQUE), N+1 無. L2 추가 분석 불필요.

- [ ] T3. Repository slice test (Red) — `src/test/kotlin/com/flowmeet/auth/infrastructure/persistence/UserJpaRepositoryTest.kt`
      · `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Testcontainers` + Role 테스트와 동일한 companion object 블록 (PostgreSQL 16 + `withInitScript("schema.sql")`).
      · 테스트 케이스 (백틱 한국어 테스트 이름, kotlin-style 준수):
        1. `email로 User를 저장하고 조회한다` — save → findByEmail → id·email 일치.
        2. `존재하지 않는 email 조회 시 null을 반환한다`.
        3. `중복된 email로 저장하면 unique 제약 위반이 발생한다` — assertThatThrownBy DataIntegrityViolationException.
        4. `User.create는 비밀번호를 BCrypt 해시로 저장한다` — User.create로 만든 user의 passwordHash는 raw와 다르고, `encoder.matches(raw, hash)` 가 true. (단위 테스트 수준이지만 slice test 안에서 encoder bean을 직접 쓸 수 없으므로 **별도 단위 테스트 파일**로 분리 가능 — 결정은 T3-b 참조).
      · T3-b: `src/test/kotlin/com/flowmeet/auth/domain/UserTest.kt` (단위, Spring 컨텍스트 無) — `BCryptPasswordEncoder()` 직접 인스턴스화해 `User.create(…)` 테스트. 4번 케이스를 여기로.
      · **Red 확인**: T1·T2 미작성 상태에서 테스트 작성 → 컴파일 실패 또는 bean 없음.

- [ ] T4. 최소 구현으로 Green — T1·T2 작성 완료 후 `./gradlew test --tests "...UserJpaRepositoryTest" --tests "...UserTest"` 통과.

- [ ] T5. Refactor — 중복 제거(테스트의 User 생성 helper), 네이밍 개선. 기존 테스트 (58개) 통과 유지.

- [ ] T6. L0 — `./gradlew ktlintCheck`

- [ ] T7. L1 — `./gradlew build` (ktlintCheck + 전체 test). 58 + 신규 ≈ 62~65 tests passed 기대.

- [ ] T8. 커밋 — `feat(auth): User entity + repository + BCrypt-aware factory (#33)` + body에 `Verified: L1`. **사용자 명시 승인 후.**

- [ ] T9. PR 생성 → `_pr-flow` 위임. 라벨 전환 in-progress → in-review. **머지는 사용자 명시 승인 후.**

## 5. 검증 계획

- **레벨**: **L1** (`./gradlew build` = ktlintCheck + 전체 test suite).
- 명령:
  - L0: `./gradlew ktlintCheck`
  - L1: `./gradlew build`
- 체크리스트:
  - [ ] 신규 테스트 4개 (Repository 3 + Domain 1) 모두 PASS
  - [ ] 기존 58 테스트 regression 없음
  - [ ] `./gradlew bootRun` 기동 시 Hibernate validate 성공 (schema ↔ Entity 매핑 정합) — 수동 1회 확인 선택 사항
- 롤백 포인트: `e617352` (브랜치 시작, main HEAD)

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**: User.toString()에 passwordHash·email 노출 금지. 테스트 어서션에서도 raw password를 로그로 찍지 않음. [security-logging](../../.claude/skills/security-logging/SKILL.md) 참조.
- **error-handling**: 이번 범위에선 신규 예외 無. ApiException/ErrorCode 변경 없음.
- **spring-layering**: domain에는 JPA 매핑만, Spring 스테레오타입(@Service, @Repository) 없음. UserJpaRepository는 Spring Data 인터페이스로 **@Repository 어노테이션 불필요** (Spring Boot auto-detect).
- **jpa-query-guard**: 메서드 1개, 쿼리 1건, 인덱스 적중, N+1 無. L2 분석 생략.
- **kotlin-style**: `@Entity class` (not data class), 4-space indent, trailing comma 허용, 백틱 한국어 테스트 이름.
- **tdd-service**: Red→Green→Refactor 사이클 (T3 Red → T4 Green → T5 Refactor), `@DataJpaTest`+Testcontainers, MockK 미사용 (여기선 불필요).

## 7. 알려진 리스크 / 가정

- **R1**: `system_permission` Postgres enum 타입 매핑 — Hibernate 6.x는 `@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` 조합이 Postgres enum을 지원. 실패 시 fallback은 `@Column(columnDefinition = "text")` + application-level enum. 테스트가 R1 검증.
- **R2**: User.toString() 자동 생성이 passwordHash를 노출할 수 있음 — `@Entity class`는 Kotlin이 자동 toString 생성 안 함(data class와 달리) → 리스크 낮음. 명시적 toString override는 선택.
- **R3**: PasswordEncoder bean은 common/security에, User는 auth/domain에 — domain이 common.security를 import하면 레이어 역류. **해결**: `User.create` 가 `PasswordEncoder` 를 **파라미터로 받음** (의존 역전) → domain은 PasswordEncoder 인터페이스(Spring Security의 표준 인터페이스)만 알게 됨. 테스트에서는 BCryptPasswordEncoder()를 직접 생성해 주입.
- **A1**: Flyway 설정은 #32에서 이미 완료. Testcontainers는 withInitScript로 schema를 적용하므로 Flyway는 baseline만 하고 끝 — RoleJpaRepositoryTest가 이미 Green인 사실로 검증됨.
- **A2**: team_id/role_id/manager_id는 nullable → User 생성 시 생략 가능. #37 Team 엔티티 생성 시 `@ManyToOne` 으로 승격 예정.
