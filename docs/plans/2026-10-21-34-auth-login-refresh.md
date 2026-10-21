---
issueKey: "#34"
issueTitle: "[S1-E2.2] POST /api/auth/login + Refresh 발급·쿠키"
slug: "auth-login-refresh"
author: "ParkJH0514"
createdAt: "2026-10-21 12:40"
chosenApproach: "B — HMAC-SHA256(pepper) refresh hash + Role 별도 조회 + profileComplete 하드코딩 true (S1 contract 전제)"
verifyLevel: "L1"
status: APPROVED
approvedAt: "2026-10-21 12:50"
---

# Plan — #34 [S1-E2.2] POST /api/auth/login + Refresh 발급·쿠키

## 1. 목표

- `POST /api/auth/login` — 이메일/비번 검증 성공 시 Access(JWT, 30분) + Refresh(opaque 32B, 14일) 발급
- 응답 바디: `{ accessToken, accessTokenExpiresIn, tokenType: "Bearer", user: { id, name, email, role, systemPermission, requirePasswordChange, profileComplete } }`
- Response `Set-Cookie: refreshToken=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=1209600`
- 실패 시 `AUTH-401 INVALID_CREDENTIALS` 단일 메시지 (이메일/비번 구분 금지)
- `refresh_tokens.token_hash` 에 **HMAC-SHA256(pepper, raw)** 결과를 Base64URL로 저장 (raw 저장 금지)
- 비번·refresh raw는 **로그 금지** (`SensitiveDataMasker` 통과 메시지만)

## 2. 선택된 접근

**옵션 B** — HMAC-SHA256(pepper) refresh hash + Role 별도 조회.

- Refresh 토큰: `SecureRandom` 32B → Base64URL → cookie 반환, DB에는 HMAC-SHA256(pepper, raw) Base64URL만 저장.
- pepper: 신규 설정 키 `flowmeet.security.refresh-token-pepper` (운영은 환경변수로 교체). JWT secret과 **분리**.
- Role 조회: `RoleJpaRepository.findById(user.roleId)` 추가 호출. role name은 contract의 `ADMIN | EXECUTIVE | TEAM_LEADER | TEAM_MEMBER` enum으로 매핑. roleId가 null이거나 알 수 없는 name은 `TEAM_MEMBER` 기본값 (seed admin은 ADMIN 보장).
- `profileComplete`: S1 contract "seed 사용자는 true로 미리 세팅" 전제대로 **하드코딩 true**. S2에서 계산 로직 추가 예정.

## 3. 영향 파일

### 신규

- `src/main/kotlin/com/flowmeet/auth/api/AuthController.kt` — `POST /api/auth/login` 수신, DTO ↔ Service 변환, Set-Cookie 작성
- `src/main/kotlin/com/flowmeet/auth/api/dto/LoginRequest.kt` — email(not blank/email), password(not blank, 8..72)
- `src/main/kotlin/com/flowmeet/auth/api/dto/LoginResponse.kt` + `AuthUserDto` — 응답 shape
- `src/main/kotlin/com/flowmeet/auth/application/AuthService.kt` — login(email, rawPassword): LoginResult. Access/Refresh 발급·저장
- `src/main/kotlin/com/flowmeet/auth/application/LoginResult.kt` — Controller가 쿠키 작성에 쓸 refresh raw + response body 모델
- `src/main/kotlin/com/flowmeet/auth/domain/RefreshToken.kt` — Entity(`refresh_tokens` 매핑, private ctor + factory)
- `src/main/kotlin/com/flowmeet/auth/domain/UserRole.kt` — contract 4종 enum (ADMIN/EXECUTIVE/TEAM_LEADER/TEAM_MEMBER)
- `src/main/kotlin/com/flowmeet/auth/infrastructure/persistence/RefreshTokenJpaRepository.kt` — Spring Data 인터페이스. `findByTokenHashAndRevokedAtIsNull`은 #35에서 사용 예정 — 이번 PR은 `save`만.
- `src/main/kotlin/com/flowmeet/auth/infrastructure/token/RefreshTokenHasher.kt` — HMAC-SHA256(pepper) 유틸. `hash(raw: String): String` 결정적
- `src/main/kotlin/com/flowmeet/auth/infrastructure/token/RefreshTokenGenerator.kt` — SecureRandom 32B → Base64URL
- `src/main/kotlin/com/flowmeet/common/security/RefreshTokenProperties.kt` — `@ConfigurationProperties(prefix = "flowmeet.security.refresh-token")` (pepper, cookie-secure 토글)
- `src/test/kotlin/com/flowmeet/auth/application/AuthServiceTest.kt` — 단위 (성공, 이메일 없음, 비번 불일치, inactive user)
- `src/test/kotlin/com/flowmeet/auth/api/AuthControllerTest.kt` — @WebMvcTest slice (200 shape, 401, 400 validation, Set-Cookie 속성)
- `src/test/kotlin/com/flowmeet/auth/infrastructure/token/RefreshTokenHasherTest.kt` — 결정적 hash, pepper 변화 시 결과 변경

