---
sprintId: S1
rev: 1
status: DRAFT
capacityMD: 8
capacityLimitMD: 6.4
estimateMD: 9.9
bufferMD: 2.0
contractRev: v1
proposedAt: 2026-10-19 10:32
proposedBy: backend-developer
---

# [S1] Backend Plan v1 (DRAFT)

> SCOPE_DECISION(01-scope §3.4) 범위 안에서 BE Must/Should/Could 상세 분해 + 공수.
> `api-contract.md` v1과 짝. FE 합의(02-design 회의) 후 `status: FE_AGREED` → PM 제출.

## 1. 요약

**Must(E1+E2+E3) 합계 9.9 MD** — 상한 6.4 MD 명백 초과. ESCALATE 필요.

| 묶음 | E 합(MD) | 상한 |
|---|---|---|
| Must (W1+W2+W3-seed) | **9.9** | 6.4 |
| Should (W5) | **1.8** | - |
| Could (M-01 B / SCR-10_2 저장) | 산정 보류 (S2로 미는 전제) | - |
| 20% 버퍼 | 2.0 | - |

**축소 가능 최대치 (BE 자체 재추정 후)**: 9.9 MD 이하로 더 깎기 어려움.
- E1.1 Security 뼈대: 1/2/3 → 0.5/1.5/2.5 재추정 적용 (1.5 MD)
- E2.2 login+refresh 발급: 1/2/3 → 1/1.5/2.5 재추정 적용 (1.6 MD)
- 나머지는 TDD + Testcontainers + 보안 요구 때문에 축소 어려움.

## 2. 이슈 분해 (요약표)

| Plan ID | 제목 | type | cx | O/M/P | E(MD) | blockedBy | FE차단 | 우선순위 | W# |
|---|---|---|---|---|---|---|---|---|---|
| S1-E1 | 플랫폼 기반 (W1) 에픽 | — | — | — | — | — | — | Must | W1 |
| S1-E1.1 | Spring Security·JWT 인프라 뼈대 | FEAT | high | 0.5/1.5/2.5 | 1.5 | — | 간접 | Must | W1 |
| S1-E1.2 | Flyway 도입 + V1 baseline (schema.sql → V1__init.sql) | CHORE | normal | 0.5/1/1.5 | 1.0 | — | — | Must | W1 |
| S1-E1.3 | Testcontainers Postgres 공통 베이스 + slice test 설정 | TEST | normal | 0.5/0.5/1 | 0.6 | S1-E1.2 | — | Must | W1 |
| S1-E2 | 인증·세션 min (W2) 에픽 | — | — | — | — | — | — | Must | W2 |
| S1-E2.1 | User 엔티티·Repository + BCrypt 비번 저장 | FEAT | normal | 0.5/1/1.5 | 1.0 | S1-E1.1, S1-E1.2 | — | Must | W2 |
| S1-E2.2 | POST /api/auth/login + Refresh 발급·쿠키·DB 저장 | FEAT | high | 1/1.5/2.5 | 1.6 | S1-E2.1 | ○ | Must | W2 |
| S1-E2.3 | POST /api/auth/refresh (회전·revoke 재사용 감지) + POST /api/auth/logout | FEAT | normal | 0.5/1/1.5 | 1.0 | S1-E2.2 | ○ | Must | W2 |
| S1-E2.4 | JwtAuthenticationFilter + 401 코드 분기 (AUTH-401-001 EXPIRED_TOKEN) | FEAT | normal | 0.5/1/1.5 | 1.0 | S1-E2.2 | ○ | Must | W2 |
| S1-E3 | W3-seed 에픽 | — | — | — | — | — | — | Must | W3-seed |
| S1-E3.1 | Team 엔티티·Repository + V2 마이그레이션 (teams FK 포함) | FEAT | normal | 0.5/1/1.5 | 1.0 | S1-E1.2 | — | Must | W3-seed |
| S1-E3.2 | 관리자 1명 + 역할 4종 + 조직 1개 seed (R__seed.sql 또는 CommandLineRunner) | CHORE | normal | 0.5/0.5/1 | 0.6 | S1-E2.1, S1-E3.1 | ○ | Must | W3-seed |
| S1-E3.3 | GET /api/auth/me (role·team·플래그 — navFlags 제외) | FEAT | normal | 0.5/0.5/1 | 0.6 | S1-E2.4, S1-E3.2 | ○ | Must | W3-seed |
| S1-E5 | 공통 셸 지원 (W5·Should) 에픽 | — | — | — | — | — | — | Should | W5 |
| S1-E5.1 | /me 응답에 navFlags 추가 | FEAT | normal | 0.3/0.5/1 | 0.6 | S1-E3.3 | ○ | Should | W5 |
| S1-E5.2 | GET /api/nav/counts (하드코딩 0) | FEAT | normal | 0.3/0.5/1 | 0.6 | S1-E2.4 | ○ | Should | W5 |
| S1-E5.3 | GET /api/dashboard?dashboardType=MEETING|TEAM skeleton | FEAT | normal | 0.3/0.5/1 | 0.6 | S1-E2.4 | ○ | Should | W5 |

