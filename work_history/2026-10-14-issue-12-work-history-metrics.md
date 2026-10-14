---
issueKey: "#12"
issueTitle: "[P2-4] work_history 파일·메트릭 체계 (frontmatter + 메트릭 쿼리)"
type: CHORE
jobType: backend
agent: manual
assignee: ParkJH0514
branch: chore/12-work-history-metrics
baseBranch: main
startedAt: "2026-10-14 14:26"
completedAt: ""
totalMinutes: 0
status: IN_PROGRESS
issueUrl: "https://github.com/timonPark/flowmeet-api/issues/12"
prUrl: ""
---

# #12 — [P2-4] work_history 파일·메트릭 체계

## 기본 정보

| 항목 | 값 |
|---|---|
| 이슈키 | `#12` |
| 유형 | CHORE |
| 담당자 | `ParkJH0514` |
| 브랜치 | `chore/12-work-history-metrics` |
| 베이스 | `main` |
| 시작 | 2026-10-14 14:26 KST |
| 완료 | — |
| 소요 | — |
| 상태 | 🔄 진행 중 |

## 유형 판정 근거

- 라벨: `phase-2`
- 본문 키워드: `work_history 파일·메트릭 체계` — 규약 수립 + 집계 CLI
- 판정: `CHORE` (실행 코드 변경 없음, 도구·규약 추가)

## 단계별 소요 기록

| # | 단계 | 시작 | 완료 | 소요 | 상태 |
|---|---|---|---|---|---|
| 1 | wh-metrics.py 작성 | 14:26 | — | — | 🔄 |
| 2 | 샘플 work_history 생성 | — | — | — | ⏳ |
| 3 | _REFERENCE.md 메트릭 섹션 보강 | — | — | — | ⏳ |
| 4 | L0 검증 | — | — | — | ⏳ |
| 5 | PR 생성 + codex triage | — | — | — | ⏳ |

## 상태 변화 이력

| 일시 | 이벤트 | 비고 |
|---|---|---|
| 2026-10-14 14:26 | 🚀 작업 시작 | work-manager 수동 분기 |
| 2026-10-14 14:26 | ✅ 브랜치 생성 | `chore/12-work-history-metrics` |

## 작업 상세

- 의존성 없는 Python 3 스크립트로 frontmatter 파싱 + 집계.
- 그룹 기준: `type` / `assignee` / `agent` / `jobType` / `month`.
- 출력 형식: table (기본) / json.
- 완료 후 작업 완료 시 이 파일의 `completedAt`·`totalMinutes`·`status`를 수동 업데이트 (상세 자동화는 PR flow Step 6-3 참조).

## 메모 / 특이사항

- 이 파일 자체가 **첫 샘플 work_history 레코드**다. 템플릿 역할도 겸한다.
- 메트릭 스크립트 테스트가 실제 데이터로 돌아간다 (완료 후 CHORE 1건 집계).
