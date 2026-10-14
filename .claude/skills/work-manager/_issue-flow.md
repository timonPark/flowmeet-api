# Issue 경로 — GitHub Issue 처리 flow

> GitHub Issue URL 또는 `#N` 입력 시 실행. PR URL이면 `_pr-flow.md` 로 이동.

**이 파일은 [#10 P2-2] 범위에서 상세화된다.** 현재는 라우팅 분기점만 성문화하고, 실제 단계별 절차(브랜치 생성·work_history 작성·전문 에이전트 호출·PR 생성)는 다음 이슈에서 포팅.

---

## 현재 (stub) — 입력 수신 시 수행

1. **투명성 포맷**으로 입력 접수 보고.
   ```
   ▶ [입력 수신] GitHub Issue #{N} 접수. flow 상세 포팅은 #10 P2-2 범위 — 수동 안내만 수행.
   ```
2. 번호가 **정수**인지 확인. 아니면 되묻는다 (추측 금지).
3. `gh issue view {N} --repo timonPark/flowmeet-api` 로 이슈 본문·라벨 조회. 404면 번호 재확인 요청.
4. **work_history 플레이스홀더** 생성 — 검증 2번(stub 모드) 만족:
   ```
   work_history/{YYYY-MM-DD}-issue-{N}-{slug}.md
   ```
   frontmatter 최소 필드: `issueKey`, `issueTitle`, `type`(빈 값), `agent: "manual"`, `assignee`, `branch`(빈 값), `baseBranch: main`, `startedAt`, `status: IN_PROGRESS`. 본문에 `gh` 결과를 복사할 때는 PII·토큰이 섞이지 않도록 식별자만 쓰거나 `SensitiveDataMasker.scrub()` 통과.
5. 사용자에게 **다음 수동 절차**를 안내:
   - 브랜치 생성 (`<type>/{N}-{slug}`) — `type` ↔ 브랜치 매핑은 [`_REFERENCE.md`](./_REFERENCE.md#type--브랜치-type-매핑)
   - work_history 상세 편집 (유형 판정 근거·단계별 소요 기록)
   - 적절한 전문 에이전트 호출 (현재 미등록 — [#13] 완료 후 자동화. 미등록 상태에서는 `agent: "manual"` 유지)
6. 사용자가 "진행해주세요"로 승인하면 Claude Code가 수동으로 각 단계를 실행.

## 예정 (상세 포팅: #10)

- Step 0 — 베이스 브랜치·브랜치 성격 사전 수집 (`AskUserQuestion`)
- Step 1 — 브랜치 생성 + 이슈 상태 라벨 전환 (`in-progress`)
- Step 2 — work_history 파일 생성 (frontmatter 자동 작성)
- Step 3 — 전문 에이전트 Agent 호출 ([#13] 에이전트 세트 등록 후)
- Step 4 — 완료 시 PR 생성 + 이슈 라벨 전환 (`in-review`)
- Step 5 — PR 머지는 `_pr-flow.md`로 위임 (**사용자 명시 승인 필수**)

## 입력 검증

| 조건 | 처리 |
|---|---|
| 번호가 정수가 아님 | 사용자에게 되묻는다 (추측 금지) |
| `gh issue view` 실패 (존재하지 않음) | 사용자에게 번호 확인 요청 |
| 이슈가 CLOSED 상태 | "이미 종료된 이슈입니다. 작업을 계속할까요?" 확인 |

## 투명성 포맷 예시

```
▶ [이슈 조회] gh issue view #42 실행 중
✅ [이슈 조회] #42 title="[P1-X] 샘플", labels=[phase-1, feat], state=OPEN
▶ [유형 판정] 라벨 phase-1 + 제목 "FEAT:" → FEAT
✅ [유형 판정] type=FEAT, agent=feat-engineer (예정)
🚧 [에이전트 미등록] feat-engineer 미등록 — 사용자가 수동으로 작업을 진행할까요?
```