### 2.1 Must 합계
- E1: 1.5 + 1.0 + 0.6 = 3.1
- E2: 1.0 + 1.6 + 1.0 + 1.0 = 4.6
- E3: 1.0 + 0.6 + 0.6 = 2.2
- **합계: 9.9 MD** (상한 6.4 초과 — ESCALATE)

### 2.2 Should 합계
- E5: 0.6 + 0.6 + 0.6 = 1.8 MD

### 2.3 cut line 제안 (PM 판정 요청용)
Must 안에서는 더 깎기 어려움. ESCALATE 시 PM에 제시할 옵션:

**옵션 A**: Must 9.9 MD 그대로 승인 — BE 1인 가용 6.4 MD 상한을 **9.9/6.4 ≈ 1.55배** 초과. 2주 상한 돌파 → 스프린트 연장 또는 기능 축소 필요.

**옵션 B** (BE 추천): E1.3 (Testcontainers 공통 베이스)를 S2 선행 작업으로 뺀다 (-0.6) + E3.1 (Team 엔티티)을 "뼈대 schema만 Flyway에 포함, Repository는 S2"로 축소 (-0.5) → **Must 8.8 MD**. 여전히 상한 초과이나 "Flyway + Security + login 왕복 + /me" 핵심은 유지. Testcontainers 없이는 통합 테스트 품질이 떨어지므로 **S1 범위 안의 테스트는 slice test만** 수행하고 통합은 S2 Must로.

**옵션 C**: W3-seed 전체를 S2 Must로 미루고 S1은 W1+W2만 (**Must 7.7 MD**). 로그인은 **in-memory 사용자(application.yml 하드코딩)**로 end-to-end 데모. /me는 in-memory에서 조립. S2에서 DB seed + /me DB 전환. → FE는 로그인 플로우/셸까지 완료 가능.

→ **BE는 옵션 B 선호**. 옵션 C는 "seed된 DB 사용자로 로그인"이라는 S1 목표(scope.md §3 D1) 자체를 흔듦.

## 3. 리스크 · 가정

### 리스크
- **R1**: Must 공수 상한 초과 — PM ESCALATE 필수. 미결 시 S1 완결 불가.
- **R2**: Flyway 도입 시점이 `schema.sql`과 분기될 가능성 — `database/schema.sql` 과 `src/main/resources/db/migration/V1__init.sql` 를 **동일 내용으로 유지**, 변경 시 양쪽 수정 규칙을 CLAUDE.md에 추가 필요 (S1-E1.2 PR에 포함).
- **R3**: JWT_SECRET 로컬 기본값(현재 `application.yml` 가정) — 운영 전환 시 반드시 교체. `application.yml` 샘플 + 환경변수 override 패턴 S1-E1.1 PR에 포함.
- **R4**: `SameSite=Lax + no CSRF 토큰` 전략이 S1 가정. 운영 배포 전 보안 리뷰 1회 필요 (S2 초입).
- **R5**: refresh 재사용 감지 시 "전 세션 revoke"가 UX 공격면 (악의 사용자가 피해자 refresh 1회 훔쳐서 재사용하면 피해자 모든 세션 kick) — 수용 가능한 trade-off이나 S2 FAQ에 명시 필요.
- **R6**: Testcontainers CI 러너에서 Docker 가용성 — 로컬은 docker-compose 쓰므로 OK, GitHub Actions는 ubuntu-latest에서 Docker 지원 (별도 설정 불필요).