### 수정

- `src/main/kotlin/com/flowmeet/common/security/SecurityConfig.kt` — `/api/auth/login`은 이미 permitAll 범위 (`anyRequest().permitAll()`), **이번 PR은 변경 없음**. #36에서 authenticated 전환 시 login/refresh/health만 permit 유지.
- `src/main/resources/application.yml` — `flowmeet.security.refresh-token.pepper` 로컬 기본값 + `cookie-secure: false` (local), `cookie-max-age: PT336H` (14일) 추가
- `src/main/kotlin/com/flowmeet/FlowmeetApiApplication.kt` — `@ConfigurationPropertiesScan` 또는 명시적 `@EnableConfigurationProperties(RefreshTokenProperties::class)` (JwtProperties와 동일 패턴 확인 후 선택)

## 4. Task

- [ ] **T1**. `UserRole` enum + `RefreshToken` Entity 작성 — `auth/domain/UserRole.kt`, `auth/domain/RefreshToken.kt`
      · 검증: 컴파일 (`./gradlew compileKotlin`)
- [ ] **T2**. `RefreshTokenJpaRepository` 인터페이스 + `RefreshTokenHasher`(HMAC-SHA256) + `RefreshTokenGenerator`(SecureRandom 32B) 작성
      · 선행: T1
      · 검증: `RefreshTokenHasherTest` RED 작성 (결정성 + pepper 변경 시 다름)
- [ ] **T3**. `RefreshTokenHasher` 구현 → T2 테스트 GREEN
      · 검증: `./gradlew test --tests "*RefreshTokenHasherTest"` 통과
- [ ] **T4**. `RefreshTokenProperties` + `application.yml` 로컬 pepper + Application 등록
      · 검증: `./gradlew bootRun` 또는 `@SpringBootTest` 컨텍스트 로드 (후속 테스트에서 간접 검증)
- [ ] **T5**. `AuthService.login` RED 테스트 — 성공(user·토큰 반환), 비번 불일치→ApiException(AUTH-401), 이메일 없음→ApiException(AUTH-401), inactive→AUTH-401
      · MockK로 `UserJpaRepository`, `RoleJpaRepository`, `PasswordEncoder`, `JwtTokenProvider`, `RefreshTokenGenerator`, `RefreshTokenHasher`, `RefreshTokenJpaRepository` 전부 mock
      · 검증: `./gradlew test --tests "*AuthServiceTest"` FAILED
- [ ] **T6**. `AuthService.login` 구현 → T5 GREEN
      · readOnly 트랜잭션 외부 + refresh 저장만 writeable 서브 메서드 (단일 트랜잭션 유지가 더 단순 — 로그인 자체는 짧은 트랜잭션)
      · 검증: `./gradlew test --tests "*AuthServiceTest"` 통과
