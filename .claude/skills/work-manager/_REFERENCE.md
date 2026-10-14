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
| `agent` | string | 라우팅된 전문 에이전트명 (예: `feat-engineer`) | 에이전트별 집계 |
| `assignee` | string | `git config user.name` 값 | 담당자별 소요 시간 |
| `branch` | string | 작업 브랜치명 (`<type>/<N>-<slug>`). DEPLOY-REVIEW는 빈 문자열 | 브랜치 추적 |
| `baseBranch` | string | 베이스 브랜치 (`main` 기본) | 환경별 집계 |
| `startedAt` | `"YYYY-MM-DD HH:mm"` | 작업 시작 시각 (KST) | 소요 시간 계산 |
| `completedAt` | `"YYYY-MM-DD HH:mm"` 또는 `""` | 완료 시각, 미완료 시 `""` | 소요 시간 계산 |
| `totalMinutes` | number | 총 소요 시간(분), 미완료 시 `0` | 소요 시간 집계 |
| `status` | enum | `IN_PROGRESS` · `COMPLETED` · `BLOCKED` | 현황 파악 |
| `issueUrl` | string | GitHub Issue 또는 PR URL (전체) | 추적·역참조 |
| `prUrl` | string | 작업 결과 PR URL (완료 시) | 결과물 추적 |

### 금지 필드 (TRD §11 보안)

다음은 **frontmatter·본문 어디에도 쓰지 않는다**:

- 비밀번호·토큰·API 키
- 음성 원문·transcript 본문
- 회의 참가자 이메일·전화번호 원본 (식별자만 — `meeting_id`, `user_id`)

> free-form 메모가 필요하면 식별자 중심으로 쓴다. 이메일/토큰이 섞여 들어가면 `SensitiveDataMasker.scrub()` 통과 후 저장.

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

### 메트릭 수집 명령 (예시)

work_history 파일들의 frontmatter를 YAML로 파싱해 집계. 실제 구현은 [#12 P2-4]에서 다룬다.

```bash
# 유형별 완료 건수 — 빠른 ad-hoc 확인용
grep -rE "^type:" work_history/ | awk -F'"' '{print $2}' | sort | uniq -c
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
