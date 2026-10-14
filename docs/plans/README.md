# docs/plans/

`plan-writer` 에이전트가 각 작업 전에 저장하는 **상세 실행 플랜 아카이브**. git 추적 대상(조직 지식 아카이브).

## 파일명

```
docs/plans/{YYYY-MM-DD}-{N}-{slug}.md
```

- 날짜는 **`date '+%Y-%m-%d'` 출력값 그대로**. 추정·수기 입력 금지.
- slug는 [issue-kickoff §2 결정적 알고리즘](../../.claude/agents/issue-kickoff.md)과 동일.

## frontmatter (평면 key:value — wh-metrics.py 파싱 호환)

| 필드 | 설명 |
|---|---|
| `issueKey` | `#{N}` |
| `issueTitle` | 이슈 제목 |
| `slug` | 결정적 slug |
| `author` | `git config user.name` 값 |
| `createdAt` | `"YYYY-MM-DD HH:mm"` |
| `chosenApproach` | brainstormer가 수집한 접근 요약 (없으면 `"trivial"`) |
| `verifyLevel` | `L0`·`L1`·`L2` |
| `status` | `PROPOSED` → `APPROVED` → `EXECUTED` → `DONE` |

> 템플릿·수명 관리: [plan-writer 에이전트](../../.claude/agents/plan-writer.md).

## 보안 (TRD §11)

**frontmatter·본문 어디에도 다음 데이터를 쓰지 않는다**:

- 비밀번호·토큰·API 키
- 음성 원문·transcript 본문
- 참가자 이메일·전화번호 원본 (식별자 `meeting_id`·`user_id`만)

`gh issue view` body에서 발췌할 때는 `SensitiveDataMasker.scrub()` 통과 후 저장. 상세: [security-logging](../../.claude/skills/security-logging/SKILL.md).

## 수명 관리

- 플랜 파일은 **삭제하지 않는다**. 수명이 끝나도 (`status: DONE`) 조직 지식 아카이브로 유지.
- `*.tmp`, `*.local.md`는 임시·개인 메모 전용 — `.gitignore` 반영.
- 플랜을 참조한 PR이 머지되면 `plan-writer`가 `status: EXECUTED` 또는 `DONE`으로 자동 갱신.