- [ ] **T7**. `LoginRequest`/`LoginResponse`/`AuthUserDto` DTO 작성 + `AuthController` slice RED 테스트
      · `@WebMvcTest(AuthController::class)` + `MockkBean(AuthService)` — 200/400/401, Set-Cookie 속성(`HttpOnly`, `Secure=false` 로컬, `SameSite=Lax`, `Path=/api/auth`, `Max-Age=1209600`)
      · 검증: `./gradlew test --tests "*AuthControllerTest"` FAILED
- [ ] **T8**. `AuthController` 구현 (ResponseCookie 사용) → T7 GREEN
      · 검증: `./gradlew test --tests "*AuthControllerTest"` 통과
- [ ] **T9**. 로그 안전 확인 — `AuthService`에 `log.info("login attempt email=${SensitiveDataMasker.email(req.email)}")` 패턴. 비번·refresh raw는 어떤 레벨에서도 출력 금지. SensitiveDataMasker.scrub() 통과 유닛 체크.
      · 검증: 로그 포맷 그렙 + 코드 리뷰 체크리스트
- [ ] **T10**. `./gradlew ktlintFormat build` — L1 검증
      · 검증: `BUILD SUCCESSFUL`, 전체 테스트 녹색
- [ ] **T11**. 커밋 — Conventional Commits (`feat(auth): login API + refresh cookie (#34)`)
      · **사용자 명시 승인 후에만 실행**
- [ ] **T12**. PR 생성 → `_pr-flow.md` 로 위임 (리뷰·머지는 별도 사용자 승인)

## 5. 검증 계획

- **레벨**: L1 (`./gradlew build` = ktlintCheck + test)
- **주요 명령**:
  - `./gradlew test --tests "com.flowmeet.auth.*"`
  - `./gradlew ktlintCheck`
  - `./gradlew build`
- **DB 스키마 변경 없음** → Flyway migration 추가 불필요 (V1 `refresh_tokens` 테이블 그대로 사용)
- **보안 verification**: pepper 로컬 기본값은 긴 랜덤 문자열, 운영은 `REFRESH_TOKEN_PEPPER` 환경변수. secret 길이 체크(≥32 bytes) `require`.
- **롤백 포인트**: 브랜치 시작 `origin/main` (6a09495)

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**:
  - 응답/로그/plan 본문에 **비번·refresh raw 미포함** ✅
  - 이메일은 `SensitiveDataMasker.email()` 통과 후 로깅
  - `AUTH-401` 메시지 "이메일 또는 비밀번호가 올바르지 않습니다" — 사용자 열거 공격 방지 ✅
- **spring-layering**: Controller → Service → Repository, DTO는 api 레이어에만, Entity는 Controller 반환 금지 ✅
- **jpa-query-guard**: 로그인은 단건 조회(by email unique) + role 단건 조회 + refresh insert — N+1 없음, readOnly 트랜잭션은 service.login 전체(단, insert 포함이므로 readOnly=false)
- **error-handling**: `AuthService` 는 `ApiException(ErrorCode.INVALID_CREDENTIALS)` 만 throw. GlobalExceptionHandler가 그대로 변환.
- **tdd-service**: Service 단위 테스트 Red→Green→Refactor, Controller는 slice test, 공통 체인 통합은 S1-E2.4(#36)에서.
- **security-logging**: `log.info("login attempt email={}", SensitiveDataMasker.email(email))` + 성공 시 `userId` 만. 비번 변수는 변수명으로 로그 금지.

## 7. S1 범위 밖 (명시)

- Refresh 재발급(`POST /api/auth/refresh`) + 로그아웃(`POST /api/auth/logout`) → **#35**
- JwtAuthenticationFilter active 모드 전환(401 분기) → **#36**
- 로그인 N회 실패 잠금 → S2+ (`AUTH-423` 코드만 예약)
- `profileComplete` 실제 계산 → S2+
- `GET /api/auth/me` → S1-E3.3 (#?)

## 8. 참조

- Contract: `docs/sprints/S1/api-contract.md` §0.3·0.5·§1 API-E2-01
- Plan: `docs/sprints/S1/backend-plan.md` §2 S1-E2.2
- Skills: `security-logging`, `error-handling`, `spring-layering`, `tdd-service`
