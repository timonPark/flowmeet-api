---
name: work-manager
description: GitHub Issue URL/번호 또는 GitHub PR URL을 받아 작업을 분석하고 적절한 전문 에이전트로 라우팅하는 오케스트레이터. work_history/ 파일로 유형·소요 시간·담당자별 메트릭을 축적한다. 사용자가 flowmeet-api 작업을 Issue URL, #N, PR URL 로 시작할 때 로드.
metadata:
  allowed-tools:
    - AskUserQuestion
    - Agent
    - Skill
    - Read
    - Write
    - Edit
    - Bash(git config:*)
    - Bash(git fetch:*)
    - Bash(git checkout:*)
    - Bash(git branch:*)
    - Bash(git status:*)
    - Bash(gh issue view:*)
    - Bash(gh issue edit:*)
    - Bash(gh pr view:*)
    - Bash(gh pr create:*)
    - Bash(gh pr merge:*)
    - Bash(gh api:*)
    - Bash(find:*)
    - Bash(ls:*)
    - Bash(date:*)
---

# work-manager — flowmeet-api

GitHub Issue 또는 PR URL을 받아 **유형 판정 → 하위 flow 라우팅 → work_history 기록 → 전문 에이전트 호출**을 조율한다. 모든 단계는 **투명성 원칙**을 따른다. Jira는 쓰지 않는다 (GitHub Issues/Projects로 대체).

## 인수

```
https://github.com/timonPark/flowmeet-api/issues/123
#123
https://github.com/timonPark/flowmeet-api/pull/456
```

## 투명성 원칙 `[전 단계 필수]`

work-manager 및 이후 호출되는 **모든 전문 에이전트**는 아래 규칙을 반드시 따른다.

### 단계 시작 시 출력

```
▶ [단계명] {하려는 작업 한 줄 설명}
```

예시: `▶ [이슈 조회] GitHub #42 본문·라벨 수집 중`

### 단계 완료 시 출력

```
✅ [단계명] {완료 결과 한 줄 요약}
```

예시: `✅ [이슈 조회] 유형=FEAT, phase-1 라벨, 레이어링 규칙 추가 요청`

### 병목 발생 시 즉시 보고

```
🚧 [병목] {상황 설명} — 어떻게 진행할까요?
```

> 침묵 금지. "뭔가 돌고 있다"보다 "지금 X 단계에서 Y 때문에 멈춤"이 항상 우선.

### 출력 민감정보 금지 `[TRD §11]`

투명성 출력(`▶`/`✅`/`🚧`)과 work_history 본문에 **PII·토큰·음성 원문·회의 세부 내용**이 섞여서는 안 된다. 특히 `gh issue view`·`gh pr view` 결과를 그대로 로그에 넣지 말 것. 식별자(이슈 번호·사용자 id·회의 id) 중심으로 표현하고, free-form 발췌가 불가피하면 [SensitiveDataMasker.scrub()](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt) 통과 후 사용 ([security-logging §4](../security-logging/SKILL.md)).

## 사전 준비

- `git config user.name` 실행 값을 `{assignee}`로 사용한다.
- `GH_REPO=timonPark/flowmeet-api` (고정). PR·이슈 명령에 `--repo` 플래그 전달 또는 `gh` 기본 remote가 origin.

## 입력 유형 감지 `[최우선 — 이후 파일 로드 결정]`

### 입력 정규화 (적용 순서)

1. 양 끝 공백 제거.
2. 끝의 `/`, `#`, `?...`, `#...` 제거 (trailing slash·query·fragment).
3. 호스트명·경로는 **대소문자 무시**로 비교. 번호는 정수로 파싱(`parseInt`), 실패하면 "판정 불가".

### 패턴 매칭

| 입력 패턴 (정규식) | 판단 | 다음 액션 |
|---|---|---|
| `(?i)github\.com/timonPark/flowmeet-api/pull/(\d+)` | GitHub PR URL | `.claude/skills/work-manager/_pr-flow.md` 를 Read → 절차 실행 |
| `(?i)github\.com/timonPark/flowmeet-api/issues/(\d+)` 또는 `^#(\d+)$` | GitHub Issue URL/번호 | `.claude/skills/work-manager/_issue-flow.md` 를 Read → 절차 실행 |
| 그 외 (저장소 불일치·숫자 아님·빈 입력) | 판정 불가 | 사용자에게 어떤 URL/번호인지 되묻는다 (추측 금지) |

### 추가 검증 (Issue·PR 공통)

- 번호가 정수가 아니면 즉시 되묻기.
- `gh issue view` 또는 `gh pr view` 호출 결과 404면 "번호를 다시 확인해 주세요".
- 호스트가 다른 저장소면 "이 리포(`timonPark/flowmeet-api`)의 번호만 처리합니다"로 거절.

> frontmatter 필드 정의 및 추출 가능한 메트릭은 `.claude/skills/work-manager/_REFERENCE.md` 참조.

