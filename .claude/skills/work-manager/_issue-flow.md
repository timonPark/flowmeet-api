# Issue 경로 — GitHub Issue 처리 flow

> GitHub Issue URL 또는 `#N` 입력 시 실행. PR URL이면 `_pr-flow.md` 로 이동.

cdp-server `_jira-flow.md` 대응. Jira transition 대신 **GitHub status 라벨** 전환으로 상태 머신을 표현한다.

---

## 상태 머신 (라벨 기반)

```
status:todo  →  status:in-progress  →  status:in-review  →  status:done
     ▲               (브랜치 분기)        (PR 생성)          (PR 머지)
     │                                                        │
     └─────────────── (BLOCKED/재개 — 라벨은 유지, status 변경 없음) ────┘
```

- 라벨 이름 정확히: `status:todo` / `status:in-progress` / `status:in-review` / `status:done`.
- 라벨 전환은 **반드시 이전 status 제거 + 새 status 추가** 세트로. 두 개가 공존하면 안 됨.
- `done`은 PR 머지 후 `_pr-flow.md`에서 전환 — 이슈 경로는 **`in-progress` → `in-review`** 까지만 책임.

---

## 전체 흐름 요약

```
[입력] #N or Issue URL
  │
  ▼
Step 0 — AskUserQuestion: baseBranch + branchType 수집
  │
  ▼
Step 1 — issue-kickoff 에이전트 호출 (브랜치 생성 + 이슈 조회)
  │
  ▼
Step 2 — work_history 파일 생성 (frontmatter 완전 작성)
  │
  ▼
Step 3 — status:todo 제거 → status:in-progress 추가
  │
  ▼
→ 전문 에이전트에게 핸드오프 (현재 미등록 — #13 완료 후 자동, 현재는 사용자 수동 작업)
  │
  ▼ (작업 완료 시)
PR 생성 → status:in-progress 제거 → status:in-review 추가
  │
  ▼
→ _pr-flow.md로 위임 (리뷰·머지는 사용자 명시 승인 후)
```

---

## Step 0 — 사전 수집 `[필수]`

`AskUserQuestion`으로 두 값을 **한 번에** 수집한다 (툴 호출 1회).

| 항목 | 옵션 |
|---|---|
| baseBranch | `main` (기본) / `develop` / `release` |
| branchType | `feature` / `fix` / `chore` / `refactor` / `docs` / `test` |

→ 저장: `{baseBranch}`, `{branchType}`. 이후 Step 1·2에서 재사용.

### 투명성 포맷 예시

```
▶ [Step 0] 베이스 브랜치·브랜치 성격 수집
✅ [Step 0] baseBranch=main, branchType=chore
```

---

## Step 1 — issue-kickoff 호출 `[Agent 툴 금지]`

**`.claude/agents/issue-kickoff.md` Read 후 Claude Code가 직접 실행**한다. Agent 툴 호출은 금지(1-depth 유지). 수행:

1. `gh issue view {N} --repo timonPark/flowmeet-api --json number,title,body,labels,state`로 이슈 조회.
   - 404면 번호 재확인 요청.
   - `state == CLOSED`면 "이미 종료된 이슈입니다. 작업을 계속할까요?" 확인.
2. 제목에서 **slug** 추출: 소문자·하이픈·30자 이내. 특수문자·한글은 전치 또는 제거.
   - 예: `[P2-2] GitHub Issue flow (상태 라벨 전환, 브랜치 생성 통합)` → `p2-2-github-issue-flow`
3. 브랜치명 조립: `{branchType}/{N}-{slug}`.
4. `git fetch origin && git checkout main && git pull` 로 베이스 최신화.
5. 브랜치 생성·전환:
   ```bash
   git checkout -b {branchType}/{N}-{slug} origin/{baseBranch}
   ```
   - 이미 존재하면 `git checkout {branchType}/{N}-{slug}` 로 전환.
6. 결과 보고: 투명성 포맷 + 브랜치명·HEAD SHA.

### 투명성 포맷 예시

```
▶ [Step 1] issue-kickoff — 이슈 #42 조회 및 브랜치 분기
✅ [Step 1] 브랜치: chore/42-sample-slug (HEAD: abc1234, base: main)
```

---

## Step 2 — work_history 파일 생성 `[필수]`

파일명: `work_history/{YYYY-MM-DD}-issue-{N}-{slug}.md`

- 날짜는 `date '+%Y-%m-%d'` 결과를 그대로 사용 — **임의 추정 금지**.
- 시작 시각은 `date '+%Y-%m-%d %H:%M'` 결과.

### frontmatter (완전 형식)

```yaml
---
issueKey: "#{N}"
issueTitle: "{이슈 제목 — 민감 정보 금지}"
type: "{FEAT|FIX|REFACTOR|PERF|DOC|CHORE|TEST}"
jobType: backend
agent: "manual"   # 전문 에이전트 등록(#13) 후 agent 이름으로 교체
assignee: "{git config user.name}"
branch: "{branchType}/{N}-{slug}"
baseBranch: "{baseBranch}"
startedAt: "{YYYY-MM-DD HH:mm}"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/{N}"
prUrl: ""
---
```

### 본문 템플릿

