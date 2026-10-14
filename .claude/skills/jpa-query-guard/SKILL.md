---
name: jpa-query-guard
description: flowmeet-api의 JPA·PostgreSQL 쿼리 가드. N+1 방지, 읽기 전용 트랜잭션, 인덱스 적중, EXPLAIN 체크, 페이지네이션, 동시성 제어. 신규 Repository 메서드/쿼리를 추가하거나 성능 문제를 디버깅할 때 로드.
---

# JPA / PostgreSQL Query Guard

**dev 환경 데이터가 적어 안 보이는 성능 문제를 코드 작성 단계에서 가드**하기 위한 체크리스트. 운영 규모에 들어서야 드러나는 N+1·전 테이블 스캔·offset 깊이 등을 저장소 레이어에서 미리 차단한다.

## 1. N+1 방지 (반드시)

JPA 지연 로딩이 반복 호출되는 전형적 함정. **상위 엔티티 N건 조회 후 각 엔티티의 연관을 터치하면 N+1 쿼리가 자동으로 나간다.**

### 선택 기준

| 상황 | 권장 수단 |
| --- | --- |
| 단건 연관(@ManyToOne) 1개 함께 가져오기 | `@EntityGraph(attributePaths = ["organizer"])` |
| 1:N 컬렉션 1개 함께 가져오기 | JPQL `JOIN FETCH` (orderBy·중복은 `DISTINCT`로 처리) |
| 1:N 컬렉션 **여러 개** 함께 | 쿼리 분리 + `@BatchSize(size = ...)` — `JOIN FETCH` 2개는 MultipleBagFetchException 유발 |
| 조회 전용·필드가 제한적 | **DTO Projection** (JPQL `select new ...` 또는 Spring Data Projection) — [spring-layering §3](../spring-layering/SKILL.md) 참조 |

```kotlin
// 좋음: 단건 연관 + 1:N 컬렉션 함께 (JOIN FETCH + DISTINCT)
@Query(
    """
    SELECT DISTINCT m FROM Meeting m
    JOIN FETCH m.organizer
    LEFT JOIN FETCH m.participants
    WHERE m.status = :status AND m.scheduledStartAt BETWEEN :from AND :to
    """,
)
fun findScheduledWithParticipants(status: MeetingStatus, from: Instant, to: Instant): List<Meeting>
```

```kotlin
// 조심: 1:N 컬렉션 2개를 동시에 JOIN FETCH → MultipleBagFetchException
// 해결: @BatchSize로 쪼개서 두 번째 컬렉션을 N+1 → N/배치 수로 축소
@BatchSize(size = 50)
val participants: List<MeetingParticipant>
```

### 금지

- ❌ Service 안에서 Entity 리스트를 돌면서 `meeting.participants.size` 같은 접근 — 지연 로딩 트리거.
- ❌ Hibernate 설정의 `default_batch_fetch_size`에 의존해 코드에서 의도를 숨기기 — 쿼리별로 명시.

## 2. 읽기 전용 트랜잭션

```kotlin
@Service
class FindMeetingsService(private val repo: MeetingRepository) {
    @Transactional(readOnly = true) // dirty check·flush 생략 → 간단 조회에서 CPU/메모리 절약
    operator fun invoke(query: MeetingSearchQuery): List<MeetingView> = ...
}
```

- **조회 유스케이스는 전부 `readOnly = true`.** 트랜잭션 경계는 Application 레이어에만 ([spring-layering §3](../spring-layering/SKILL.md)).
- Controller에서 `@Transactional` 금지.

## 3. 인덱스 체크 (신규 쿼리 추가 시)

새 쿼리를 추가할 때마다 `database/schema.sql`에 해당 필터·조인·정렬 컬럼의 인덱스가 선언돼 있는지 확인.

### 체크 순서

1. **WHERE 절의 모든 컬럼**이 인덱스에 포함돼 있는가? (복합 인덱스는 **선두 컬럼부터 순차 적중**이 중요)
2. **ORDER BY 컬럼**이 WHERE 절 뒤에 복합 인덱스로 이어져 있는가? (`LIMIT N`에서 정렬 비용 절감)
3. **JOIN 키**가 FK 측에 인덱스로 있는가?
4. 없다면 schema.sql에 `CREATE INDEX idx_<table>_<columns>` 추가.

### 금지

