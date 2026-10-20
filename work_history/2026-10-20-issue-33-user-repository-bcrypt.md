---
issueKey: "#33"
issueTitle: "[S1-E2.1] User 엔티티·Repository + BCrypt 비번 저장"
type: "FEAT"
jobType: backend
agent: "feat-developer"
assignee: "ParkJH0514"
branch: "feature/33-user-repository-bcrypt"
baseBranch: "main"
startedAt: "2026-10-20 13:44"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/33"
prUrl: ""
---

# #33 — [S1-E2.1] User 엔티티·Repository + BCrypt 비번 저장

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#33` |
| 유형 | FEAT |
| 담당자 | `ParkJH0514` |
| 브랜치 | `feature/33-user-repository-bcrypt` |
| 베이스 | `main` |
| 시작 | 2026-10-20 13:44 KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: `status:todo`
- 본문 키워드: `User` Entity 신규 + `UserRepository.findByEmail` + `PasswordEncoder` 빈 + slice test — 신규 레이어·보안 구성
- backend-plan.md §2: 명시적으로 `type: FEAT`, 1.0 MD, cx: normal, blockedBy S1-E1.1+E1.2 (둘 다 해소)
- 판정: **FEAT** (신규 도메인·인프라·설정 bean)

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | 선행 chore PR (#46) | 13:40 | 13:43 | 3m | ✅ |
| 2 | issue-kickoff | 13:43 | 13:44 | 1m | ✅ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| 2026-10-20 13:40 | 🚀 작업 시작 | work-manager 진입 (#33) |
| 2026-10-20 13:40 | ⚠️ 선행 처리 | #32 완료 메타 → #46 chore PR 분리 |
| 2026-10-20 13:43 | 🚢 선행 PR 머지 | #46 (e617352) — 사용자 명시 승인 |
| 2026-10-20 13:44 | ✅ 브랜치 생성 | `feature/33-user-repository-bcrypt` (base: main, HEAD: e617352) |
| 2026-10-20 13:44 | 🏷️ 라벨 전환 | `status:todo` → `status:in-progress` |
| 2026-10-20 13:48 | 🧠 brainstormer | A/A/A 선택 (Role 패턴 일관, 전체 컬럼, 직접 encoder). PasswordEncoder는 #31에서 이미 등록됨 발견. |
| 2026-10-20 13:50 | ✅ 플랜 승인 | docs/plans/2026-10-20-33-user-repository-bcrypt.md (Task 9, L1) |
| 2026-10-20 13:51 | 🔴 Red | UserTest + UserJpaRepositoryTest 작성 → compile fail 확인 |
| 2026-10-20 13:52 | 🟢 Green | User.kt + UserJpaRepository.kt 구현 → 7 tests passed |
| 2026-10-20 13:52 | 🧪 L1 | ktlintFormat + build → BUILD SUCCESSFUL, 65 tests passed |
| 2026-10-20 13:53 | 🧪 L2 | bootRun → ddl-auto=validate 통과, health UP |

## 작업 상세

### 영향 범위 (예상)
- `src/main/kotlin/com/flowmeet/auth/domain/User.kt` — 신규 (`@Entity class`, NOT data class — CLAUDE.md 규칙)
- `src/main/kotlin/com/flowmeet/auth/infrastructure/persistence/UserRepository.kt` — 신규 (`findByEmail`)
- `src/main/kotlin/com/flowmeet/common/config/PasswordEncoderConfig.kt` — 신규 (BCrypt bean)
- `src/test/.../UserRepositoryTest.kt` — slice test (`@DataJpaTest` + Testcontainers)
- 선택: `PasswordHasher` 서비스 (encoder wrapper)

### DB 스키마 참조 (database/schema.sql:39-52)
users 컬럼: id (UUID), name (VARCHAR(100)), email (unique VARCHAR(255)), password_hash (TEXT), team_id (nullable FK), role_id (nullable FK), manager_id (self FK nullable), system_permission (enum ADMIN/USER), temporary_password (bool), active (bool), created_at, updated_at.

### 참조
- `docs/sprints/S1/backend-plan.md` §2 — S1-E2.1 (FEAT, 1.0 MD, cx normal)
- `docs/sprints/S1/api-contract.md` — User 응답 shape (login/me)
- Skill: [spring-layering](../.claude/skills/spring-layering/SKILL.md), [jpa-query-guard](../.claude/skills/jpa-query-guard/SKILL.md), [security-logging](../.claude/skills/security-logging/SKILL.md), [tdd-service](../.claude/skills/tdd-service/SKILL.md), [error-handling](../.claude/skills/error-handling/SKILL.md), [kotlin-style](../.claude/skills/kotlin-style/SKILL.md)

## 메모 / 특이사항

- Team/Role Entity는 **S1-E3.1 (#37) 이후** 생성 — User는 team_id/role_id를 **nullable UUID 컬럼으로만** 보유 (@ManyToOne 지연).
- BCrypt bean은 `common/config` 하위에 둬서 Spring Security 전역 bean으로 노출.
- 접근 방식 (brainstormer): **A/A/A** — ① Spring Data interface 직접 (Role 패턴) ② 전체 컬럼 매핑 ③ PasswordEncoder bean 직접.
