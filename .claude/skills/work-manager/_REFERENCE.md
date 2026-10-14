# work-manager 참조 문서

실행 로직과 무관한 참조용 정의. Claude 실행 시 자동 로드되지 않는다 (언더스코어 접두 파일).

---

## work_history frontmatter 필드 정의

| 필드 | 타입 | 설명 | 메트릭 용도 |
|---|---|---|---|
| `issueKey` | string | GitHub Issue 번호(`#123`) 또는 `PR-{번호}` | 이슈별 조회 |
| `issueTitle` | string | GitHub Issue 또는 PR 제목 | 검색·조회 |
| `type` | enum | `FEAT` · `FIX` · `PERF` · `REFACTOR` · `DOC` · `CHORE` · `TEST` · `DEPLOY-REVIEW` · `ANALYSIS` | 유형별 집계 |
| `jobType` | enum | `backend` (현재 flowmeet-api는 backend만) | 미래 확장(mobile/frontend/devops) |
| `agent` | string | 라우팅된 전문 에이전트명 (예: `feat-engineer`). **미등록 상태에서 수동 분기 중이면 `"manual"`**로 기록 — 메트릭 집계 시 자동 라우팅 완료와 구분 | 에이전트별 집계 |
| `assignee` | string | `git config user.name` 값 | 담당자별 소요 시간 |
| `branch` | string | 작업 브랜치명 (`<type>/<N>-<slug>`). DEPLOY-REVIEW는 빈 문자열 | 브랜치 추적 |
| `baseBranch` | string | 베이스 브랜치 (`main` 기본) | 환경별 집계 |
| `startedAt` | `"YYYY-MM-DD HH:mm"` | 작업 시작 시각 (KST) | 소요 시간 계산 |
| `completedAt` | `"YYYY-MM-DD HH:mm"` 또는 `""` | 완료 시각, 미완료 시 `""` | 소요 시간 계산 |
| `totalMinutes` | number | 총 소요 시간(분), 미완료 시 `0` | 소요 시간 집계 |
| `status` | enum | `IN_PROGRESS` · `COMPLETED` · `BLOCKED` | 현황 파악 |
| `issueUrl` | string | GitHub Issue 또는 PR URL (전체) | 추적·역참조 |
| `prUrl` | string | 작업 결과 PR URL (완료 시) | 결과물 추적 |

### type ↔ 브랜치 type 매핑

CLAUDE.md "작업 시작 규칙"의 브랜치 명명과 메트릭의 `type`을 1:1로 맞춘다.

| `type` (메트릭) | 브랜치 `type` | 비고 |
|---|---|---|
| `FEAT` | `feature` | 신규 기능 |
| `FIX` | `fix` | 버그 수정 |
| `PERF` | `refactor` | 성능 개선 — 외형 변경 작으면 refactor로 분류 |
| `REFACTOR` | `refactor` | 구조 개선 |
| `DOC` | `docs` | 문서·하네스 |
| `CHORE` | `chore` | 설정·의존성 |
| `TEST` | `test` | 테스트 추가·보강 |
| `DEPLOY-REVIEW` | — (브랜치 없음) | PR URL 입력 — 브랜치 불필요 |
| `ANALYSIS` | `chore` | 조사·분석 결과물. 코드 변경 작으면 chore |

### type ↔ 전문 에이전트 라우팅 (`agent` 필드)

work-manager는 Step 2 유형 판정 결과를 받아 아래 에이전트를 호출한다. 에이전트 정의는 `.claude/agents/*.md`. 호출 금지(1-depth): Claude Code가 Read 후 직접 실행.

#### 흐름 전체

```
issue-kickoff (공통) → [brainstormer] → [plan-writer] → 전문 에이전트 → PR
                      └── 복잡도별 skip ──┘
```

#### type별 매핑

| `type` | brainstormer | plan-writer | 전문 에이전트 | 비고 |
|---|---|---|---|---|
| `FEAT` (복잡: 영향 파일 3개↑ 또는 DB/보안/외부 연동) | ✅ | ✅ | [feat-developer](../../agents/feat-developer.md) | TDD 사이클 |
| `FEAT` (작음: 단일 파일·레이어) | ⬜ | ✅ (간소) | [feat-developer](../../agents/feat-developer.md) | |
| `FIX` (재현 조건 명확 + 수정 범위 좁음) | ⬜ | ✅ (간소) | [fix-developer](../../agents/fix-developer.md) | 재현 테스트 먼저 |
| `FIX` (영향 범위 넓음) | ✅ | ✅ | [fix-developer](../../agents/fix-developer.md) | |
| `REFACTOR` · `PERF` | ✅ | ✅ | [refactor-developer](../../agents/refactor-developer.md) | 외부 계약 유지 |
| `TEST` | ⬜ | ✅ (간소) | [feat-developer](../../agents/feat-developer.md) | 테스트도 TDD |
| `DOC` · `CHORE`(하네스) | ⬜ | ⬜ | [doc-updater](../../agents/doc-updater.md) | L0 |
| `ANALYSIS` (문서 산출물) | ⬜ | ✅ (간소) | [doc-updater](../../agents/doc-updater.md) | |
| `ANALYSIS` (코드 변경 포함) | ✅ | ✅ | feat-developer 또는 refactor-developer | 샘플 코드 추가 등 |
| `DEPLOY-REVIEW` | — | — | [pr-review-check](../../agents/pr-review-check.md) | PR URL 입력 전용 |

