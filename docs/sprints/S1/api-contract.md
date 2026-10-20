---
sprintId: S1
contractRev: v1
status: DRAFT
proposedAt: 2026-10-19
proposedBy: backend-developer
basedOn: pm/sprints/S1/scope.md v1 (SCOPE_DECISION 10-19 10:28)
---

# [S1] API 계약 v1 (DRAFT — FE 합의 전)

> `pm/sprints/S1/scope.md` Must + 채택 Should 범위만 포함. S2 이월분은 빠져 있음.
> 공통 응답/에러 포맷은 `flowmeet-api/.claude/skills/error-handling/SKILL.md`를 그대로 따르고 신설하지 않는다.
> 네임스페이스는 TRD §8과 `flowmeet-api/CLAUDE.md`의 `/api/auth/*`, `/api/dashboard`, `/api/nav/*` 를 따른다.

## 0. 공통

### 0.1 응답 엔벨로프
- **성공**: 각 엔드포인트가 직접 JSON 객체를 반환한다 (별도 envelope 없음). 레거시 호환 금지.
- **에러**: `com.flowmeet.common.exception.ErrorResponse` 그대로.
  ```json
  {
    "errorCode": "AUTH-401",
    "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
    "path": "/api/auth/login",
    "timestamp": "2026-10-19T01:32:00Z",
    "fieldErrors": null
  }
  ```
- **fieldErrors**: Bean Validation 실패 시만 `[{ "field": "...", "reason": "..." }]`, 그 외 `null`.

### 0.2 ErrorCode enum (S1 범위에서 사용/추가되는 것만)
| code | HTTP | 조건 |
|---|---|---|
| `COMMON-400` INVALID_REQUEST | 400 | 요청 바디 형식 오류 (JSON 파싱 실패 등). Bean Validation 실패도 400. |
| `COMMON-401` UNAUTHORIZED | 401 | 보호 리소스에 토큰 없음 또는 유효하지 않음 (JwtAuthenticationFilter) |
| `COMMON-403` FORBIDDEN | 403 | 토큰은 유효하나 권한 부족 |
| `COMMON-500` INTERNAL_ERROR | 500 | 예상치 못한 서버 오류 (스택 로그 마스킹 후 기록) |
| `AUTH-401` INVALID_CREDENTIALS | 401 | 로그인 이메일·비번 불일치 (어느 쪽인지 구분 안 함 — 열거 공격 방지) |
| `AUTH-401-001` EXPIRED_TOKEN | 401 | Access/Refresh 토큰 만료. **FE는 이 코드만 `M-02` 트리거** (아래 §0.4) |
| `AUTH-423` ACCOUNT_LOCKED (신규) | 423 | 로그인 실패 N회 초과 — S1 **구현 범위 밖**, 코드만 예약 (FE가 M-02 아닌 "관리자 문의" 모달로 분기 가능) |
| `AUTH-410` REFRESH_TOKEN_REVOKED (신규) | 401 (http=401, code suffix로 구분) | refresh 재사용 감지(세션 하이재킹) → 전 세션 revoke. S1 구현 포함. |

> `ACCOUNT_LOCKED`는 S1 Must 밖. ErrorCode enum에 **코드만** 예약해 FE 분기 안정화. 실제 N회 잠금 로직은 S2+.

