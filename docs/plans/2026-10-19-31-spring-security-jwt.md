---
issueKey: "#31"
issueTitle: "[S1-E1.1] Spring Security·JWT 인프라 뼈대"
slug: "spring-security-jwt"
author: "ParkJH0514"
createdAt: "2026-10-19 16:50"
chosenApproach: "옵션 B — 체인 통합 + inert 모드 (Filter를 chain에 등록하되 유효 토큰만 Authentication 세팅, 무효/누락은 통과. 401 분기는 E2.4)"
verifyLevel: "L1"
status: DONE
approvedAt: "2026-10-19 16:52"
mergedAt: "2026-10-19 17:14"
mergeCommit: "671e538c"
prUrl: "https://github.com/timonPark/flowmeet-api/pull/43"
---

# Plan — #31 [S1-E1.1] Spring Security·JWT 인프라 뼈대

## 1. 목표

- `com.flowmeet.common.security` 아래 Security + JWT 뼈대 구축.
- JwtTokenProvider: HS256, access 30분 발급·검증. refresh TTL은 설정만 담고 발급은 E2.3 범위.
- JwtAuthenticationFilter: Authorization 헤더 파싱 → 유효 토큰만 `SecurityContextHolder`에 Authentication 설정, 무효/누락은 **아무 것도 하지 않음**(inert).
- SecurityConfig: filter chain + CORS(`http://localhost:3000`) + 모든 요청 `permitAll()` + 신규 Filter를 `UsernamePasswordAuthenticationFilter` 앞에 삽입.
- SecurityContextUtil: 현재 인증 subject(user id) 반환 유틸.
- `application.yml`의 access TTL을 PT1H → PT30M로 수정 (api-contract §0.3).
- JWT secret은 `JWT_SECRET` env override + 32bytes↑ 검증 (startup에 fail-fast).
- L1 검증: `./gradlew ktlintCheck test build` 통과.

## 2. 선택된 접근

**옵션 B — 체인 통합 + inert 모드**:
- Filter chain에 신규 `JwtAuthenticationFilter` 등록.
- 유효 토큰: `SecurityContextHolder.getContext().authentication = ...` 세팅 후 chain.doFilter.
- 무효 토큰(만료·서명 불일치·malformed): **context 손대지 않고** chain.doFilter — 401은 E2.4에서 `authenticated()` + EntryPoint로 매핑.
- 누락 토큰: 그대로 chain.doFilter.
- 보안: 토큰 원문 로그 금지(`SensitiveDataMasker.token()` 통과) — security-logging §1·§4.

## 3. 영향 파일

### 신규
- `src/main/kotlin/com/flowmeet/common/security/JwtProperties.kt` — `@ConfigurationProperties("flowmeet.jwt")`: secret(String) / accessTokenTtl(Duration) / refreshTokenTtl(Duration). init block에서 secret 최소 32bytes 검증(fail-fast).
- `src/main/kotlin/com/flowmeet/common/security/JwtTokenProvider.kt` — HS256 발급(access only in this PR, refresh는 E2.3에서 발급 로직 추가)/검증. `issueAccessToken(subject, role)` / `parse(token): Claims` / `validate(token): Boolean`. `Clock` 주입(기존 `ClockConfig` 재사용).
- `src/main/kotlin/com/flowmeet/common/security/SecurityContextUtil.kt` — `currentUserId(): String?` / `currentRole(): String?`. `SecurityContextHolder` 래핑, 테스트 가능하도록 object 또는 Component.
- `src/main/kotlin/com/flowmeet/common/security/JwtAuthenticationFilter.kt` — `OncePerRequestFilter`. Bearer 토큰 추출 → 유효할 때만 `UsernamePasswordAuthenticationToken` 세팅. 로그는 **토큰 원문 금지**(scrub).
- `src/main/kotlin/com/flowmeet/common/security/SecurityConfig.kt` — `@EnableWebSecurity`. CORS(origin: `http://localhost:3000`, credentials true), CSRF disabled, session STATELESS, 모든 요청 permitAll, Filter 등록. BCryptPasswordEncoder Bean 포함(E2.1 선행 편의 — security-logging §2).
- `src/test/kotlin/com/flowmeet/common/security/JwtPropertiesTest.kt` — 32bytes 미만 secret 거부 테스트.
- `src/test/kotlin/com/flowmeet/common/security/JwtTokenProviderTest.kt` — 발급→검증 round-trip / 만료 토큰 reject / 서명 변조 reject.
- `src/test/kotlin/com/flowmeet/common/security/JwtAuthenticationFilterTest.kt` — 유효/만료/malformed/누락 각각 Authentication 설정 유무 검증.
- `src/test/kotlin/com/flowmeet/common/security/SecurityConfigTest.kt` — slice test(`@WebMvcTest` or `@SpringBootTest`)로 CORS preflight + permitAll 동작 확인.

