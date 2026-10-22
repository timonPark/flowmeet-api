---
issueKey: "#35"
issueTitle: "[S1-E2.3] POST /api/auth/refresh (회전) + POST /api/auth/logout"
type: "FEAT"
jobType: backend
agent: "feat-developer"
assignee: "ParkJH0514"
branch: "feature/35-auth-refresh-logout"
baseBranch: "main"
startedAt: "2026-10-21 14:50"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/35"
prUrl: ""
---

# #35 — [S1-E2.3] POST /api/auth/refresh (회전) + POST /api/auth/logout

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#35` |
| 유형 | FEAT |
| 담당자 | `ParkJH0514` |
| 브랜치 | `feature/35-auth-refresh-logout` |
| 베이스 | `main` |
| 시작 | 2026-10-21 14:50 KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: `status:todo`
- 본문 키워드: 신규 API 2개(`POST /api/auth/refresh`, `POST /api/auth/logout`), 재사용 감지, 전 세션 revoke
- 판정: `FEAT` (신규 엔드포인트 + 세션 하이재킹 방어 로직)

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | issue-kickoff | 14:47 | 14:50 | 3m | ✅ |
| 2 | brainstormer | 16:00 | 16:20 | 20m | ✅ |
| 3 | plan-writer | 16:20 | 16:30 | 10m | ✅ |
| 4 | feat-developer (T1-T13 TDD) | 16:30 | 10:05 | ~90m | ✅ |
| 5 | L1·L2 검증 | 10:02 | 10:06 | 4m | ✅ |
| 6 | codex-review-triage + 반영 (Finding 3·4) | 10:15 | 10:25 | 10m | ✅ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| 2026-10-21 14:47 | 🚀 작업 시작 | work-manager 진입 |
| 2026-10-21 14:50 | ✅ 브랜치 생성 | `feature/35-auth-refresh-logout` (origin/main 03664d9에서 분기) |
| 2026-10-21 14:50 | 🏷️ 라벨 전환 | `status:todo` → `status:in-progress` |
| 2026-10-22 00:05 | ✅ 플랜 승인 | docs/plans/2026-10-21-35-auth-refresh-logout.md (옵션 A, L2, Task 18) |
| 2026-10-22 10:06 | ✅ L2 검증 통과 | build + bootRun /actuator/health UP + refresh/logout smoke |
| 2026-10-22 10:25 | ✅ codex-review-triage | Finding 3·4 반영 (테스트 보강). Finding 1·2(동시성)는 follow-up 이슈로 이월 |

## 작업 상세

- Contract 참조: `docs/sprints/S1/api-contract.md` §1 API-E2-02 (refresh), API-E2-03 (logout)
- Plan 참조: `docs/sprints/S1/backend-plan.md` §2 S1-E2.3
- BlockedBy #34: AuthService + RefreshToken Entity + Hasher (완료 — 01fc7ae)
- Blocks: 없음 (FE S1-WF3.2 로그아웃만 참조)
- 접근 방식: 옵션 A — AuthService에 refresh/logout 2 메서드 추가 + `RefreshCookieManager`로 쿠키 조립·추출 공통화 (brainstormer 결정)
- 사전 합의: logout 인증은 엔드포인트 수동 체크 (SecurityConfig permitAll 유지), Bearer-only 로그아웃 시 해당 user의 모든 활성 refresh revoke

## 메모 / 특이사항

- **Refresh 회전**: refresh 사용 시 기존 즉시 revoke + 새 refresh 발급
- **재사용 감지**: 이미 revoke된 refresh로 재요청 시 → `AUTH-410 REFRESH_TOKEN_REVOKED` + 해당 user의 **전 refresh revoke** (세션 하이재킹 방어)
- `AUTH-401-001 EXPIRED_TOKEN`: 만료 refresh (M-02 트리거)
- 응답에 `user` 미포함 (PII 노출 방지, FE는 `/me`로 별도 조회)
- 로그아웃: cookie의 refresh revoke + Set-Cookie Max-Age=0
