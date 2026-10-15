---
name: brainstormer
description: flowmeet-api 작업에서 코드를 쓰기 전에 가능한 접근 방식 2~3개와 트레이드오프를 사용자에게 제시하고 선택을 수집하는 절차. 복잡한 FEAT·REFACTOR·PERF 또는 영향 범위가 넓은 FIX에 로드. plan-writer 선행. 에이전트판([.claude/agents/brainstormer.md](../../agents/brainstormer.md))과 동일 책임의 스킬 버전 — work-manager flow 바깥에서 사용자가 직접 "접근 방식 정렬"을 요청하거나 Claude가 자체 판단으로 사용할 때 로드.
metadata:
  allowed-tools:
    - Read
    - AskUserQuestion
    - Bash(gh issue view:*)
    - Bash(git log:*)
    - Bash(git diff:*)
    - Bash(find:*)
    - Bash(grep:*)
    - Bash(ls:*)
---

# brainstormer — 접근 방식 탐색·정렬 (스킬)

전문 로직(feat/fix/refactor)이 코드를 쓰기 **전에** 접근 방식을 사용자와 정렬한다. 요구사항을 바로 구현으로 밀어붙여 방향이 어긋나는 사고를 예방한다.

> 에이전트판([`.claude/agents/brainstormer.md`](../../agents/brainstormer.md))은 **work-manager `_issue-flow.md` Step 3.3**에서 Read 후 직접 실행된다. 이 스킬 버전은 그 flow 밖에서(사용자가 직접 요청하거나 Claude가 복잡도 판단으로 로드할 때) 같은 절차를 그대로 수행하기 위한 것이다. 두 문서의 로직은 동기 유지 — 한쪽을 바꾸면 다른 쪽도 맞춘다.

## 로드 조건 (복잡도 필터)

| 상황 | 로드 |
|---|---|
| FEAT (복잡 — 영향 파일 3개↑ 또는 DB/보안/외부 연동 포함) | ✅ 필수 |
| REFACTOR, PERF | ✅ 필수 |
| FIX (영향 범위 넓음) | ✅ 필수 |
| FIX (재현 조건 명확 + 수정 범위 좁음) | ⬜ skip 가능 |
| 작은 FEAT (단일 파일·단일 레이어) | ⬜ skip 가능 |
| DOC, 간단한 CHORE | ⬜ skip |

> 사용자가 `--skip-plan` 류의 opt-out을 명시하면 skip. 애매하면 로드.

## 입력 수집 `[로드 시점에 Claude Code가 수집]`

| 변수 | 어디서 |
|---|---|
| `{N}` | 사용자 메시지 또는 현재 작업 중인 GitHub Issue 번호 |
| `{title}` | `gh issue view {N}` 결과 또는 사용자가 제공한 제목 |
| `{body-summary}` | 이슈 본문 요약 — **반드시 [SensitiveDataMasker.scrub()](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt) 통과 후 사용**. `gh issue view` 결과를 날것으로 쓰지 않는다 ([security-logging §4](../security-logging/SKILL.md)) |
| `{labels}` | `gh issue view --json labels` 결과 |

`gh issue view` 결과를 그대로 투명성 출력·토론 본문에 복사 금지. PII·토큰·음성 원문·회의 세부 내용이 섞이면 안 된다 ([TRD §11](../security-logging/SKILL.md#4-민감정보-마스킹)).

## 수행 절차

### 1. 요구사항·제약 정리 `[투명성]`

```
▶ [brainstormer] 이슈 #{N} 요구사항·제약 파악
```

scrub 완료된 `{body-summary}` 에서 다음을 bullet로 정리:

- **달성해야 하는 것** (수용 기준, DoD).
- **지키거나 유지해야 하는 것** (외부 API·DB 스키마·보안 요구).
- **알려진 제약** (성능·호환성·마감).
- **불명확한 지점** (가정 또는 사용자 질문 필요).

불명확 지점이 있으면 `🚧 [병목]` 로 먼저 사용자에게 질문해 clear 한 뒤 접근 탐색 진행.

### 2. 접근 방식 2~3개 생성 `[결정적 비교]`

각 옵션에 대해 아래 **동일한 틀**을 채운다 (사용자가 비교 가능하도록).

```
## 옵션 A — <한 줄 요약>
- 개요: <2~3 문장>
- 영향 파일: <경로 bullet>
- 레이어: <api/application/domain/infrastructure 중 어디가 바뀌는지>
- 트레이드오프:
  · 성능: <예상 수준>
  · 복잡도: <추가되는 추상화·의존성>
  · 호환성: <외부 API/DB 스키마 영향>
  · 소요: <상대 비교>
- 리스크: <실패 시나리오 1~2개>

## 옵션 B — …
## 옵션 C — … (선택)
```

- 옵션은 **서로 유의미하게 다른** 축에 서야 한다 (예: "라이브러리 추가 vs 자체 구현", "쿼리 변경 vs 캐시 추가").
- 거의 동일한 두 옵션(파라미터만 다름)은 하나로 통합.
- 옵션이 사실상 하나뿐이면 **대안 없음**을 사용자에게 솔직히 보고하고 바로 plan-writer 로 넘긴다.

### 3. 사용자 선택 `[AskUserQuestion]`

```
AskUserQuestion({
  question: "이슈 #{N} 접근 방식 어떤 걸로 갈까요?",
  options: [
    { label: "A — <요약>", description: "<트레이드오프 핵심 1줄>" },
    { label: "B — <요약>", description: "..." },
    { label: "C — <요약>", description: "..." }  // 선택
  ]
})
```

- 사용자가 "Other"로 다른 접근을 제시하면 그 안을 **옵션 D** 로 받아 trade-off를 1회 더 정리.
- 선택 결과를 `{chosen}` 에 저장.

### 4. 완료 보고

```
✅ [brainstormer] 접근 방식 결정 — 옵션 {X}
   사유(사용자): "..."
   → plan-writer 로 핸드오프 (docs/plans/{YYYY-MM-DD}-{N}-{slug}.md 작성)
```

- 선택 결과·사유를 다음 단계(plan-writer)에 전달.
- work-manager flow 안이라면 `_issue-flow.md` 호출자에 반환, work_history "작업 상세" 섹션에 "접근 방식: 옵션 {X} — {요약}" 1줄 추가.

## 금지

- ❌ Agent 툴로 subagent 호출. 이 스킬은 **Claude Code가 로드해 그 절차대로 수행**한다 (1-depth 유지).
- ❌ 접근 방식 생성 전에 코드 Edit/Write.
- ❌ 옵션을 **1개만** 제시하고 바로 선택 요청 (사용자 선택 의미 없음 — 그 경우는 "대안 없음" 보고).
- ❌ 사용자 선택 수집 전에 plan-writer 로드/핸드오프.
- ❌ 이슈 본문·GitHub 응답을 그대로 복사 (PII·토큰 금지, [security-logging §4](../security-logging/SKILL.md)).

## 관련

- 에이전트판: [`.claude/agents/brainstormer.md`](../../agents/brainstormer.md) — work-manager flow 호출 전용
- 다음 단계: [`plan-writer`](../plan-writer/SKILL.md)
- 호출자 참조(flow 안일 때): [`work-manager/_issue-flow.md`](../work-manager/_issue-flow.md)
- 상위 규칙: [spring-layering](../spring-layering/SKILL.md), [jpa-query-guard](../jpa-query-guard/SKILL.md), [security-logging](../security-logging/SKILL.md)
