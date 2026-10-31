---
issueKey: "#35"
issueTitle: "[S1-E2.3] POST /api/auth/refresh (회전) + POST /api/auth/logout"
slug: "auth-refresh-logout"
author: "ParkJH0514"
createdAt: "2026-10-21 16:30"
chosenApproach: "옵션 A — AuthService에 refresh/logout 2 메서드 추가 + RefreshCookieManager로 쿠키 조립·추출 공통화"
verifyLevel: "L2"
status: DONE
approvedAt: "2026-10-22 00:05"
executedAt: "2026-10-22 10:30"
doneAt: "2026-10-22 10:30"
prUrl: "https://github.com/timonPark/flowmeet-api/pull/52"
mergeCommit: "f424c53"
---

# Plan — #35 [S1-E2.3] POST /api/auth/refresh (회전) + POST /api/auth/logout

## 1. 목표 (DoD)

- `POST /api/auth/refresh` 신규 — HttpOnly 쿠키의 refresh로 **access + refresh 회전** 발급. 응답에 `user` 미포함.
  - 성공 200: `{accessToken, accessTokenExpiresIn, tokenType}` + Set-Cookie 신규 refresh
  - 쿠키 없음 → `401 COMMON-401`
  - 만료 refresh → `401 AUTH-401-001 EXPIRED_TOKEN` (FE M-02 트리거)
  - revoke된 refresh 재사용 감지 → `401 AUTH-410 REFRESH_TOKEN_REVOKED` + **해당 user의 모든 활성 refresh revoke** (세션 하이재킹 방어)
- `POST /api/auth/logout` 신규 — 쿠키 또는 Bearer 중 하나로 세션 식별, refresh revoke + Set-Cookie Max-Age=0.
  - 성공 204, body 없음
  - 쿠키 우선 revoke / Bearer-only이면 **해당 user의 모든 활성 refresh revoke** (로그에 WARN)
  - 쿠키·Bearer 둘 다 없음 → `401 COMMON-401` (SecurityConfig는 permitAll 유지, 엔드포인트 내부 수동 체크)
- 신규 `ErrorCode.REFRESH_TOKEN_REVOKED` (`AUTH-410`, HTTP 401)
- `RefreshToken.revoke(at)` 도메인 메서드 + Repository `revokeAllActiveByUserId(userId, at)` 벌크 UPDATE
- Spring Security `AuthenticationPrincipal` 아닌, `SecurityContextHolder` 를 통해 Bearer 매칭된 userId를 가져옴 (JwtAuthenticationFilter가 subject=userId 세팅)
- TDD(Red → Green → Refactor): 회전·재사용 감지·만료·멱등 로그아웃 시나리오 테스트 선작성

## 2. 선택된 접근

**옵션 A** — AuthService에 `refresh()`·`logout()` 메서드 추가 + `RefreshCookieManager`를 신규 두어 쿠키 조립·삭제·추출을 Controller 3곳(login/refresh/logout)에서 재사용. 서비스 분리는 하지 않음(2 유스케이스에 과잉). 재사용 감지는 벌크 UPDATE 1발.

## 3. 영향 파일

### 신규
- `src/main/kotlin/com/flowmeet/auth/api/dto/RefreshResponse.kt` — refresh 성공 응답 DTO (`accessToken`, `accessTokenExpiresIn`, `tokenType`)
- `src/main/kotlin/com/flowmeet/auth/api/support/RefreshCookieManager.kt` — 쿠키 build/clear/extract 공통 유틸
- `src/main/kotlin/com/flowmeet/auth/application/RefreshRotationResult.kt` — refresh 성공 결과(access + 신규 refresh raw)
- `src/test/kotlin/com/flowmeet/auth/application/AuthServiceRefreshTest.kt` — refresh 로직 단위 테스트(MockK)
- `src/test/kotlin/com/flowmeet/auth/application/AuthServiceLogoutTest.kt` — logout 단위 테스트
- `src/test/kotlin/com/flowmeet/auth/api/AuthControllerRefreshLogoutMvcTest.kt` — @WebMvcTest 슬라이스 테스트
- `src/test/kotlin/com/flowmeet/auth/AuthRefreshLogoutIntegrationTest.kt` — Testcontainers 통합(회전 + 재사용 감지 전 세션 revoke 검증)

