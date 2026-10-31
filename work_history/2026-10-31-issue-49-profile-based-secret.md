---
issueKey: "#49"
issueTitle: "[chore] profile-based secret management (jwt.secret + refresh.pepper)"
type: "CHORE"
jobType: backend
agent: "manual (work-manager 직접 수행)"
assignee: "ParkJH0514"
branch: "chore/49-profile-based-secret"
baseBranch: "main"
startedAt: "2026-10-31 16:21"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/49"
prUrl: "https://github.com/timonPark/flowmeet-api/pull/54"
---

# #49 — [chore] profile-based secret management (jwt.secret + refresh.pepper)

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#49` |
| 유형 | CHORE (설정/보안 운영 가드) |
| 담당자 | `ParkJH0514` |
| 브랜치 | `chore/49-profile-based-secret` |
| 베이스 | `main` |
| 시작 | 2026-10-31 16:21 KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: `status:todo`
- 제목 키워드: `[chore]` prefix, "secret management"
- 본문 요지: `application.yml`의 공개 기본값(`change-me-...`) 패턴을 profile 분리로 교체, 운영에서 env var 누락 시 기동 실패시키기
- 판정: `CHORE` (운영 설정/보안 가드 — 내부 비즈니스 로직 없음, 설정 체계·문서 중심)
- 라우팅: `doc-updater` 1차 후보. 다만 `@ConfigurationProperties` 검증 로직이 Kotlin 코드로 들어가면 `feat-developer` 전환 가능성.

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | issue-kickoff | 16:21 | 16:21 | ~0m | ✅ |
| 2 | work_history 작성 | 16:21 | 16:22 | 1m | ✅ |
| 3 | 라벨 전환 | 16:22 | 16:22 | ~0m | ✅ |
| 4 | brainstormer | 16:22 | 16:25 | 3m | ✅ |
| 5 | plan-writer | 16:25 | 16:28 | 3m | ✅ |
| 6 | 구현 (T1~T5) | 16:28 | 16:29 | 1m | ✅ |
| 7 | L1 검증 (ktlintCheck + test) | 16:29 | 16:30 | 1m | ✅ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| 2026-10-31 16:21 | 🚀 작업 시작 | work-manager 진입 |
| 2026-10-31 16:21 | ✅ 브랜치 생성 | `chore/49-profile-based-secret` (HEAD d759dda) |
| 2026-10-31 16:22 | 🏷️ 라벨 전환 | `status:todo` → `status:in-progress` |
| 2026-10-31 16:28 | ✅ 플랜 승인 | `docs/plans/2026-10-31-49-profile-based-secret.md` (T6 skip) |
| 2026-10-31 16:28 | 🔁 라우팅 재조정 | doc-updater charter 밖(runtime config 변경) → work-manager 직접 수행 |
| 2026-10-31 16:30 | ✅ 구현 완료 | T1~T5, L1 (ktlintCheck + test 110 pass) |
| 2026-10-31 16:31 | 📤 커밋·푸시 | 98e078d, origin/chore/49-profile-based-secret |
| 2026-10-31 16:31 | 🔀 PR 생성 | #54 (https://github.com/timonPark/flowmeet-api/pull/54) |
| 2026-10-31 16:31 | 🏷️ 라벨 전환 | `status:in-progress` → `status:in-review` |
| 2026-10-31 16:57 | 🔍 codex 리뷰 Triage | high×1 / medium×1 / low×1 — 모두 유효 판정, 반영 결정 |
| 2026-10-31 16:57 | ✅ 리뷰 반영 | F1(profile default 제거) + F1b(Test task env) + F2(local cookie override) + F3(SecretProfileStartupTest 4 cases) + F4(CLAUDE.md), L1 114 pass |

## 작업 상세

- 접근 방식: **옵션 B** — profile 분리 (local/prod) + base default=local
  - base `application.yml`: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`, `jwt.secret`·`refresh.pepper` 선언 제거
  - `application-local.yml` 신규 — 로컬 전용 default
  - `application-prod.yml` 신규 — `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` (no default)
  - `build.gradle.kts` bootRun 기본 profile=local
  - CLAUDE.md: prod 배포 가이드 추가

## 메모 / 특이사항

- codex 리뷰 (medium) 지적 사항. 참조: `codereview/20261021/feature-34-auth-login-refresh.md` §지적사항 #3
- 상위 규칙: `security-logging`, TRD §11