### 수정
- `src/main/resources/application.yml` — `flowmeet.jwt.access-token-ttl: PT1H` → `PT30M`. CORS allowed-origin 설정 블록 신설(`flowmeet.security.cors.allowed-origins`). `JWT_SECRET` env override 코멘트 보강.
- (해당 시) `build.gradle.kts` 의존성 변경 없음 확인 — `starter-security` + `jjwt` 이미 존재.

### 영향 레이어
- common/security (신규) — spring-layering의 common 영역. api/application/domain/infrastructure 어느 쪽에도 종속되지 않음.
- 외부 계약: Response 변경 없음. permitAll이므로 기존 PingController 동작 유지.

## 4. Task

- [ ] **T1. JwtProperties 추가 + 32bytes 검증 (Red → Green)**
      · 신규: `JwtProperties.kt`, `JwtPropertiesTest.kt`
      · 검증: `./gradlew test --tests "*JwtPropertiesTest"` 32bytes 미만일 때 IllegalArgumentException 확인
      · 참고: security-logging §1 "secret ≥ 32bytes", `@ConstructorBinding` 패턴
- [ ] **T2. JwtTokenProvider 테스트 작성 (Red)**
      · 신규: `JwtTokenProviderTest.kt`
      · 케이스: round-trip, 만료 토큰 거부(Clock mock), 서명 변조 거부
      · 검증: 테스트 FAILED 확인
- [ ] **T3. JwtTokenProvider 구현 (Green)**
      · 신규: `JwtTokenProvider.kt`. `issueAccessToken(subject: String, role: String): String`, `parse(token: String): Claims`, `validate(token: String): Boolean`
      · `Clock` 주입(기존 `common.config.ClockConfig` 재사용), jjwt 0.12 API
      · 검증: T2 테스트 Green
- [ ] **T4. SecurityContextUtil 구현 + 테스트**
      · 신규: `SecurityContextUtil.kt`, `SecurityContextUtilTest.kt`
      · `currentUserId()`, `currentRole()` — Context 비어있으면 null
      · 검증: `./gradlew test --tests "*SecurityContextUtilTest"`
- [ ] **T5. JwtAuthenticationFilter 테스트 작성 (Red)**
      · 신규: `JwtAuthenticationFilterTest.kt`
      · 케이스: 유효 Bearer → Authentication 세팅 / 만료 → 미세팅 통과 / malformed → 미세팅 통과 / 누락 → 미세팅 통과
      · 추가: **로그에 토큰 원문이 섞이지 않는지** 검증(appender 또는 verify Masker 호출)
      · 검증: 테스트 FAILED 확인
- [ ] **T6. JwtAuthenticationFilter 구현 (Green)**
      · 신규: `JwtAuthenticationFilter.kt`. `OncePerRequestFilter` 상속. inert 모드 — 실패 시 chain.doFilter 통과
      · 로그: `SensitiveDataMasker.token()` 사용. ApiException throw 금지(E2.4 범위)
      · 검증: T5 테스트 Green