### 수정
- `src/main/kotlin/com/flowmeet/common/exception/ErrorCode.kt` — `REFRESH_TOKEN_REVOKED("AUTH-410", 401, …)` 추가
- `src/main/kotlin/com/flowmeet/auth/domain/RefreshToken.kt` — `fun revoke(at: Instant)` + `fun isActive(now: Instant): Boolean` + `fun isExpired(now: Instant): Boolean` 추가
- `src/main/kotlin/com/flowmeet/auth/infrastructure/persistence/RefreshTokenJpaRepository.kt` — `@Modifying @Query`로 `revokeAllActiveByUserId(userId, at)` 추가
- `src/main/kotlin/com/flowmeet/auth/application/AuthService.kt` — `rotate(rawRefresh)` + `logout(rawRefresh?, bearerUserId?)` 메서드 추가
- `src/main/kotlin/com/flowmeet/auth/api/AuthController.kt` — `POST /refresh`·`POST /logout` 라우트 + `RefreshCookieManager` 도입(login도 매니저로 교체)

### 레이어
- api(dto·controller·support), application(service·result), domain(entity method), infrastructure(repository), common(ErrorCode)

## 4. Task

> **원칙**: Red → Green → Refactor. 테스트는 반드시 **실패 확인 후** 구현.

- [ ] T1. `ErrorCode.REFRESH_TOKEN_REVOKED` 추가 — `common/exception/ErrorCode.kt`
      · 검증: 컴파일 성공 (`./gradlew ktlintCheck`)
- [ ] T2. `RefreshToken` 도메인 메서드 추가(`revoke`, `isActive`, `isExpired`) — `auth/domain/RefreshToken.kt`
      · 검증: 유닛 테스트 (`RefreshTokenTest.kt`에 추가) — revoke idempotent 확인
- [ ] T3. `RefreshTokenJpaRepository.revokeAllActiveByUserId` 추가 — `auth/infrastructure/persistence/RefreshTokenJpaRepository.kt`
      · `@Modifying(clearAutomatically=true) @Query("update RefreshToken r set r.revokedAt=:at where r.userId=:userId and r.revokedAt is null and r.expiresAt > :at")`
      · 검증: Testcontainers DataJpaTest로 벌크 UPDATE 반환 행수 확인
- [ ] T4. AuthService refresh 실패 시나리오 테스트 작성(RED) — `AuthServiceRefreshTest.kt`
      · 케이스: 쿠키 미존재 raw, hash 미매칭(unknown), 이미 revoke됨(→ AUTH-410 + revokeAll 호출), 만료됨(→ AUTH-401-001)
      · `./gradlew test --tests "...AuthServiceRefreshTest"` 결과 FAILED 확인
- [ ] T5. AuthService refresh 성공 시나리오 테스트 추가(RED) — `AuthServiceRefreshTest.kt`
      · 기존 refresh revoke 호출 + 신규 refresh 저장 + 반환값 shape 검증
      · FAILED 확인
- [ ] T6. `AuthService.rotate(rawRefresh)` 구현(GREEN) — `AuthService.kt`
      · hash → findByTokenHash → isExpired/isRevoked 체크 → 재사용이면 revokeAllActiveByUserId + AUTH-410 throw → 정상이면 기존 revoke + 신규 생성 → access 재발급 + `RefreshRotationResult`
      · T4·T5 테스트 PASS
- [ ] T7. AuthService logout 테스트 작성(RED) — `AuthServiceLogoutTest.kt`
      · 케이스: raw 쿠키만 있음(→ 그 토큰 revoke), bearer userId만 있음(→ revokeAllActiveByUserId 호출), 둘 다 null(→ ApiException COMMON-401), 이미 revoke된 토큰(→ 멱등, 추가 revoke 없음)
      · FAILED 확인
- [ ] T8. `AuthService.logout(rawRefresh?, bearerUserId?)` 구현(GREEN) — `AuthService.kt`
      · raw → hash → 찾으면 revoke(멱등). raw가 null이고 userId만 있으면 revokeAllActive. 둘 다 null이면 COMMON-401 throw. userId-only 경로는 WARN 로그.
      · T7 테스트 PASS
- [ ] T9. `RefreshCookieManager` 신규 — `auth/api/support/RefreshCookieManager.kt`
      · `build(rawRefresh): ResponseCookie` / `clear(): ResponseCookie` / `extract(request): String?`
      · Secure 토글은 RefreshTokenProperties, Max-Age는 JwtProperties 참조
