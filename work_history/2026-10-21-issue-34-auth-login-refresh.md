---
issueKey: "#34"
issueTitle: "[S1-E2.2] POST /api/auth/login + Refresh 발급·쿠키"
type: "FEAT"
jobType: backend
agent: "feat-developer"
assignee: "ParkJH0514"
branch: "feature/34-auth-login-refresh"
baseBranch: "main"
startedAt: "2026-10-21 12:34"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/34"
prUrl: ""
---

# #34 — [S1-E2.2] POST /api/auth/login + Refresh 발급·쿠키

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#34` |
| 유형 | FEAT |
| 담당자 | `ParkJH0514` |
| 브랜치 | `feature/34-auth-login-refresh` |
| 베이스 | `main` |
| 시작 | 2026-10-21 12:34 KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: `status:todo`
- 본문 키워드: "신규 API", `AuthController`/`AuthService.login` 신규, `refresh_tokens` 테이블·Entity·Repository 신규
- 판정: `FEAT` (S1-E2.1 완료를 전제로 로그인 엔드포인트와 Refresh 토큰 저장소 신규 구현)

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | issue-kickoff | 12:30 | 12:34 | 4m | ✅ |
| 2 | work_history + 라벨 | 12:34 | 12:38 | 4m | ✅ |
| 3 | brainstormer | 12:38 | 12:42 | 4m | ✅ |
| 4 | plan-writer + 승인 | 12:42 | 12:50 | 8m | ✅ |
| 5 | feat-developer (TDD) | 12:50 | 13:56 | 66m | ✅ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| 2026-10-21 12:30 | 🚀 작업 시작 | work-manager 진입 |
| 2026-10-21 12:32 | 🧹 dirty worktree 정리 | #33 메타 파일 2개 → `docs/33-finalize-metadata` 브랜치로 분리, PR #48 (사용자 승인 전까지 머지 보류) |
| 2026-10-21 12:34 | ✅ 브랜치 생성 | `feature/34-auth-login-refresh` (origin/main 6a09495에서 분기) |
| 2026-10-21 12:34 | 🏷️ 라벨 전환 | `status:todo` → `status:in-progress` |
| 2026-10-21 12:50 | ✅ 플랜 승인 | `docs/plans/2026-10-21-34-auth-login-refresh.md` (옵션 B, Task 12개, L1) |
| 2026-10-21 13:56 | ✅ feat-developer 완료 | TDD(Red→Green) 완료, L1 검증 통과 (79 tests passed, ktlintCheck + build 모두 green) |
| 2026-10-21 14:33 | 🔍 codex 2차 리뷰 | 4 지적(high 2 / medium 1 / low 1) + 4 제안. codereview/20261021/feature-34-auth-login-refresh.md |
| 2026-10-21 14:40 | 🛠️ triage 반영 | #1 timing-safe login(dummy BCrypt) + #2 cookie-secure 운영 기본 true + #4 toString() 민감정보 가림 + Q3 refreshTokenTtl 통합. L1 재검증 80 tests passed. #3(운영 pepper 누락 감지)는 jwt.secret과 공통 패턴이라 별도 이슈 #49로 분리. |

## 작업 상세

- Contract 참조: `docs/sprints/S1/api-contract.md` §0.3·0.5·§1 API-E2-01
- Plan 참조: `docs/sprints/S1/backend-plan.md` §2 S1-E2.2
- 공통 가드: `security-logging` (비번/토큰 로그 금지), `error-handling` (`AUTH-401` 통일), `spring-layering`
- BlockedBy #33: User 엔티티·BCryptPasswordEncoder bean (완료 — 6a09495)
- Blocks: #35 (refresh/logout), #36 (JwtAuthenticationFilter)
- 접근 방식: 옵션 B — HMAC-SHA256(pepper) refresh hash + Role 별도 조회. `profileComplete`는 S1 contract 전제대로 하드코딩 `true`.

## 메모 / 특이사항

- Refresh 토큰은 **opaque + 해시 저장** (DB에 raw 저장 금지).
- `AUTH-401 INVALID_CREDENTIALS` — 이메일/비번 틀림 구분 없이 동일 메시지로 사용자 열거 공격 방지.
