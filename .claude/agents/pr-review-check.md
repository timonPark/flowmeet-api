---
name: pr-review-check
description: GitHub PR #N 메타(title/labels/head/base/state/mergeState/reviews)를 수집하고 리뷰 점검 체크리스트(미답변 코멘트·사람 승인·codex 산출물·CI 상태)를 생성하는 수행 에이전트. work-manager _pr-flow Step 1~4에서 호출된다. Agent 툴 호출 금지 — Claude Code가 Read 후 직접 실행.
metadata:
  allowed-tools:
    - Read
    - Bash(gh pr view:*)
    - Bash(gh api:*)
    - Bash(gh issue view:*)
    - Bash(git branch:*)
    - Bash(git show-ref:*)
    - Bash(ls:*)
    - Bash(date:*)
---

# pr-review-check — PR 메타 수집 + 리뷰 체크리스트

work-manager `_pr-flow.md` Step 1~4 에서 호출. **Agent 툴로 호출하지 않는다** — Claude Code가 Read 후 직접 실행한다. (1-depth 유지)

## 입력

| 변수 | 설명 |
|---|---|
| `{N}` | GitHub PR 번호 (정수) |

## 수행

### 1. 메타 수집 `[단일 호출]`

```bash
gh pr view {N} --repo timonPark/flowmeet-api --json \
  number,title,body,labels,headRefName,baseRefName,state,mergeStateStatus,mergeable,isDraft,reviews,url,author,statusCheckRollup
```

- 404 → "PR #{N}이 존재하지 않습니다. 번호를 다시 확인해 주세요."
- `state == MERGED` / `CLOSED` → 사용자에게 어떤 작업을 할지 확인 (회고·리뷰 조회만 허용).
- `isDraft == true` → "Draft PR입니다. 머지 전 Ready for review 전환 필요합니다." 로 안내.

### 2. 미답변 인라인 코멘트 조회

```bash
gh api repos/timonPark/flowmeet-api/pulls/{N}/comments \
  --jq '[.[] | select(.in_reply_to_id == null) | {id, path, line, user: .user.login, body}]'
```

- `in_reply_to_id`가 없는 코멘트가 **최상위**(답글 받지 못한 상태).
- 결과를 Step 4-1 보고에 사용.

### 3. 사람 리뷰 승인 수

```bash
APPROVED=$(gh pr view {N} --repo timonPark/flowmeet-api --json reviews \
  --jq '[.reviews[] | select(.state == "APPROVED")] | length')
echo "approved_count=${APPROVED}"
```

### 4. codex 2차 리뷰 산출물 확인

```bash
BRANCH="{headRefName}"
SLUG=$(echo "$BRANCH" | tr '/' '-')
find codereview -type f -name "${SLUG}.md" 2>/dev/null
```

- 없으면 `codex-review-triage` 수행을 권고하는 메시지 반환.

### 5. mergeable·CI 상태

Step 1에서 이미 받은 `mergeStateStatus`·`mergeable`·`statusCheckRollup` 재사용. 추가 호출 불필요.

## 반환 (호출자에게 전달하는 요약)

```
PR #{N}
  title: {title — PII·토큰 금지. 민감하면 요약으로 교체}
  state: {OPEN|MERGED|CLOSED}
  head → base: {headRefName} → {baseRefName}
  mergeState: {CLEAN|DIRTY|BLOCKED|UNKNOWN}
  mergeable: {MERGEABLE|CONFLICTING|UNKNOWN}
  isDraft: {true|false}
  labels: [{labels[].name}]

리뷰 점검:
  미답변 인라인 코멘트: {count} ({파일: 사용자 간단 목록})
  사람 승인: {APPROVED count}
  codex 산출물: {경로 또는 "없음 — triage 권고"}
  CI 상태: {statusCheckRollup 요약 — SUCCESS|FAILURE|PENDING|NONE}
```

## 호출자 (work-manager) 책임

pr-review-check 반환 후 호출자가 수행:

1. **유형 판정** (`_pr-flow.md` Step 2 — title 접두사/변경 범위 기준).
2. **work_history 플레이스홀더 작성** (`_pr-flow.md` Step 3).
3. **사용자에게 리뷰 점검 결과 보고** (`_pr-flow.md` Step 4 — 투명성 포맷).
4. **머지 승인 대기** (`_pr-flow.md` Step 5 — 사용자 명시 "머지해줘" 전까지 대기).

## 금지

- ❌ Agent 툴로 호출 (반드시 Claude Code가 직접 Read·실행).
- ❌ PR body(`body`)를 로그·보고 메시지에 그대로 노출 — 요약 또는 생략.
- ❌ `gh pr merge` 를 이 에이전트에서 실행 — 머지는 `_pr-flow.md` Step 5 책임이고 **반드시 사용자 명시 승인 후**.
- ❌ CLOSED/MERGED PR에서 사용자 확인 없이 작업 재개.

## 관련

- [work-manager/_pr-flow.md](../skills/work-manager/_pr-flow.md) — 호출자
- [codex-review-triage](../skills/codex-review-triage/SKILL.md) — codex 산출물 생성 흐름
- [security-logging](../skills/security-logging/SKILL.md) — Masker 사용 기준
- CLAUDE.md "PR 리뷰·머지 규칙" — 사용자 명시 승인 원칙