- [ ] T10. `AuthController.login`을 매니저 사용으로 리팩터 — `AuthController.kt`
      · 외부 응답(ResponseCookie 헤더 문자열) 동일 유지 확인
      · 기존 login MVC 테스트 PASS 유지
- [ ] T11. `AuthController` MVC 슬라이스 테스트(RED) — `AuthControllerRefreshLogoutMvcTest.kt`
      · refresh 200 shape · 쿠키 미존재 401 · 재사용 410 · 만료 401-001
      · logout 204 · 쿠키·Bearer 모두 없음 401
      · FAILED 확인
- [ ] T12. `AuthController.refresh` / `AuthController.logout` 라우트 구현(GREEN) — `AuthController.kt`
      · refresh: `@CookieValue("refreshToken", required=false)` → 없으면 COMMON-401 → `authService.rotate` → RefreshResponse + Set-Cookie
      · logout: 쿠키 + `SecurityContextHolder` subject(userId as UUID) → `authService.logout` → ResponseEntity.noContent() + clear 쿠키
      · T11 테스트 PASS
- [ ] T13. 통합 테스트 추가 — `AuthRefreshLogoutIntegrationTest.kt`
      · 시나리오 1: login → refresh (쿠키·access 바뀜 확인) → 이전 refresh로 refresh 재시도 → AUTH-410 + 전 refresh revoke 확인(DB)
      · 시나리오 2: login → logout(쿠키) → DB revokedAt 세팅 + 쿠키 Max-Age=0 확인
- [ ] T14. 민감정보·로깅 체크(security-logging §4) — Service·Controller 로그 라인 재검토
      · refresh raw / 쿠키 raw는 로그 금지. userId/errorCode만.
- [ ] T15. ktlintFormat + build — `./gradlew ktlintFormat && ./gradlew build`
      · L1 통과(Verified: L1)
- [ ] T16. bootRun + health smoke — `./gradlew bootRun` (별도 터미널 가정) + `curl -s localhost:8080/actuator/health`
      · 200 UP 확인. 종료 후 Verified: L2 커밋 메시지 반영.
- [ ] T17. 커밋 — Conventional Commits 형식, Verified: L2 포함. **사용자 명시 승인 후에만 실행**.
- [ ] T18. PR 생성 → `_pr-flow.md`로 위임

## 5. 검증 계획

- 레벨: **L2** (DB·보안 변경 포함 — plan-writer §2 상향 규칙)
- 명령:
  - L0: `./gradlew ktlintCheck`
  - L1: `./gradlew build` (ktlintCheck + test 포함)
  - L2: `./gradlew bootRun` + `curl -s http://localhost:8080/actuator/health | jq .status` → `"UP"`
- 롤백 포인트: `main`(03664d9) — 브랜치 시작점. 각 Task 완료마다 작업 커밋을 쪼개도 되지만, 머지는 squash 전제라 로컬 단위는 자유.

## 6. 보안·관련 규칙 체크

- **TRD §11 민감정보**: refresh raw / 쿠키 raw / Bearer raw는 로그·응답 금지. userId·errorCode·마스킹된 식별자만. plan 본문·work_history 본문도 동일.
- **spring-layering**: Controller는 Entity 반환 금지 — RefreshResponse DTO 사용. 트랜잭션은 Service에만 (`@Transactional`).
- **error-handling**: `ApiException(ErrorCode.*)` + GlobalExceptionHandler. 수동 ResponseEntity.status(...) 금지.
- **jpa-query-guard**: `revokeAllActiveByUserId`는 `@Modifying @Query` 벌크 UPDATE. `clearAutomatically=true`로 영속성 컨텍스트 flush. `expires_at > :at`로 만료 제외하여 불필요 UPDATE 방지(인덱스 사용 가능하도록 user_id · revoked_at 복합 조건 고려 — 추후 성능 이슈로 추가 인덱스 필요하면 별도 티켓).
- **tdd-service**: 각 Service 메서드는 단위 테스트(MockK) + 통합 테스트(Testcontainers). Red → Green → Refactor.
- **security-logging**: `log.info("auth.refresh userId=...")` 같이 식별자 중심, raw는 절대 금지. logout의 bearer-only revoke 경로는 WARN 레벨로 "cookie absent, revoking all active" 기록.