- ❌ WHERE 절 컬럼에 **함수**를 씌움 (`DATE(scheduled_start_at)`, `LOWER(email)`, `scheduled_start_at::date` 등) — **인덱스 미적중**. 범위 조건으로 바꾸거나 함수 인덱스(`CREATE INDEX ... ON t ((DATE(col)))`) 추가.
- ❌ `LIKE '%keyword%'` — 선두 와일드카드는 인덱스 사용 불가. 전문 검색은 `pg_trgm` + GIN 또는 별도 검색 엔진.
- ❌ 캐스팅으로 타입 변환 (`WHERE id::text = ?`) — 인덱스 미적중.

## 4. EXPLAIN ANALYZE 레퍼런스

### 재현 환경

```bash
docker compose up -d   # schema.sql 자동 적용
docker compose exec postgres psql -U flowmeet -d flowmeet
```

샘플 데이터 삽입 (dev 환경 전용 — 운영 DB에서 실행 금지):

```sql
-- users 100, meetings 10000, meeting_participants ~4000
INSERT INTO users (name, email, password_hash)
SELECT 'User ' || i, 'u' || i || '@example.com', 'x'
FROM generate_series(1, 100) AS i;

INSERT INTO meetings (title, organizer_id, scheduled_start_at, scheduled_end_at, status)
SELECT
  'Meeting ' || i,
  (SELECT id FROM users OFFSET (i % 100) LIMIT 1),
  NOW() + ((i % 60) || ' days')::interval + ((i % 24) || ' hours')::interval,
  NOW() + ((i % 60) || ' days')::interval + ((i % 24) || ' hours')::interval + '1 hour'::interval,
  CASE WHEN i % 10 = 0 THEN 'COMPLETED'::meeting_status ELSE 'SCHEDULED'::meeting_status END
FROM generate_series(1, 10000) AS i;

INSERT INTO meeting_participants (meeting_id, user_id)
SELECT m.id, u.id FROM meetings m CROSS JOIN users u
WHERE random() < 0.004
ON CONFLICT (meeting_id, user_id) DO NOTHING;

ANALYZE users; ANALYZE meetings; ANALYZE meeting_participants;
```

### 샘플 쿼리 비교 — 인덱스 적중/미적중

시나리오: "특정 날짜(2026-10-20)에 예정된 회의 목록 20건".

#### 안티패턴 — WHERE 절에 함수를 씌워 인덱스 뒤쪽 컬럼 미적중

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, title, scheduled_start_at
FROM meetings
WHERE status = 'SCHEDULED'
  AND DATE(scheduled_start_at) = '2026-10-20'
ORDER BY scheduled_start_at ASC
LIMIT 20;
```

실제 플랜:

```
 Limit  (cost=0.29..335.98 rows=20 width=36) (actual time=0.481..0.497 rows=20 loops=1)
   Buffers: shared hit=857
   ->  Index Scan using idx_meetings_status_scheduled_start on meetings
       Index Cond: (status = 'SCHEDULED'::meeting_status)
       Filter: (date(scheduled_start_at) = '2026-10-20'::date)
       Rows Removed by Filter: 835
       Buffers: shared hit=857
 Execution Time: 0.514 ms
```

→ `status`만 Index Cond로 적중, `DATE(scheduled_start_at)`는 **Filter 단계로 밀려** 855건을 읽고 836건을 버린다. 복합 인덱스의 뒤쪽 컬럼이 함수에 가려져 미적중.

#### 개선 — 범위 조건으로 바꿔 복합 인덱스 완전 적중

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, title, scheduled_start_at
FROM meetings
WHERE status = 'SCHEDULED'
  AND scheduled_start_at >= TIMESTAMPTZ '2026-10-20 00:00:00+09'
  AND scheduled_start_at <  TIMESTAMPTZ '2026-10-21 00:00:00+09'
ORDER BY scheduled_start_at ASC
LIMIT 20;
```

실제 플랜:

```
 Limit  (cost=0.29..37.04 rows=20 width=36) (actual time=0.005..0.012 rows=20 loops=1)
   Buffers: shared hit=22
   ->  Index Scan using idx_meetings_status_scheduled_start on meetings
       Index Cond: ((status = 'SCHEDULED'::meeting_status)
                    AND (scheduled_start_at >= ...)
                    AND (scheduled_start_at <  ...))
       Buffers: shared hit=22
 Execution Time: 0.018 ms
```

