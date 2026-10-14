---
name: work-manager
description: GitHub Issue URL/번호 또는 GitHub PR URL을 받아 작업을 분석하고 적절한 전문 에이전트로 라우팅하는 오케스트레이터. work_history/ 파일로 유형·소요 시간·담당자별 메트릭을 축적한다. 사용자가 flowmeet-api 작업을 Issue URL, #N, PR URL 로 시작할 때 로드.
allowed-tools:
  [
    'AskUserQuestion',
    'Agent',
    'Skill',
    'Read',
    'Write',
    'Edit',
    'Bash(git config:*)',
    'Bash(git fetch:*)',
    'Bash(git checkout:*)',
    'Bash(git branch:*)',
    'Bash(git status:*)',
    'Bash(gh issue view:*)',
    'Bash(gh issue edit:*)',
    'Bash(gh pr view:*)',
    'Bash(gh pr create:*)',
    'Bash(gh pr merge:*)',
    'Bash(gh api:*)',
    'Bash(find:*)',
    'Bash(ls:*)',
    'Bash(date:*)',
  ]
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

## 사전 준비

- `git config user.name` 실행 값을 `{assignee}`로 사용한다.
- `GH_REPO=timonPark/flowmeet-api` (고정). PR·이슈 명령에 `--repo` 플래그 전달 또는 `gh` 기본 remote가 origin.

## 입력 유형 감지 `[최우선 — 이후 파일 로드 결정]`

| 입력 패턴 | 판단 | 다음 액션 |
|---|---|---|
| `github.com/timonPark/flowmeet-api/pull/{번호}` | GitHub PR URL | `.claude/skills/work-manager/_pr-flow.md` 를 Read → 절차 실행 |
| `github.com/timonPark/flowmeet-api/issues/{번호}` 또는 `#{번호}` | GitHub Issue URL/번호 | `.claude/skills/work-manager/_issue-flow.md` 를 Read → 절차 실행 |
| 그 외 | 판정 불가 | 사용자에게 어떤 URL/번호인지 되묻는다 (추측 금지) |

> frontmatter 필드 정의 및 추출 가능한 메트릭은 `.claude/skills/work-manager/_REFERENCE.md` 참조.

## 라우팅 목적지 (전문 에이전트)

flow 파일이 판단한 **작업 유형**에 따라 `.claude/agents/<name>.md` 에 정의된 전문 에이전트 중 하나를 Agent 툴로 호출한다. 에이전트 세트 정의는 [#13 P2-5] 범위.

| 유형 | 에이전트 (예정) |
|---|---|
| FEAT (신규 기능) | `feat-engineer` |
| FIX (버그) | `fix-engineer` |
| REFACTOR / PERF | `refactor-engineer` |
| DOC (문서·하네스) | `doc-writer` |
| DEPLOY-REVIEW (PR 배포 리뷰) | `pr-reviewer` |

> 에이전트 미등록 상태(현재)에서는 flow 파일이 **"어떤 에이전트가 필요한지만 보고"** 하고 사용자 확인 후 수동 분기. 자동 호출은 #13 완료 후.

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

라우팅을 마친 뒤 아래 네 가지를 반드시 확인한다. 하나라도 실패하면 고치고 다시 검사한다.

1. 선택한 전문 에이전트가 `.claude/agents/` 에 **실제로 존재하는가** — 없으면 사용자에게 되묻는다. (현 시점에는 "에이전트 세트 미등록" 사실을 그대로 보고하고 수동 분기)
2. `work_history/` 에 이번 작업 행이 추가됐는가 — 폴더가 없으면 만들고 다시 기록.
3. 이슈/PR 유형 판정 근거(라벨·본문 키워드·제목)를 **한 줄로 남겼는가**.
4. PR 머지 지점이면 사용자 명시 승인이 로그에 남았는가.

네 가지 모두 통과하면 라우팅 결과와 근거를 사용자에게 보고한다.

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
