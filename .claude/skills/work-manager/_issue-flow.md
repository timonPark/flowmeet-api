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
Step 3.3 — brainstormer 호출 (복잡 FEAT·REFACTOR·PERF)
            옵션 2~3개 + trade-off → AskUserQuestion → 선택
  │
  ▼
Step 3.5 — plan-writer 호출
            Task 쪼개서 docs/plans/{YYYY-MM-DD}-{N}-{slug}.md 저장
  │
  ▼
Step 3.6 — 사용자 최종 승인 (AskUserQuestion) 대기 🚧
            승인 전까지 전문 에이전트 호출 금지
  │
  ▼
→ 전문 에이전트에게 핸드오프 — type 전체 라우팅은 [라우팅 매트릭스](./_REFERENCE.md#type--전문-에이전트-라우팅-agent-필드) 참조. 주요: FEAT·TEST→feat-developer / FIX→fix-developer / REFACTOR·PERF→refactor-developer / DOC·CHORE·ANALYSIS(문서형)→doc-updater / DEPLOY-REVIEW→pr-review-check
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

**`.claude/agents/issue-kickoff.md` Read 후 Claude Code가 직접 실행**한다. Agent 툴 호출은 금지(1-depth 유지). 상세 로직(dirty worktree 보호 / 결정적 slug / baseBranch 반영 분기)은 **issue-kickoff.md에만** 두고, 이 flow 문서는 요약만 유지한다.

수행 요약:

1. `gh issue view {N}`으로 이슈 조회 (404·CLOSED 상태 검증).
2. 결정적 slug 추출 (대괄호 접두어 제거 · 한글 요약 교체 · 소문자 하이픈 30자).
   - 예: `[P2-2] GitHub Issue flow (상태 라벨 전환, 브랜치 생성 통합)` → **`github-issue-flow`**.
3. dirty worktree 체크. 변경사항이 있으면 중단·사용자 확인.
4. `git fetch origin "{baseBranch}"` 후 `origin/{baseBranch}` 에서 바로 분기 (로컬 base 브랜치는 전환하지 않는다).
   ```bash
   git checkout -b "{branchType}/{N}-{slug}" "origin/{baseBranch}"
   ```
5. 결과 보고: 투명성 포맷 + 브랜치명·HEAD SHA·base.

> 로컬 `main`·`develop` 등을 임의로 pull하지 않는다 — 사용자가 선택한 base가 정확히 반영되도록 origin ref에서 바로 분기.

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
agent: "{type에 매핑된 에이전트 이름 — _REFERENCE.md 참조}"   # 수동 분기 시에는 "manual"
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

## Step 3 — 상태 라벨 전환 `[gh label 사용 + 결과 검증]`

work_history 작성 후 즉시 수행. **전환 전·후 모두 검증**한다.

### 3-1. 전제 — 라벨 세트 존재 확인

```bash
REQUIRED=("status:todo" "status:in-progress" "status:in-review" "status:done")
EXISTING=$(gh label list --repo timonPark/flowmeet-api --search "status:" --json name --jq '.[].name')
for L in "${REQUIRED[@]}"; do
  echo "$EXISTING" | grep -qx "$L" || {
    echo "라벨 '${L}' 가 리포에 없습니다. #10 PR이 머지됐는지, 또는 'gh label create' 로 생성이 필요한지 확인해 주세요." >&2
    exit 1
  }
done
```

### 3-2. 전환 실행

```bash
# 이전 status 라벨 제거 (존재할 때만, 실패는 무시)
for L in status:todo status:in-review status:done; do
  gh issue edit {N} --repo timonPark/flowmeet-api --remove-label "$L" 2>/dev/null || true
done

# in-progress 라벨 추가
gh issue edit {N} --repo timonPark/flowmeet-api --add-label "status:in-progress"
```

### 3-3. 결과 검증 `[필수 — 순서 비의존 집합 비교]`

`gh issue edit`는 silent fail 가능. 전환 후 반드시 재조회. GitHub API가 라벨 배열 순서를 보장하지 않으므로 **jq `sort` 로 정규화 후 비교**한다.

```bash
LABELS=$(gh issue view {N} --repo timonPark/flowmeet-api --json labels \
  --jq '[.labels[].name] | map(select(startswith("status:"))) | sort')
# 정확히 ["status:in-progress"] 가 아니면 실패 (다른 status 라벨 공존도 검출)
echo "$LABELS" | jq -e '. == ["status:in-progress"]' > /dev/null || {
  echo "라벨 전환 검증 실패: 현재 status 라벨=$LABELS" >&2
  exit 1
}
```

- 상태 라벨이 **없거나**, **in-progress 외 라벨이 섞여 있거나**, **두 개가 공존**하면 실패로 간주.
- 실패 시 사용자에게 보고하고 수동 수정 요청.
- 성공하면 work_history의 "상태 변화 이력"에 1줄 추가:
  ```
  | {YYYY-MM-DD HH:mm} | 🏷️ 라벨 전환 | status:todo → status:in-progress |
  ```

### 투명성 포맷 예시

```
▶ [Step 3] GitHub 라벨 전환 (todo → in-progress)
✅ [Step 3] 라벨 전환 완료 — issue #42
```

---

## Step 3.3 — brainstormer 호출 `[복잡 작업만]`

**호출 조건**은 [`_REFERENCE.md`의 type ↔ 전문 에이전트 라우팅](./_REFERENCE.md#type--전문-에이전트-라우팅-agent-필드) 표를 **단일 소스**로 사용한다. 요약: 복잡 FEAT·REFACTOR·PERF·넓은 FIX·코드형 ANALYSIS는 필수, 작은 변경·명확한 FIX·DOC·간단 CHORE·문서형 ANALYSIS는 skip. 애매하면 호출.

> opt-out: `--skip-brainstorm` 는 brainstormer만 skip (plan-writer는 수행), `--skip-plan` 은 **둘 다 skip** (사용자 책임).

### 수행 요약

**`.claude/agents/brainstormer.md` Read 후 Claude Code가 직접 실행** (Agent 툴 금지, 1-depth 유지). 상세 로직은 brainstormer.md에만.

1. 요구사항·제약 bullet 정리 (불명확 지점은 사용자에게 질문).
2. 접근 방식 **2~3개** 생성 — 동일 틀(개요·영향 파일·레이어·trade-off·리스크)로.
3. `AskUserQuestion` 으로 선택 수집. "Other" 지정 시 D 안으로 수용 후 trade-off 재정리.
4. 결과를 호출자(`_issue-flow`)에 반환 + work_history "작업 상세"에 1줄.

### Skip 조건에서

사용자에게 "자명한 수정으로 보여 brainstormer skip, plan-writer로 바로 넘어갑니다"라고 **투명성 포맷으로 보고**하고 Step 3.5로.

### 투명성 포맷 예시

```
▶ [Step 3.3] brainstormer — 접근 방식 탐색
🚧 [Step 3.3] 옵션 3개 제시 — 선택을 기다립니다
✅ [Step 3.3] 선택: 옵션 B — "QueryDSL + @BatchSize 조합"
```

---

## Step 3.5 — plan-writer 호출 `[대부분 작업]`

**호출 조건**은 [`_REFERENCE.md`의 type ↔ 전문 에이전트 라우팅](./_REFERENCE.md#type--전문-에이전트-라우팅-agent-필드) 표를 **단일 소스**로 사용한다 — Step 3.3 brainstormer 조건과 함께 거기서만 관리. 요약: 거의 모든 작업에 필수, DOC/간단 CHORE/작은 TEST/문서형 ANALYSIS는 skip 또는 간소 템플릿.

> `--skip-plan` 가 있으면 brainstormer + plan-writer **둘 다 skip**. `--skip-brainstorm` 는 brainstormer만 skip.

### 수행 요약

**`.claude/agents/plan-writer.md` Read 후 Claude Code가 직접 실행** (Agent 툴 금지). 상세 로직은 plan-writer.md에만.

1. 저장 경로 결정: `docs/plans/{YYYY-MM-DD}-{N}-{slug}.md`.
2. 영향 파일 매핑(신규/수정) + 레이어 식별.
3. Task를 **2~5분 단위**로 쪼갠다 (한 Task = 한 액션, 커밋 Task 포함하되 **자동 실행 안 함**).
4. 검증 레벨(L0/L1/L2) + 롤백 포인트 명시.
5. 플랜 파일 저장 — frontmatter(`status: PROPOSED` 초기값) + 본문 템플릿.

### 투명성 포맷 예시

```
▶ [Step 3.5] plan-writer — Task 쪼개서 저장
✅ [Step 3.5] docs/plans/2026-10-14-42-sample-slug.md 저장 (Task 6개, L1)
```

---

## Step 3.6 — plan-writer 결과 수신 `[승인은 plan-writer가 수집]`

**사용자 승인 AskUserQuestion은 plan-writer가 §6에서 직접 수집한다.** `_issue-flow`는 결과만 받는다 — 승인 질문을 두 번 띄우지 않는다.

### 받는 값

- plan-writer가 반환한 승인 결과: `APPROVED` / `REVISION_REQUESTED` / `CANCELLED`.
- 승인된 플랜 파일 경로.

### 분기

- `APPROVED` → 전문 에이전트 호출 단계로 진행. work_history 상태 변화 이력에 "✅ 플랜 승인 — {경로}" 1줄 추가.
- `REVISION_REQUESTED` → plan-writer가 이미 Edit 반복 후 다시 승인 수집했을 것. 이 상태로 돌아오면 비정상 — 호출자가 사용자에게 중단 사유 보고.
- `CANCELLED` → work_history에 "🚧 사용자 취소" 기록, 전문 에이전트 호출 중단, 라벨은 `status:in-progress` 유지(재개 가능).

### 투명성 포맷 예시

```
✅ [Step 3.6] plan-writer 결과 수신 — APPROVED (docs/plans/2026-10-14-42-sample-slug.md)
→ 전문 에이전트 호출로 진행
```

---

## 작업 완료 시 — PR 생성 + status:in-review 전환

전문 에이전트가 작업을 완료하면(현재는 사용자 수동):

1. **커밋·푸시**: Conventional Commits 형식 ([_REFERENCE.md](./_REFERENCE.md#커밋-메시지-관례)).
2. **PR 생성**: `gh pr create ... --body "Closes #{N} ..."` — Closes 키워드로 머지 시 자동 close.
3. **status 전환**: `in-progress` → `in-review`. **Step 3-3 과 동일한 검증 패턴** 적용(배열 순서 비의존 집합 비교).
   ```bash
   gh issue edit {N} --repo timonPark/flowmeet-api --remove-label "status:in-progress"
   gh issue edit {N} --repo timonPark/flowmeet-api --add-label "status:in-review"
   # 검증 — sort로 순서 정규화 후 비교
   gh issue view {N} --repo timonPark/flowmeet-api --json labels \
     --jq '[.labels[].name] | map(select(startswith("status:"))) | sort' \
     | jq -e '. == ["status:in-review"]' > /dev/null
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
🎯 [라우팅] type=CHORE → doc-updater 호출 예정 (agents/doc-updater.md Read 후 직접 실행, Agent 툴 금지)
```

---

## 금지 사항

- ❌ `Agent` 툴로 issue-kickoff 호출 (1-depth 유지 — Claude Code가 직접 Read·실행).
- ❌ 라벨 전환 생략 (메트릭 집계 왜곡).
- ❌ work_history 생성 전에 Step 3로 넘어가기 (순서 역전 금지).
- ❌ 이슈 본문 전체를 work_history에 복사 (PII·토큰·회의 내용 유출 위험).
- ❌ 라벨 두 개가 공존하는 상태 (반드시 remove → add 세트).
