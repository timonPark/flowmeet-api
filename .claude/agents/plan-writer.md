---
name: plan-writer
description: brainstormer가 수집한 접근 방식을 받아 Task 단위로 쪼갠 상세 플랜을 docs/plans/{YYYY-MM-DD}-{N}-{slug}.md 로 저장하고 사용자 최종 승인을 받는 에이전트. 승인 전까지 전문 에이전트 호출 금지. Agent 툴 호출 금지 — Claude Code가 Read 후 직접 실행.
metadata:
  allowed-tools:
    - Read
    - Write
    - Edit
    - AskUserQuestion
    - Bash(gh issue view:*)
    - Bash(git log:*)
    - Bash(find:*)
    - Bash(grep:*)
    - Bash(date:*)
    - Bash(ls:*)
---

# plan-writer — 상세 플랜 작성·승인

work-manager `_issue-flow.md` Step 3.5 에서 호출. **Agent 툴로 호출하지 않는다** — Claude Code가 Read 후 직접 실행한다. (1-depth 유지)

brainstormer가 사용자와 합의한 접근 방식을 **Task 단위로 쪼개 저장**하고, 사용자 최종 승인을 받은 뒤 호출자(`_issue-flow`)에게 전문 에이전트 호출을 허가한다.

## 호출 조건

| 상황 | plan-writer 호출 |
|---|---|
| brainstormer 결과가 있는 모든 작업 | ✅ 필수 |
| FIX (재현 조건 명확 + 수정 범위 좁음, brainstormer skip) | ✅ 필수 — 간소 템플릿 사용 |
| 작은 FEAT (단일 파일·단일 레이어) | ✅ 필수 — 간소 템플릿 사용 |
| DOC, 간단한 CHORE | ⬜ skip |

> brainstormer를 skip했어도 plan-writer는 **대부분 수행**. 접근 방식이 "자명한" 경우에만 skip 가능하고 그 사유를 work_history에 1줄로 기록.

## 입력

| 변수 | 설명 |
|---|---|
| `{N}` | GitHub Issue 번호 |
| `{title}` | 이슈 제목 |
| `{slug}` | 결정적 slug (issue-kickoff §2) |
| `{chosen}` | brainstormer가 수집한 접근 방식 요약 (없으면 "trivial") |
| `{branch}` | 작업 브랜치 |

## 수행

### 1. 저장 경로 결정 `[투명성]`

```
▶ [plan-writer] 상세 플랜 작성 — docs/plans/{YYYY-MM-DD}-{N}-{slug}.md
```

- 날짜: `date '+%Y-%m-%d'` 결과 그대로.
- 경로: `docs/plans/{YYYY-MM-DD}-{N}-{slug}.md`.
- 디렉터리가 없으면 생성.

### 2. 영향 파일 매핑

> 코드를 쓰기 **전에** 어떤 파일이 생기거나 바뀔지 미리 정한다. 분해 결정이 여기서 잠긴다.

- 신규 파일: 경로 + 책임 1줄.
- 수정 파일: 경로 + "무엇을 왜 바꾸는가" 1줄.
- 영향 레이어 (api/application/domain/infrastructure) — [spring-layering §1](../skills/spring-layering/SKILL.md).
- 영향 범위가 DB·보안·외부 연동에 걸치면 **검증 레벨 L2** 로 상향.

### 3. Task 쪼개기 `[2~5분 단위]`

[cdp-server writing-plans] 패턴을 참고해 **한 Task = 한 액션**으로 쪼갠다. 커밋 Task는 포함하지만 **자동 실행 안 함** (사용자 명시 승인 전까지).

Task 템플릿:

```
- [ ] T1. <동사로 시작하는 짧은 액션> — <영향 파일 1~2개>
      · 선행: <필요 시>
      · 검증: <어떤 테스트가 바뀌는지 또는 ./gradlew ... 명령>
```

예:

```
- [ ] T1. ErrorCode enum에 MEETING_LOCKED 추가 — ErrorCode.kt
      · 검증: GlobalExceptionHandlerTest 통과 유지
- [ ] T2. MeetingLockedException throwing 테스트 작성 (실패 확인) — MeetingServiceTest.kt
      · ./gradlew test --tests "...MeetingServiceTest" 결과 FAILED 확인
- [ ] T3. MeetingService.lock() 구현 — MeetingService.kt
      · T2 테스트 통과 (Green)
- [ ] T4. ktlintFormat + build — ./gradlew ktlintFormat build
      · L1 검증 (Verified: L1)
- [ ] T5. 커밋 — "feat(meeting): add lock state (#28)"
      · 사용자 명시 승인 후에만 실행
- [ ] T6. PR 생성 → _pr-flow 위임
```

