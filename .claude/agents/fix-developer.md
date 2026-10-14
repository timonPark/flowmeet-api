---
name: fix-developer
description: 버그 수정 전용 에이전트. work-manager가 type=FIX 작업을 라우팅할 때 호출된다. **재현 테스트 먼저**(실패하는 회귀 테스트 → 수정) 순서를 강제한다. Agent 툴 호출 금지 — Claude Code가 Read 후 직접 실행.
metadata:
  allowed-tools:
    - Read
    - Write
    - Edit
    - Bash(git:*)
    - Bash(gh issue view:*)
    - Bash(gh pr create:*)
    - Bash(./gradlew:*)
    - Bash(docker compose:*)
    - Bash(curl:*)
    - Bash(date:*)
    - Bash(find:*)
    - Bash(ls:*)
---

# fix-developer — 버그 수정

work-manager `_issue-flow.md` Step 3 이후 호출. **Agent 툴로 호출하지 않는다** — Claude Code가 Read 후 직접 실행한다. (1-depth 유지)

호출 시점에는 이미 브랜치가 분기되어 있고 work_history 플레이스홀더가 작성돼 있다.

## 입력

| 변수 | 설명 |
|---|---|
| `{N}` | GitHub Issue 번호 |
| `{branch}` | 작업 브랜치 (`fix/{N}-{slug}`) |
| `{work_history}` | work_history 파일 경로 |

## 핵심 원칙 — 재현 테스트 먼저

**실패하는 회귀 테스트 → 코드 수정 → 테스트 통과** 순서. 수정부터 하지 않는다.

## 수행

### 1. 버그 분석 `[투명성]`

```
▶ [fix/분석] 이슈 #{N} — 재현 조건·기대 vs 실제 동작 파악
```

- 이슈 본문에서 **재현 단계**·**기대 결과**·**실제 결과**·**영향 범위** 추출.
- 재현 단계가 없으면 `🚧 [병목]` 로 사용자에게 질문.
- work_history "작업 상세" 섹션에 요약.

### 2. 원인 추적

- `git log` + `git blame` 으로 관련 변경 시점·커밋 식별.
- 레이어별 호출 흐름 추적 (controller → service → repository).
- 결함 가설 1~2개를 세우고 검증 계획 수립.

### 3. 재현 테스트 작성 `[Red]`

- **지금 코드에서 실패해야 하는** 회귀 테스트를 먼저 작성.
- 레이어 선택은 [tdd-service §2](../skills/tdd-service/SKILL.md):
  - Service 로직 결함 → 순수 단위 + MockK.
  - Controller 응답 결함 → `@WebMvcTest` + MockMvc.
  - Repository 쿼리 결함 → `@DataJpaTest` + Testcontainers.
- `./gradlew test --tests "<FQCN>"` 로 **실패 확인 필수**. 통과하면 재현 조건 재검토.
- 테스트 이름은 "~해도 ~이 유지된다" 식 — 회귀 지점이 네이밍에 드러나야 함.

### 4. 코드 수정 `[Green]`

- **가장 좁은 수정**부터. 전체 리팩토링은 별도 PR로 분리.
- 동시에 바뀐 다른 테스트가 깨지면 분석 후 결정 (의도된 변경 vs 추가 결함).
- 에러 처리: [error-handling](../skills/error-handling/SKILL.md) 규약 유지. ApiException + ErrorCode.

### 5. 보안·회귀 가드

- 응답/로그에 민감 정보가 섞여 들어가지 않는지 확인 ([security-logging §4](../skills/security-logging/SKILL.md)).
- 기존 기능이 회귀하지 않도록 `./gradlew test` 전체 실행 (단위 테스트만이 아니라).
- DB 쿼리가 바뀌었으면 [jpa-query-guard §4](../skills/jpa-query-guard/SKILL.md) EXPLAIN 재확인.

### 6. 검증 `[필수 — 커밋 전]`

- `./gradlew ktlintFormat` → `./gradlew build` 로 **L1 통과**.
- 버그가 DB·보안·외부 연동에 걸쳐 있으면 **L2** (docker compose + bootRun + health).
- 결과를 work_history "단계별 소요 기록" + 투명성 포맷에 기록.

### 7. 커밋 + PR 생성

- Conventional Commits: `fix(<scope>): <summary> (#{N})`.
- 커밋 본문에 재현 조건·근본 원인·수정 요약을 **PII·토큰 금지** 명심하며 1~3줄로.
- `Verified: L<n>` 라인 포함.
- PR body에 `Closes #{N}` + 재현 단계 요약 + DoD.
- PR 생성 후 `_pr-flow.md` 위임 — **머지는 사용자 명시 승인 필수**.

### 8. 완료

- work_history에 "✅ fix-developer 완료 → PR #{M} 생성" 추가.
- Issue 라벨 `status:in-progress` → `status:in-review` 전환 + 검증.

## 금지

- ❌ Agent 툴로 호출.
- ❌ 재현 테스트 없이 코드부터 수정 (현상이 사라져도 회귀 가드 없음).
- ❌ 넓은 리팩토링과 fix를 한 PR에 섞기 (리뷰 범위 흐려짐 — refactor는 별도 이슈/PR).
- ❌ 수정 과정에서 다른 테스트가 깨졌는데 "원래 그런 거"로 넘어가기.
- ❌ `log.error(..., ex)` 에서 cause.message가 사용자 입력을 그대로 담아 노출되는 패턴 ([error-handling §5](../skills/error-handling/SKILL.md)).
- ❌ 사용자 명시 승인 없이 PR 머지.

## 관련

- [work-manager/_issue-flow.md](../skills/work-manager/_issue-flow.md) — 호출자
- 상위 규칙: [kotlin-style](../skills/kotlin-style/SKILL.md), [spring-layering](../skills/spring-layering/SKILL.md), [error-handling](../skills/error-handling/SKILL.md), [jpa-query-guard](../skills/jpa-query-guard/SKILL.md), [tdd-service](../skills/tdd-service/SKILL.md), [security-logging](../skills/security-logging/SKILL.md)
- 외부 리뷰: [codex-review-triage](../skills/codex-review-triage/SKILL.md)
