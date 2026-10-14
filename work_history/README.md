# work_history/

work-manager가 각 작업(이슈·PR)마다 자동 생성하는 로컬 아카이브. **git 추적 대상.**

## 파일명

```
work_history/{YYYY-MM-DD}-issue-{N}-{slug}.md
work_history/{YYYY-MM-DD}-pr-{N}-deploy-risk-review.md
```

- 날짜는 **`date '+%Y-%m-%d'` 출력값 그대로**. 추정·수기 입력 금지.
- slug는 이슈 제목에서 소문자·하이픈·30자 이내로 추출 (상세: [`issue-kickoff` 에이전트](../.claude/agents/issue-kickoff.md)).

## frontmatter

필드 정의 전체는 [`_REFERENCE.md`](../.claude/skills/work-manager/_REFERENCE.md#work_history-frontmatter-필드-정의) 참조.

최소 필수:
`issueKey` · `issueTitle` · `type` · `agent` · `assignee` · `branch` · `baseBranch` · `startedAt` · `status`.

## 보안 (TRD §11)

**frontmatter·본문 어디에도 다음 데이터를 쓰지 않는다**:

- 비밀번호·토큰·API 키
- 음성 원문·transcript 본문
- 참가자 이메일·전화번호 원본 (식별자 `meeting_id`·`user_id`만)

이슈 본문을 참고용으로 발췌할 때는 `SensitiveDataMasker.scrub()` 통과 후 저장. 상세: [security-logging](../.claude/skills/security-logging/SKILL.md).

## 임시·개인 메모

`*.tmp`, `*.local.md`는 `.gitignore` 적용 — 임시·개인 메모 전용.