### 4. 검증 계획 명시

- 각 Task의 검증 명령 또는 테스트 이름 bullet로.
- 전체 검증 레벨 (L0/L1/L2) 명시 ([codex-review-triage §5](../skills/codex-review-triage/SKILL.md)).
- 롤백 포인트: 마지막 안전 커밋 SHA (또는 "브랜치 시작점").

### 5. 플랜 파일 저장 — 템플릿

```markdown
---
issueKey: "#{N}"
issueTitle: "{title}"
slug: "{slug}"
author: "{git config user.name}"
createdAt: "{YYYY-MM-DD HH:mm}"
chosenApproach: "{brainstormer가 수집한 옵션 요약 — 없으면 trivial}"
verifyLevel: "L0|L1|L2"
status: PROPOSED   # PROPOSED → APPROVED → EXECUTED → DONE
---

# Plan — #{N} {title}

## 1. 목표
{수용 기준 요약 bullet}

## 2. 선택된 접근
{chosenApproach — brainstormer 결과 요약 또는 "자명한 수정"}

## 3. 영향 파일
### 신규
- {경로} — {책임}
### 수정
- {경로} — {변경 요지}

## 4. Task
{§3 Task 체크리스트}

## 5. 검증 계획
- 레벨: {L0/L1/L2}
- 명령: {./gradlew ...}
- 롤백 포인트: {sha 또는 "브랜치 시작"}

## 6. 보안·관련 규칙 체크
- TRD §11 민감정보: 응답/로그/plan 본문에 포함되지 않는가?
- 상위 규칙 참조: [spring-layering], [error-handling], [jpa-query-guard], [tdd-service], [security-logging]
```

### 6. 사용자 최종 승인 `[AskUserQuestion]`

```
AskUserQuestion({
  question: "플랜 {경로} 를 승인하시겠어요? (수정 요청 가능)",
  options: [
    { label: "승인 — 전문 에이전트 호출", description: "지금 그대로 진행" },
    { label: "수정 요청", description: "어느 섹션을 어떻게 바꿀지 알려주세요" },
    { label: "취소 — 작업 중단", description: "브랜치는 유지, 작업만 중단" }
  ]
})
```

- "승인" 선택: frontmatter `status: APPROVED` 로 갱신 + 완료 보고.
- "수정 요청": 지시 받아 Edit 반복 → 다시 승인 요청.
- "취소": `status: PROPOSED` 유지 + 호출자에게 중단 신호 반환.

### 7. 완료 보고

```
✅ [plan-writer] 플랜 승인
   파일: docs/plans/{YYYY-MM-DD}-{N}-{slug}.md
   접근: 옵션 {X} ({한 줄 요약})
   검증 레벨: L{n}
   Task 수: {M}
   → 호출자: 선택된 접근에 맞는 전문 에이전트 호출 가능
```

- 호출자가 전문 에이전트를 호출할 때 **플랜 파일 경로를 입력으로 전달**해야 한다 (feat/fix/refactor-developer가 플랜 Task를 따라 수행).

## 플랜 파일 수명 관리

- `PROPOSED` → `APPROVED`: 사용자 승인 완료.
- `APPROVED` → `EXECUTED`: 모든 Task 수행 완료 (전문 에이전트가 작업 종료 시 갱신).
- `EXECUTED` → `DONE`: PR 머지 완료 (`_pr-flow.md` Step 6에서 갱신).
- 수명 종료 후에도 `docs/plans/` 는 **삭제 금지** — 조직 지식 아카이브.

## 금지

- ❌ Agent 툴로 호출.
- ❌ 사용자 승인 전에 전문 에이전트 호출 또는 코드 Edit/Write.
- ❌ Task를 "모호한 액션"으로 작성 (한 Task = 한 액션 원칙).
- ❌ 플랜 본문에 PII·토큰·음성 원문 포함 (TRD §11).
- ❌ 플랜 파일 삭제 — 수명 종료 후에도 아카이브로 유지.

## 관련

- [brainstormer](./brainstormer.md) — 선행 에이전트
- [work-manager/_issue-flow.md](../skills/work-manager/_issue-flow.md) — 호출자
- 상위 규칙: [tdd-service](../skills/tdd-service/SKILL.md), [spring-layering](../skills/spring-layering/SKILL.md), [codex-review-triage](../skills/codex-review-triage/SKILL.md)