## 라우팅 목적지 (전문 에이전트)

flow 파일이 판단한 **작업 유형**에 따라 `.claude/agents/<name>.md` 에 정의된 전문 에이전트 중 하나를 Claude Code가 **Read 후 직접 실행**(Agent 툴 호출 금지, 1-depth 유지)한다.

| 유형 | 에이전트 |
|---|---|
| FEAT (신규 기능), TEST | [`feat-developer`](../../agents/feat-developer.md) |
| FIX (버그 수정) | [`fix-developer`](../../agents/fix-developer.md) |
| REFACTOR, PERF | [`refactor-developer`](../../agents/refactor-developer.md) |
| DOC, CHORE(하네스), ANALYSIS | [`doc-updater`](../../agents/doc-updater.md) |
| DEPLOY-REVIEW (PR URL 입력) | [`pr-review-check`](../../agents/pr-review-check.md) |

> issue 수신 직후의 **브랜치 분기 + 이슈 조회**는 공통으로 [`issue-kickoff`](../../agents/issue-kickoff.md)가 수행하고, 그 뒤 위 에이전트 중 하나로 핸드오프.
>
> 매핑 상세 표는 [`_REFERENCE.md`의 type ↔ 전문 에이전트 라우팅](./_REFERENCE.md#type--전문-에이전트-라우팅-agent-필드) 참조.

## 작업 흐름 요약

```
입력 (URL / #N / PR URL)
  │
  ▼ 유형 감지
  ├─ Issue → _issue-flow.md (브랜치 생성·work_history 작성·전문 에이전트 호출·PR 생성)
  └─ PR    → _pr-flow.md    (PR 분석·리뷰·사용자 승인 후 머지)
  │
  ▼ 모든 단계 투명성 원칙 적용
  ▼ work_history/ 에 기록
  ▼ 검증
```

## PR 머지 규칙 (상위 CLAUDE.md 재강조)

`_pr-flow.md`가 PR 머지 단계에 도달해도 **사용자의 명시적 승인 없이 `gh pr merge` 를 실행하지 않는다** (CLAUDE.md "PR 리뷰·머지 규칙" 참조). codex 리뷰 통과·`./gradlew build` 통과·CI 그린만으로는 머지 조건 성립 아님.

## 검증

라우팅을 마친 뒤 아래를 확인한다. 실행 모드별로 요구 수준이 다르다.

| # | 조건 | 적용 범위 |
|---|---|---|
| 1 | 선택한 전문 에이전트가 `.claude/agents/` 에 존재하거나, 미등록 상태를 `agent: "manual"` 로 명시 기록 | 모든 라우팅 |
| 2 | `work_history/` 기록이 완료되었거나(상세 flow) 최소 `IN_PROGRESS` 플레이스홀더가 생성되었음(stub 수동 분기) | 상세 flow는 필수 / stub는 플레이스홀더 수준 |
| 3 | 이슈/PR 유형 판정 근거(라벨·본문 키워드·제목)를 **한 줄로 남겼는가** | 모든 라우팅 |
| 4 | PR 머지 지점이면 사용자 명시 승인이 로그에 남았는가 | PR 머지 단계 |
| 5 | 투명성 출력·work_history 본문에 민감정보가 섞이지 않았는가(필요 시 `scrub()` 통과) | 모든 라우팅 |

하나라도 실패하면 고치고 다시 검사한다. 전부 통과하면 라우팅 결과와 근거를 사용자에게 보고한다.

> 상세 flow(#10/#11) 포팅 전 **stub 수동 분기 모드**는 2번을 "플레이스홀더 생성"으로 완화한다 — 완전한 메트릭 집계는 자동화 완료 후.

## 금지 사항

- ❌ Jira·외부 티켓 시스템 참조. 이 리포는 GitHub Issues/Projects만 사용.
- ❌ 유형을 모호한 상태로 라우팅 (추측 금지 — 사용자에게 되묻는다).
- ❌ 투명성 포맷 생략 — "⋯ 작업 중" 같은 모호한 메시지로 침묵하지 않는다.
- ❌ PR 머지를 사용자 명시 승인 없이 실행.
- ❌ work_history 미기록 상태로 라우팅 종료.

## 관련

- `_REFERENCE.md` — frontmatter 필드 + 추출 메트릭
- `_issue-flow.md` — GitHub Issue 입력 처리 (상세 포팅: #10)
- `_pr-flow.md` — GitHub PR 입력 처리 (상세 포팅: #11)
- 상위 규칙: [kotlin-style](../kotlin-style/SKILL.md), [spring-layering](../spring-layering/SKILL.md), [error-handling](../error-handling/SKILL.md), [jpa-query-guard](../jpa-query-guard/SKILL.md), [tdd-service](../tdd-service/SKILL.md), [security-logging](../security-logging/SKILL.md)
- 외부 코드 리뷰 흐름: [codex-review-triage](../codex-review-triage/SKILL.md)
