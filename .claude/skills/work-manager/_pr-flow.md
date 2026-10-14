# PR 경로 — GitHub PR 처리 flow

> GitHub PR URL 입력 시 실행. Issue URL이면 `_issue-flow.md` 로 이동.

**이 파일은 [#11 P2-3] 범위에서 상세화된다.** 현재는 라우팅 분기점과 **머지 승인 규칙**만 성문화하고, 리뷰·댓글 답글·머지 자동화 상세는 다음 이슈에서 포팅.

---

## 현재 (stub) — 입력 수신 시 수행

1. **투명성 포맷**으로 입력 접수 보고.
   ```
   ▶ [입력 수신] GitHub PR #{N} 접수. flow 상세 포팅은 #11 P2-3 범위 — 리뷰·머지 수동 안내.
   ```
2. `gh pr view {N} --repo timonPark/flowmeet-api` 로 PR 상태·변경 범위 조회.
3. 사용자에게 다음 중 하나를 안내:
   - **리뷰 요청**: codex 2차 리뷰 흐름은 [codex-review-triage](../codex-review-triage/SKILL.md) 참조.
   - **머지 요청**: 아래 "머지 승인 규칙" 적용.

## 머지 승인 규칙 `[최우선]`

CLAUDE.md "PR 리뷰·머지 규칙"을 그대로 따른다. 요약:

- ❌ 자동 머지 금지. codex 리뷰·`./gradlew build` 통과·CI 그린만으로는 머지 조건 성립 아님.
- ✅ 사용자가 **해당 PR에 대해 명시적으로** "머지해줘" 라고 지시한 경우에만 `gh pr merge` 실행.
- ✅ 머지 지시가 모호하면 (여러 PR이 열려 있거나 범위 불명확) 어떤 PR인지 확인 질문.
- ✅ 머지 후: 이슈·에픽 체크박스 업데이트 → **다음 이슈 착수는 별도 지시 대기**.

### 머지 실행 명령 (사용자 명시 승인 후)

```bash
gh pr merge {PR번호} --repo timonPark/flowmeet-api --squash --delete-branch
git checkout main && git pull
git branch -d <head-branch>
```

## 예정 (상세 포팅: #11)

- Step 1 — PR 상태·변경 범위 분석
- Step 2 — PR 인라인 댓글 답글 확인 (답글 없는 댓글이 있으면 처리 선행)
- Step 3 — codex-review-triage 흐름 자동 호출 (선택)
- Step 4 — 사용자 명시 승인 후 머지 + 로컬 정리 + 이슈/에픽 체크
- Step 5 — work_history 완료 처리 (`completedAt`·`totalMinutes` 기록)

## 투명성 포맷 예시

```
▶ [PR 조회] gh pr view #22 실행 중
✅ [PR 조회] #22 title="feat: ...", state=OPEN, mergeStateStatus=CLEAN
▶ [리뷰 상태] 사람 리뷰 승인 수 · codex 리뷰 산출물 확인
✅ [리뷰 상태] 사람 승인 1, codex 리뷰 유효 지적 전부 반영
🚧 [머지 승인 대기] 사용자가 "머지해줘" 지시 전까지 대기.
```

## 금지

- ❌ 사용자 명시 승인 없이 `gh pr merge` 실행.
- ❌ "앞서 한 번 머지 승인받았으니 다음 PR도 자동 머지" — 승인은 PR별로 1회성.
- ❌ 사람 리뷰 생략 — codex 리뷰는 보조 레인이지 대체재 아님.