```markdown
# #{N} — {이슈 제목}

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#{N}` |
| 유형 | — |
| 담당자 | `{assignee}` |
| 브랜치 | `{branch}` |
| 베이스 | `{baseBranch}` |
| 시작 | {YYYY-MM-DD HH:mm} KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: {라벨 목록}
- 본문 키워드: {FEAT/FIX 등 추론 근거 1~2줄 — PII 금지}
- 판정: `{type}`

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | issue-kickoff | {HH:mm} | {HH:mm} | {Xm} | ✅ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| {YYYY-MM-DD HH:mm} | 🚀 작업 시작 | work-manager 진입 |
| {YYYY-MM-DD HH:mm} | ✅ 브랜치 생성 | `{branch}` |
| {YYYY-MM-DD HH:mm} | 🏷️ 라벨 전환 | `status:todo` → `status:in-progress` |

## 작업 상세

(진행 중 메모 — 식별자 중심, 민감 정보 금지)

## 메모 / 특이사항
```

### 보안 가드

- `gh issue view` body에 PII·토큰·음성 원문이 섞여 있을 수 있다. **직접 복사 금지**. 필요한 핵심만 1~2줄로 요약하고, free-form 발췌는 `SensitiveDataMasker.scrub()`([common/logging/SensitiveDataMasker.kt](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt)) 통과 후.
- 금지 필드 전체는 [`_REFERENCE.md`의 "금지 필드"](./_REFERENCE.md#금지-필드-trd-§11-보안) 참조.

---

## Step 3 — 상태 라벨 전환 `[gh label 사용]`

work_history 작성 후 즉시 수행.

```bash
# 1. 이전 status 라벨 제거 (존재할 때만)
for L in status:todo status:in-review status:done; do
  gh issue edit {N} --repo timonPark/flowmeet-api --remove-label "$L" 2>/dev/null || true
done

# 2. in-progress 라벨 추가
gh issue edit {N} --repo timonPark/flowmeet-api --add-label "status:in-progress"
```

- 라벨이 없으면 (`gh issue edit`가 silently fail), 사용자에게 "status 라벨 세트가 리포에 없습니다. #10 PR이 머지됐는지 확인해 주세요."라고 보고.
- 라벨 전환 성공 후 work_history의 "상태 변화 이력"에 1줄 추가:
  ```
  | {YYYY-MM-DD HH:mm} | 🏷️ 라벨 전환 | status:todo → status:in-progress |
  ```

### 투명성 포맷 예시

```
▶ [Step 3] GitHub 라벨 전환 (todo → in-progress)
✅ [Step 3] 라벨 전환 완료 — issue #42
```

---

## 작업 완료 시 — PR 생성 + status:in-review 전환

전문 에이전트가 작업을 완료하면(현재는 사용자 수동):

1. **커밋·푸시**: Conventional Commits 형식 ([_REFERENCE.md](./_REFERENCE.md#커밋-메시지-관례)).
2. **PR 생성**: `gh pr create ... --body "Closes #{N} ..."` — Closes 키워드로 머지 시 자동 close.
3. **status 전환**: `in-progress` → `in-review`.
   ```bash
   gh issue edit {N} --repo timonPark/flowmeet-api --remove-label "status:in-progress"
   gh issue edit {N} --repo timonPark/flowmeet-api --add-label "status:in-review"
   ```
4. work_history 상태 변화 이력에 "🏷️ 라벨 전환 — in-progress → in-review" 추가.
5. 이후 리뷰·머지 단계는 **`_pr-flow.md`에 위임**. 머지는 사용자 명시 승인 필수.

---

## 입력 검증 (SKILL.md 요약 재인용)

| 조건 | 처리 |
|---|---|
| 번호가 정수가 아님 | 사용자에게 되묻기 (추측 금지) |
| `gh issue view` 404 | 번호 재확인 |
| 이슈가 CLOSED | 재개 의사 확인 |
| 호스트가 다른 저장소 | "이 리포의 번호만 처리합니다" |

---

## 전체 투명성 포맷 예시

```
▶ [Step 0] 베이스 브랜치·브랜치 성격 수집
✅ [Step 0] baseBranch=main, branchType=chore
▶ [Step 1] issue-kickoff — 이슈 #42 조회 및 브랜치 분기
✅ [Step 1] 브랜치: chore/42-sample (base: main, HEAD: abc1234)
▶ [Step 2] work_history 파일 생성
✅ [Step 2] work_history/2026-10-14-issue-42-sample.md (type=CHORE 판정)
▶ [Step 3] GitHub 라벨 전환 (todo → in-progress)
✅ [Step 3] issue #42 status:in-progress
🎯 [라우팅] 전문 에이전트 미등록(#13 범위) — 사용자 수동 작업 또는 "진행해주세요"로 자동 분기 예정
```

---

## 금지 사항

- ❌ `Agent` 툴로 issue-kickoff 호출 (1-depth 유지 — Claude Code가 직접 Read·실행).
- ❌ 라벨 전환 생략 (메트릭 집계 왜곡).
- ❌ work_history 생성 전에 Step 3로 넘어가기 (순서 역전 금지).
- ❌ 이슈 본문 전체를 work_history에 복사 (PII·토큰·회의 내용 유출 위험).
- ❌ 라벨 두 개가 공존하는 상태 (반드시 remove → add 세트).