- [ ] **T7. SecurityConfig 구현 + CORS/filter 등록**
      · 신규: `SecurityConfig.kt`. `BCryptPasswordEncoder` Bean 포함
      · 설정: STATELESS session, CSRF disabled, 모든 요청 permitAll, CORS(`http://localhost:3000` + `credentials=true`), Filter를 `UsernamePasswordAuthenticationFilter.class` 앞에 등록
      · 검증: `SecurityConfigTest`로 preflight(OPTIONS) 200 + GET /api/ping(기존) 200 확인
- [ ] **T8. application.yml 수정**
      · 수정: `flowmeet.jwt.access-token-ttl: PT30M`, `flowmeet.security.cors.allowed-origins: ["http://localhost:3000"]`
      · 코멘트: `JWT_SECRET` env override, 운영 전환 시 32bytes↑ 랜덤 교체 안내
      · 검증: `./gradlew bootRun` 로컬 기동 성공 (수동 L2 확인은 선택)
- [ ] **T9. L1 전체 검증**
      · 명령: `./gradlew ktlintCheck test build`
      · 결과: 모든 테스트 Green + ktlint 통과
- [ ] **T10. 커밋**
      · 메시지: `feat(security): add Spring Security + JWT skeleton (#31)` (Verified: L1)
      · ⚠️ 사용자 명시 승인 후에만 실행 — 자동 커밋 금지
- [ ] **T11. PR 생성 → _pr-flow 위임**
      · 바디: Closes #31, 접근(옵션 B), 영향 파일, L1 결과 요약
      · 라벨 전환은 _pr-flow가 담당(in-progress → in-review)

## 5. 검증 계획

- **레벨**: L1 (`./gradlew ktlintCheck test build`) — 이슈 명세와 일치.
- **명령**:
  - 각 T: `./gradlew test --tests "<class>"` 로 좁게 돌림
  - 최종 T9: `./gradlew ktlintCheck test build`
- **롤백 포인트**: 브랜치 시작점 `90dd6e3` (origin/main HEAD).
- **보안 테스트**: T5에서 Masker 통과 검증 명시(토큰 원문 로그 유출 방지).
- **보안 fail-fast**: T1에서 32bytes 미만 secret 거부(배포 사고 방지).

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**: 토큰 원문·비밀번호·email 로그 금지. T5·T6에서 `SensitiveDataMasker.token()` 사용 명시.
- **security-logging §1**: HS256 + 32bytes↑ + payload 최소화(`sub`, `role`, `exp`, `iat`). PII payload 금지.
- **security-logging §2**: BCryptPasswordEncoder Bean만 노출 — PasswordEncoder 추상은 Spring 제공.
- **error-handling**: 이 PR에서는 Filter 내부 ApiException throw 금지(401 분기는 E2.4). GlobalExceptionHandler/EntryPoint 수정 없음.
- **spring-layering**: common/security는 모든 레이어가 쓰는 횡단 관심사. 다른 BC(auth 등)가 참조만 하고 역참조 안 함.
- **kotlin-style**: ktlint 통과. `data class` 사용 (JPA 엔티티 아님 — Properties/Util이므로 안전).
- **tdd-service**: 각 Task에 Red→Green 사이클 명시.
- **PII in plan**: 이 plan 파일에 토큰·이메일·비밀번호 원문 없음 ✓.

## 7. 범위 밖 (의도적 제외 — E2.4 등 후속 이슈)

- 401 분기 (`AUTH-401-001` EXPIRED_TOKEN 응답) — E2.4
- Refresh 토큰 발급·회전·`refresh_tokens` 테이블 — E2.3
- 보호 엔드포인트 `authenticated()` 전환 — E2.4
- User 엔티티·Repository — E2.1
- 통합 테스트(Testcontainers) — E1.3 (이번엔 단위/슬라이스만)