→ 복합 인덱스 `(status, scheduled_start_at)`가 세 조건 모두를 Index Cond로 받아낸다. **읽은 블록 857 → 22 (약 39배 감소)**, **실행 시간 0.514 → 0.018ms (약 28배)**. 데이터 규모가 커질수록 격차가 선형 이상으로 벌어진다.

### 해석 포인트

- `Index Cond` vs `Filter`: Filter에 밀린 조건은 인덱스가 아니라 **읽은 뒤 거르는** 비용. 반드시 Index Cond로 끌어올 것.
- `Buffers: shared hit=N`: 읽은 캐시 블록 수. **읽는 블록 수가 줄어드는 게 쿼리 튜닝의 1차 목표**.
- `Rows Removed by Filter`가 크면 인덱스가 선택성이 낮거나 함수/캐스팅이 끼었다는 신호.
- `Seq Scan`은 소규모 테이블에선 정상이지만 대형 테이블(10k↑)에서 나오면 인덱스를 다시 검토.

## 5. 페이지네이션

- **offset 금지** (`LIMIT 20 OFFSET 10000`): offset이 커질수록 선두 N건을 읽고 버린다. 스캔 비용 선형 증가.
- **cursor 기반** 사용: 마지막으로 본 행의 **정렬 키 값**을 다음 요청의 WHERE 조건으로 넘긴다.

```sql
-- 안티: offset
SELECT ... FROM meetings
WHERE status = 'SCHEDULED'
ORDER BY scheduled_start_at ASC
LIMIT 20 OFFSET 10000;

-- 좋음: cursor
SELECT ... FROM meetings
WHERE status = 'SCHEDULED' AND scheduled_start_at > :lastSeenStart
ORDER BY scheduled_start_at ASC
LIMIT 20;
```

- 정렬 키가 비유니크면 **(정렬 키, PK)** 쌍으로 cursor 비교. 안정적인 페이지네이션 보장.

## 6. 동시성 제어

### 비관적 락 (`@Lock(PESSIMISTIC_WRITE)`)

- 사용 기준: **쓰기 경합이 있고 짧은 트랜잭션** (예: 예약 중복 방지 TRD §4.2).
- 사용 금지: 조회 전용, 긴 트랜잭션, 분산 환경에서 전역 락 대용.

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meeting m WHERE m.id = :id")
fun findForUpdate(id: UUID): Meeting?
```

### DB 제약으로 방어 (선호)

- 스키마에 **EXCLUDE 제약**이 걸려 있으면 애플리케이션 락 없이도 중복 방지됨.
- 예: `meetings_no_venue_overlap`, `meetings_no_url_overlap` — 같은 location/URL의 시간대 중복을 PostgreSQL이 거부.
- 중복 삽입 시도는 `ApiException(ErrorCode.CONFLICT)`로 변환해 응답 ([error-handling §3](../error-handling/SKILL.md)).

## 7. 리뷰 체크리스트

- [ ] 새 Repository 메서드가 N+1을 유발하지 않는가 (@EntityGraph/JOIN FETCH/@BatchSize 중 하나 명시)?
- [ ] 조회 유스케이스에 `@Transactional(readOnly = true)` 적용되었는가?
- [ ] WHERE·ORDER BY 컬럼이 schema.sql의 인덱스로 커버되는가? 아니면 인덱스를 추가했는가?
- [ ] WHERE 절 컬럼에 함수·캐스팅이 끼어 인덱스 뒤쪽 컬럼이 Filter로 밀리지 않는가?
- [ ] 신규 쿼리에 대해 EXPLAIN ANALYZE를 1회 돌려 `Buffers`·`Index Cond` 적중을 확인했는가?
- [ ] 페이지네이션이 offset 기반이 아닌 cursor 기반인가?
- [ ] 쓰기 경합 지점에 비관적 락 또는 DB EXCLUDE 제약 중 하나가 걸려 있는가?

## 8. 관련 스킬·문서

- 상위 규칙: [kotlin-style](../kotlin-style/SKILL.md), [spring-layering](../spring-layering/SKILL.md)
- 예외 변환: [error-handling](../error-handling/SKILL.md) — 중복/제약 위반 → `ErrorCode.CONFLICT`
- 스키마·인덱스: `database/schema.sql` + TRD §4