### 가정
- **A1**: 사용자가 Docker + Java 21 로컬 환경 보유 (CLAUDE.md 전제).
- **A2**: FE는 쿠키 전달 가능한 fetch 설정 사용 (`credentials: 'include'`).
- **A3**: SCR-10_1 로그인 폼에서 "이메일 기억하기"는 FE 로컬 저장만, BE API 없음.
- **A4**: 공통 응답 포맷은 `ErrorResponse` + `com.flowmeet.common.exception.*` 그대로 재사용 (신규 envelope 금지).
- **A5**: S1에서는 메일 발송 구현 전무 — 임시 비번은 seed 시 서버 로그에만 표시 (M-01 Could로 미룸).

## 4. 의존·순서

```
S1-E1.1 (Security) ─┬─> S1-E2.1 (User) ─> S1-E2.2 (login) ─┬─> S1-E2.3 (refresh/logout)
                    │                                       └─> S1-E2.4 (JWT 필터)
S1-E1.2 (Flyway) ───┼─> S1-E1.3 (TC)
                    └─> S1-E3.1 (Team) ───> S1-E3.2 (seed) ──> S1-E3.3 (/me)
                                                                      │
                                                                      └─> S1-E5.1 (navFlags)
                                                                      └─> S1-E5.2 (counts)
                                                                      └─> S1-E5.3 (dashboard)
```

**FE 차단 시점**:
- WF2(401→refresh): S1-E2.2 + E2.3 + E2.4 완료 후 실연동 가능 (mock으로 선행 OK)
- WF3(로그인): S1-E2.2 완료 후 실연동 (SCR-10_1), M-02는 E2.3 완료 후
- WF1 공통 셸(관리 메뉴 노출): S1-E5.1 완료 후 (Should)
- WF6 대시보드 shell: S1-E5.3 완료 후 (Should)

**권장 머지 순서** (FE blocking 최소화):
1주차: E1.1 → E1.2 → E2.1 → E2.2 → E2.4 (FE WF3 Realtime 연동 가능)
2주차: E2.3 → E3.1 → E3.2 → E3.3 → (여유 시) E5.1 → E5.2 → E5.3

## 5. 미결 질의 (FE 합의 또는 PM ESCALATE 필요)

| # | 내용 | 처리 |
|---|---|---|
| D-pending-1 | Must 공수 9.9 vs 상한 6.4 — 옵션 B 승인 요청 | PM ESCALATE |
| D-pending-2 | FE Q6: 쿠키 속성·CSRF 전략 — api-contract §0.3 안으로 OK? | FE 합의 |
| D-pending-3 | FE Q7: requirePasswordChange/profileComplete 플래그 위치 — 로그인 응답 `user.*` + `/me` 양쪽 포함 OK? | FE 합의 |
| D-pending-4 | FE Q8: 조직 응답 shape (flat + parentId) — api-contract §2 안으로 OK? | FE 합의 |
| D-pending-5 | FE Q9/Q10/Q11: next-intl / Radix / Pretendard — BE 영향 없음, FE 결정만 공유받음 | FE 자체 결정 |

## 6. 파일

- `flowmeet-api/docs/sprints/S1/api-contract.md` v1
- `flowmeet-api/docs/sprints/S1/backend-plan.md` v1 (이 파일)
- `flowmeet-api/docs/sprints/S1/issues/*.md` — 각 Plan ID별 이슈 본문 (FE 합의 + ESCALATE 결론 후 작성)

## 7. 변경 이력

| rev | 일시 | 변경 | 작성자 |
|---|---|---|---|
| v1 (DRAFT) | 2026-10-19 10:32 | 초안 — FE PROPOSAL 전송용. Must 9.9 MD / Should 1.8 MD. ESCALATE 안건 포함 | backend-developer |