> `brainstormer`·`plan-writer` 상세: [`brainstormer.md`](../../agents/brainstormer.md) · [`plan-writer.md`](../../agents/plan-writer.md). skip 결정 기준은 `_issue-flow.md` Step 3.3/3.5 참조.

> issue 수신 직후의 **브랜치 분기 + work_history 작성**은 공통으로 [`issue-kickoff`](../../agents/issue-kickoff.md)가 수행. 그 뒤 brainstormer → plan-writer → 전문 에이전트 순으로 핸드오프.

### 금지 필드 (TRD §11 보안)

다음은 **frontmatter·본문 어디에도 쓰지 않는다**:

- 비밀번호·토큰·API 키
- 음성 원문·transcript 본문
- 회의 참가자 이메일·전화번호 원본 (식별자만 — `meeting_id`, `user_id`)

> free-form 메모가 필요하면 식별자 중심으로 쓴다. 이메일/토큰이 섞여 들어가면 **work_history 파일 저장 직전** `SensitiveDataMasker.scrub()`([common/logging/SensitiveDataMasker.kt](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt))를 반드시 통과시킨다. `gh issue view`·`gh pr view` 결과를 그대로 저장·출력하지 말 것 — body 발췌가 필요하면 scrub 후.

---

## 추출 가능한 메트릭

| 메트릭 | 추출 방법 |
|---|---|
| 유형별 평균 소요 시간 | `type` 그룹핑 + `totalMinutes` 평균 |
| 담당자별 소요 시간 | `assignee` 그룹핑 + `totalMinutes` 합산·평균 |
| 유형별 빈도 | `type` 카운트 |
| 완료율 | `status` 분포 (COMPLETED / 전체) |
| 월별 처리량 | `startedAt` 월 단위 그룹핑 + 건수 |
| 에이전트별 효율 | `agent` + `totalMinutes` + `status` 조합 |
| 블로킹 비율 | `status == BLOCKED` 비중 |
| 평균 PR 리뷰 소요 (DEPLOY-REVIEW) | `type == DEPLOY-REVIEW` 필터 + `totalMinutes` |

### 메트릭 수집 — `scripts/wh-metrics.py`

의존성 없는 Python 3.9+ 스크립트. work_history/*.md의 YAML frontmatter를 가볍게 파싱해 집계. 상세: [`scripts/wh-metrics.py`](../../../scripts/wh-metrics.py).

```bash
# 전체 요약 (status 분포 + 완료율)
python3 scripts/wh-metrics.py

# 그룹별 집계
python3 scripts/wh-metrics.py --by type         # 유형별 소요
python3 scripts/wh-metrics.py --by assignee     # 담당자별
python3 scripts/wh-metrics.py --by agent        # 에이전트별
python3 scripts/wh-metrics.py --by jobType      # 직군별
python3 scripts/wh-metrics.py --by month        # 월별 처리량 (startedAt YYYY-MM)

# JSON 출력
python3 scripts/wh-metrics.py --by type --format json

# 다른 디렉터리 지정 (워크트리 간 비교 등)
python3 scripts/wh-metrics.py --dir other-worktree/work_history --by type
```

출력 스키마 (테이블 열 ↔ JSON 키):

| 테이블 열 | JSON 키 | 의미 |
|---|---|---|
| `bucket` | `bucket` | 그룹 키 (type/assignee/agent/jobType/month) |
| `count` | `count` | 전체 레코드 수 |
| `completed` | `completed` | `status == COMPLETED` 수 |
| `with_min` | `withMinutes` | COMPLETED 중 **유효 `totalMinutes`** 보유 수 (비정수·누락 제외) |
| `avg_min` | `avgMinutes` | 유효 `totalMinutes` 평균 (소수 1자리) |
| `sum_min` | `sumMinutes` | 유효 `totalMinutes` 합 |

- `totalMinutes`가 정수가 아니거나 누락이면 stderr 경고 + 평균/합계에서 **제외**(`count`에는 포함).
- frontmatter 종결자(`---`)가 없거나 평면 key:value 외 형식이면 stderr 경고 + 해당 파일 **skip**.

### 임시 ad-hoc 집계 (스크립트 없이)

워크트리에 Python이 없는 환경 fallback:

```bash
# type별 완료 건수 (따옴표 유무 모두 대응)
grep -rhE "^type:[[:space:]]*" work_history/ \
  | sed -E 's/^type:[[:space:]]*"?([^"]+)"?[[:space:]]*$/\1/' \
  | sort | uniq -c

# yq 설치돼 있으면
for f in work_history/*.md; do yq -f extract '.type' "$f"; done | sort | uniq -c
```

---

## 명명 규약

### work_history 파일명

```
work_history/{YYYY-MM-DD}-{issue-slug}.md
```

- Issue: `work_history/2026-10-14-issue-9-work-manager-porting.md`
- PR 배포 리뷰: `work_history/2026-10-14-pr-21-deploy-risk-review.md`

### 브랜치명 (CLAUDE.md "작업 시작 규칙" 재인용)

```
<type>/<issue-number>-<slug>
```

- `type`: `feature` / `fix` / `refactor` / `chore` / `docs` / `test`
- 이슈 번호가 없는 작업은 `<type>/<slug>` (예: `chore/update-readme`)

### 커밋 메시지 (관례)

Conventional Commits 변형:

```
<type>(<scope>): <summary> (#<issue>)

<body>

Verified: L<N> — <command> (passed, <n> tests)
Refs: #<issue>
```

L0/L1/L2 레벨은 [codex-review-triage §5](../codex-review-triage/SKILL.md) 참조.