### 0.3 토큰·세션 전략 (D7 — FE Q6 응답)
- **Access Token**: JWT, HS256, 만료 **30분**, 응답 바디로 반환 → **FE는 메모리 저장 (localStorage/sessionStorage 금지)**. `Authorization: Bearer <access>` 로 헤더 전송.
- **Refresh Token**: 불투명 랜덤 문자열(32 bytes URL-safe), 만료 **14일**, DB 테이블 `refresh_tokens` 에 **해시**로 저장. **HttpOnly 쿠키**로 전달.
  - 쿠키 속성: `HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=1209600 (14일)`
  - 로컬 개발(http://localhost): `Secure`는 `application.yml` 의 `app.security.cookies.secure=false` 로 토글 (운영 기본 true).
  - **CSRF**: SameSite=Lax 로 커버 (POST 로그아웃·refresh는 top-level navigation 아님). CSRF 토큰은 **S1 미도입** — SameSite=Lax 로 충분하다는 가정. (OWASP 2024 가이드 참조 — SameSite=Lax + HttpOnly + 짧은 Access 수명이면 CSRF 공격 성립 어려움). 로그인·refresh만 쿠키 사용하고, 그 외 모든 보호 API는 `Authorization: Bearer` 헤더 → 쿠키 없이 요청되므로 CSRF 공격 벡터 없음.
- **401 → M-02 트리거 규칙** (FE Q7 관련): FE는 **`errorCode: AUTH-401-001`**(EXPIRED_TOKEN)만 M-02 트리거.
  - 그 외 401(`AUTH-401`, `COMMON-401`)은 **로그인 실패/비정상 접근** → 로그인 페이지로 리다이렉트.
  - M-02 재인증 흐름: FE가 자동으로 `POST /api/auth/refresh` 호출 → 성공 시 재시도. 실패 시 M-02 모달 노출 → 사용자가 비번 입력 → `POST /api/auth/login` 다시.

### 0.4 Refresh 회전 정책
- `POST /api/auth/refresh` 호출 시 기존 refresh는 **즉시 revoke**, 새 refresh 발급.
- 이미 revoke된 refresh로 다시 호출하면 `AUTH-410 REFRESH_TOKEN_REVOKED` 반환 + **해당 사용자의 모든 refresh 전부 revoke** (세션 하이재킹 방어).

### 0.5 CORS·헤더
- 로컬: `http://localhost:3000` 만 허용 (`application.yml`).
- 허용 메서드: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
- 허용 헤더: `Authorization, Content-Type, X-Request-Id`
- 쿠키 전달용 `Access-Control-Allow-Credentials: true` 필수 (refresh 쿠키).

---

## 1. 인증 (W2)

### API-E2-01 로그인
- `POST /api/auth/login` | 권한: **public** | 이슈: S1-E2.2
- **Request** (body, JSON):
  ```json
  { "email": "admin@flowmeet.local", "password": "Temp#2026" }
  ```
  - `email`: string, 필수, email 포맷, max 255
  - `password`: string, 필수, min 8, max 72 (BCrypt 입력 길이 제한)
- **Response 200** (body):
  ```json
  {
    "accessToken": "eyJhbGciOi...",
    "accessTokenExpiresIn": 1800,
    "tokenType": "Bearer",
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "관리자",
      "email": "admin@flowmeet.local",
      "role": "ADMIN",
      "systemPermission": "ADMIN",
      "requirePasswordChange": true,
      "profileComplete": false
    }
  }
  ```
  - `accessTokenExpiresIn`: 초 단위. FE는 이 값으로 refresh 타이밍 결정.
  - `user.role`: `ADMIN | EXECUTIVE | TEAM_LEADER | TEAM_MEMBER` (고정 4종 — D4 반영).
  - `user.systemPermission`: `ADMIN | USER` (schema.sql enum 그대로).
  - `user.requirePasswordChange`: `users.temporary_password` 플래그 그대로. **true면 FE는 SCR-10_2 로 리다이렉트** — D5 (FE Q7 응답).
  - `user.profileComplete`: 프로필 필수 필드(이름·소속·직급) 전부 채워졌는지. **false면 SCR-10_2 로 리다이렉트** — SCR-10_2 저장 API가 Could이므로 S1에서는 **seed 사용자는 true로 미리 세팅**, FE는 플래그만 읽음. 실제 저장 플로우는 S2.
- **Response Set-Cookie** (헤더):
  - `refreshToken=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=1209600`
- **Errors**:
  - `400 COMMON-400` — 이메일 포맷 불량, 필수 누락 (`fieldErrors` 포함)
  - `401 AUTH-401` — 이메일·비번 불일치 (어느 쪽인지 안 알려줌)
- **보안**: 비번은 로그에 절대 출력 금지. `SensitiveDataMasker.scrub()` 통과한 요약만 로그.
- **FE 메모**: SCR-10_1. mock으로 선행 가능. requirePasswordChange=true seed 사용자 1명으로 SCR-10_2 리다이렉트 플로우 테스트.

### API-E2-02 Access Token 재발급 (Refresh)
- `POST /api/auth/refresh` | 권한: **public (쿠키만)** | 이슈: S1-E2.3
- **Request**: body 없음. `Cookie: refreshToken=<...>` 필수.
- **Response 200** (body):
  ```json
  { "accessToken": "...", "accessTokenExpiresIn": 1800, "tokenType": "Bearer" }
  ```
  - `user` 객체는 포함 안 함 (불필요한 PII 노출 방지, FE는 `GET /api/auth/me`로 별도 조회).
- **Response Set-Cookie**: 새 refreshToken (회전).
- **Errors**:
  - `401 COMMON-401` — 쿠키 없음
  - `401 AUTH-401-001` EXPIRED_TOKEN — refresh 만료 → **M-02 트리거**
  - `401 AUTH-410` REFRESH_TOKEN_REVOKED — 재사용 감지 → 전 세션 revoke 완료, **FE는 즉시 로그인 페이지로 리다이렉트** (M-02 아님)
- **FE 메모**: M-02 재인증 흐름 핵심. 401 응답 받으면 FE의 refresh 큐가 자동 호출.

### API-E2-03 로그아웃
- `POST /api/auth/logout` | 권한: **authenticated** | 이슈: S1-E2.3
- **Request**: body 없음. `Authorization: Bearer <access>` + `Cookie: refreshToken=<...>` 둘 다 선택적(둘 중 하나로 세션 식별).
- **Response 204**: body 없음. refresh 쿠키 **삭제** (`Set-Cookie: refreshToken=; Max-Age=0; Path=/api/auth`).
- **Errors**:
  - `401 COMMON-401` — 토큰·쿠키 모두 없음/유효 X
- **동작**: refresh_tokens 테이블의 해당 토큰을 `revoked_at = now()`.
- **FE 메모**: SCR-12 §1 로그아웃 메뉴 + M-02 "로그아웃 후 종료". 성공 후 `/login` 리다이렉트.

### API-E2-04 (내부 계약) JWT 검증 필터
- 구현만, 외부 API 없음. 이슈: S1-E2.4
- 모든 `/api/**` 요청에 대해 `Authorization: Bearer <token>` 검증. 실패 시 `COMMON-401` 또는 `AUTH-401-001`.
- **예외 경로** (filter skip): `/api/auth/login`, `/api/auth/refresh`, `/actuator/health`
- **FE 메모**: 401 응답 받을 때 FE가 `errorCode` 로 분기하는 유일한 지점.

---

## 2. 사용자·조직 seed (W3-seed)

### API-E3-01 내 정보 조회 (/me)
- `GET /api/auth/me` | 권한: **authenticated** | 이슈: S1-E3.3
- **Request**: 없음. `Authorization: Bearer <access>` 필수.
- **Response 200** (body):
  ```json
  {
    "id": "550e8400-...",
    "name": "관리자",
    "email": "admin@flowmeet.local",
    "role": "ADMIN",
    "systemPermission": "ADMIN",
    "team": {
      "id": "aaaa-...",
      "name": "경영지원본부",
      "parentId": null
    },
    "requirePasswordChange": false,
    "profileComplete": true,
    "navFlags": {
      "showAdmin": true,
      "showExecutive": false,
      "showTeamLead": false
    }
  }
  ```
  - `team`: null 가능 (팀 미배정 사용자).
  - `navFlags`: FE 사이드바가 조건부 노출할 메뉴 플래그. S1에선 role 기반으로 BE가 계산.
    - `showAdmin`: `systemPermission == ADMIN` 또는 `role == ADMIN`
    - `showExecutive`: `role == EXECUTIVE`
    - `showTeamLead`: `role == TEAM_LEADER`
  - **W5가 Should**이므로 `navFlags`는 **E5.1에서 추가**. S1-E3.3 단독으로는 `navFlags` 제외하고 반환.
- **Errors**:
  - `401 COMMON-401` — 토큰 없음/유효 X
  - `401 AUTH-401-001` — 만료
- **FE 메모**: 로그인 직후, M-02 재인증 성공 직후, 공통 셸 마운트 시 호출. React Query `staleTime: 5min` 권장.

### 조직 응답 shape (FE Q8 응답)
S1에선 **GET 조직 트리 API 없음** (CRUD 전부 S2 이월). seed로 들어가는 구조만:
- `teams` 테이블 그대로 flat + `parent_team_id` (nullable). FE가 받으면 트리 재구성.
- `/me` 응답의 `team` 객체도 flat 형식 (`id`, `name`, `parentId`).
- S2에서 `GET /api/organizations` 추가 시 `[{ "id", "name", "parentId", "type": "DIVISION|DEPARTMENT|TEAM" }]` 배열 반환 예정.

> seed 사용자: 관리자 1명(`admin@flowmeet.local`, 임시 비번은 환경변수 `ADMIN_INITIAL_PASSWORD`, 미지정 시 로그 WARN + 자동 생성 후 서버 로그에만 표시). `requirePasswordChange=true`, `profileComplete=true` (SCR-10_2 저장 API가 없으므로 프로필 완료 상태로 seed).

---

## 3. 공통 셸 지원 (W5 — Should)

### API-E5-01 /me 확장 (navFlags 포함)
- API-E3-01 응답에 `navFlags` 필드 추가. 별도 엔드포인트 아님. 이슈: S1-E5.1

### API-E5-02 내비 카운트
- `GET /api/nav/counts` | 권한: **authenticated** | 이슈: S1-E5.2
- **Request**: 없음.
- **Response 200**:
  ```json
  {
    "notifications": 0,
    "pendingApprovals": 0,
    "upcomingMeetings": 0
  }
  ```
  - **S1에선 전부 하드코딩 0**. 실제 집계는 S2+. FE가 상단바 배지 뼈대 구현 가능.
- **Errors**: 401 공통.
- **FE 메모**: WF1 상단바 알림/배지 영역. 폴링 안 함 (RQ staleTime 1분).

### API-E5-03 대시보드 skeleton
- `GET /api/dashboard?dashboardType=MEETING` | `GET /api/dashboard?dashboardType=TEAM` | 권한: **authenticated** | 이슈: S1-E5.3
- **Request**: query `dashboardType` 필수, enum `MEETING | TEAM`
- **Response 200** (type에 따라 다름):
  - MEETING:
    ```json
    {
      "dashboardType": "MEETING",
      "kpis": {
        "totalMeetings": { "value": 0, "deltaFromLastMonth": 0 },
        "inProgressTasks": { "value": 0, "progressRate": 0 },
        "todoTasks": { "value": 0, "highPriority": 0 },
        "completedThisWeek": { "value": 0, "targetRate": 0 },
        "dueSoonTasks": { "value": 0 }
      },
      "recentMeetings": [],
      "delayedTasks": [],
      "generatedAt": "2026-10-19T01:32:00Z"
    }
    ```
  - TEAM:
    ```json
    {
      "dashboardType": "TEAM",
      "kpis": { ... MEETING과 동일 shape ... },
      "teamProgress": [],
      "delayedTasks": [],
      "generatedAt": "..."
    }
    ```
- **Errors**:
  - `400 COMMON-400` — dashboardType 누락/오타 (`fieldErrors: [{"field":"dashboardType","reason":"must be MEETING or TEAM"}]`)
  - `401 COMMON-401`
- **FE 메모**: SCR-01 shell + E-02 빈 상태. 실제 집계는 S2 이후, S1은 shape만 fix.

---

## 4. S1 범위 밖 (명시)

- `POST /api/auth/password/reset` (M-01 Variant A) — S2+
- `POST /api/admin/users/{id}/password/reissue` (M-01 Variant B) — S1 Could, api-contract에서 제외 (S2로 미룸 전제)
- `POST /api/auth/onboarding` (SCR-10_2 저장) — S1 Could, 제외
- `GET /api/users`, `POST /api/users`, `PATCH /api/users/{id}` 등 사용자 CRUD — S2 Must 후보
- `GET /api/organizations`, 조직 CRUD — S2 Must 후보
- `GET /api/sessions`, `DELETE /api/sessions/{id}` — S2+
- 음성·회의·업무 전부 — S2+

---

## 5. 변경 이력

| rev | 일시 | 변경 | 작성자 |
|---|---|---|---|
| v1 (DRAFT) | 2026-10-19 10:32 | 초안 — FE PROPOSAL 전송용. 쿠키/CSRF/플래그/트리 shape 결정 제안 포함 | backend-developer |
