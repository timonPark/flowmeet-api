---
name: brainstormer
description: 복잡한 FEAT·REFACTOR·PERF 작업에서 코드를 쓰기 전에 가능한 접근 방식 2~3개와 트레이드오프를 사용자에게 제시하고 선택을 수집한다. plan-writer 호출 전 선행. Agent 툴 호출 금지 — Claude Code가 Read 후 직접 실행.
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

# brainstormer — 접근 방식 탐색·정렬

work-manager `_issue-flow.md` Step 3.3 에서 호출. **Agent 툴로 호출하지 않는다** — Claude Code가 Read 후 직접 실행한다. (1-depth 유지)

전문 에이전트(feat/fix/refactor-developer)가 코드를 작성하기 **전에** 접근 방식을 사용자와 정렬한다. 요구사항을 바로 구현으로 밀어붙여 방향이 어긋나는 사고를 예방한다.

## 호출 조건 (복잡도 필터)

| 상황 | brainstormer 호출 |
|---|---|
| FEAT (복잡 — 영향 파일 3개↑ 또는 DB/보안/외부 연동 포함) | ✅ 필수 |
| REFACTOR, PERF | ✅ 필수 |
| FIX (재현 조건 명확 + 수정 범위 좁음) | ⬜ skip 가능 |
| 작은 FEAT (단일 파일·단일 레이어) | ⬜ skip 가능 |
| DOC, 간단한 CHORE | ⬜ skip |

> 사용자가 `--skip-plan` 류의 opt-out을 명시하면 skip. 애매하면 호출.

## 입력

| 변수 | 설명 |
|---|---|
| `{N}` | GitHub Issue 번호 |
| `{title}` | 이슈 제목 |
| `{body-summary}` | 이슈 본문 요약 (PII·토큰 제외, scrub 통과) |
| `{labels}` | 라벨 목록 |

## 수행

### 1. 요구사항·제약 정리 `[투명성]`

```
▶ [brainstormer] 이슈 #{N} 요구사항·제약 파악
```

이슈 본문에서 다음을 bullet로 정리 (work_history에도 기록):

- **달성해야 하는 것** (수용 기준, DoD).
- **지키거나 유지해야 하는 것** (외부 API·DB 스키마·보안 요구).
- **알려진 제약** (성능·호환성·마감).
- **불명확한 지점** (가정 또는 사용자 질문 필요).

불명확 지점이 있으면 `🚧 [병목]` 로 먼저 사용자에게 질문해 clear 한 뒤 접근 탐색 진행.

### 2. 접근 방식 2~3개 생성 `[결정적 비교]`

각 옵션에 대해 아래 형식으로 **동일한 틀**을 채운다 (사용자가 비교 가능하도록).

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
- 옵션이 사실상 하나뿐이면 **대안 없음**을 사용자에게 솔직히 보고하고 바로 plan-writer로 넘긴다.

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

- 사용자가 "Other"로 다른 접근을 제시하면 그 안을 **옵션 D**로 받아 trade-off를 1회 더 정리.
- 선택 결과를 `{chosen}` 에 저장.

### 4. 완료 보고

```
✅ [brainstormer] 접근 방식 결정 — 옵션 {X}
   사유(사용자): "..."
   → plan-writer 로 핸드오프 (docs/plans/{YYYY-MM-DD}-{N}-{slug}.md 작성)
```

- 선택 결과·사유를 호출자(`_issue-flow.md`)에 반환.
- work_history "작업 상세" 섹션에 "접근 방식: 옵션 {X} — {요약}" 1줄 추가.

## 금지

- ❌ Agent 툴로 호출.
- ❌ 접근 방식 생성 전에 코드 Edit/Write.
- ❌ 옵션을 **1개만** 제시하고 바로 선택 요청 (사용자 선택 의미 없음 — 그 경우는 "대안 없음" 보고).
- ❌ 사용자 선택 수집 전에 plan-writer 호출.
- ❌ 이슈 본문·GitHub 응답을 그대로 복사 (PII·토큰 금지, [security-logging §4](../skills/security-logging/SKILL.md)).

## 관련

- [work-manager/_issue-flow.md](../skills/work-manager/_issue-flow.md) — 호출자
- [plan-writer](./plan-writer.md) — 다음 단계 에이전트
- 상위 규칙: [spring-layering](../skills/spring-layering/SKILL.md), [jpa-query-guard](../skills/jpa-query-guard/SKILL.md), [security-logging](../skills/security-logging/SKILL.md)
