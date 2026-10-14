---
name: security-logging
description: flowmeet-api의 보안·로깅 규칙 (TRD §11 기반). JWT 발급/검증, 비밀번호 해시, 음성·파일 URL 보호, 로그 금지 항목, 민감정보 마스킹 유틸. 인증·파일 다운로드·로그 라인을 쓰기 전 로드.
---

# Security & Logging — flowmeet-api

**TRD §11을 하네스 규칙으로 성문화.** 민감 정보(비밀번호·JWT·음성 원문·회의 세부 내용)는 응답과 로그 어디에도 유출되면 안 된다. 저장은 해시, 전송은 TLS, 접근은 권한 검사, 로그는 구조화·마스킹.

## 1. JWT 처리

### 발급·검증 규칙

- **Access / Refresh 분리**. Access는 짧게(예: 30분), Refresh는 길게(예: 14일). Refresh는 `refresh_tokens` 테이블에 저장(디바이스별 revoke 가능).
- **Secret은 환경변수(`JWT_SECRET`)** — 소스에 하드코딩 금지. 운영은 32바이트↑ 랜덤.
- 알고리즘은 **HS256(대칭) 또는 RS256(비대칭)** 중 하나. HS256은 secret 유출 시 서명 위조 가능이라 운영은 RS256 권장(공개키만 분산).
- 토큰 payload 최소화 — `sub`(user id), `role`, `exp`, `iat`, `jti`. **이름·이메일·전화번호 등 PII 포함 금지**.
- 서명 검증 실패/만료는 모두 `ApiException(ErrorCode.EXPIRED_TOKEN)` 또는 `UNAUTHORIZED`로 변환 — 원인 메시지는 응답에 노출 금지([error-handling §3](../error-handling/SKILL.md)).

### 금지

- ❌ 소스에 `JWT_SECRET` 상수 하드코딩.
- ❌ JWT를 그대로 로그에 쓰기 (`log.info("token={}", jwt)`) — 반드시 [SensitiveDataMasker.token()](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt) 통과.
- ❌ `localStorage` 저장 전제 설계 — Refresh는 `HttpOnly Secure` 쿠키 또는 Access-only 설계 선택.

## 2. 비밀번호

- 저장은 **BCrypt** 해시(cost 10~12). plain·단방향이 아닌 해시(MD5·SHA1 등) 금지.
- 응답 DTO에 `passwordHash` 필드 포함 금지 — `UserResponse.from(user)`에서 아예 제외([spring-layering §6](../spring-layering/SKILL.md)).
- 로그에는 길이도 노출하지 말 것 — `SensitiveDataMasker.password()` = `"***"` 상수.
- 로그인 실패는 **"이메일 또는 비밀번호가 올바르지 않습니다"** 통일 메시지 (사용자 존재 유무 유출 금지, `ErrorCode.INVALID_CREDENTIALS`).

## 3. 음성 파일·업로드 보호

- 로컬 저장소: `uploads/`. 운영은 S3 교체 가능하도록 **포트/어댑터 분리** (TRD §13).
- **직접 URL 노출 금지** — 다음 중 하나:
  1. **Proxy 다운로드**: `GET /api/meetings/{id}/audio` — Service에서 회의 권한 검증 후 스트림 응답.
  2. **서명된 임시 URL**: 운영 S3라면 presigned URL(만료 ≤ 5분) + IP/권한 바인딩.
- 어떤 다운로드·조회도 **회의 권한 검증 필수** — "URL을 안다"로 접근 불가하게 설계.
- 음성 파일 **원문·전사(transcript) 세부 내용은 로그에 쓰지 않는다**. 식별자(`meeting_id`, `audio_file_id`)만 로그.

## 4. 로그 금지 항목 (TRD §11)

| 항목 | 로그 가능 대체 | 비고 |
| --- | --- | --- |
| 비밀번호·해시 | `***` (`SensitiveDataMasker.password()`) | 길이도 노출 금지 |
| JWT·API 키·Bearer 토큰 | 앞 4자 + 길이 (`SensitiveDataMasker.token(jwt)`) | 서명 복원 방지 |
| 이메일 | 마스킹(`j******e@example.com`) 또는 user id 사용 | user id가 식별에 충분하면 우선 사용 |
| 전화번호 | 뒤 4자리만 (`SensitiveDataMasker.phone(no)`) | 숫자 외 포맷은 유지 |
| 음성 원문·transcript | 식별자(`meeting_id`, `audio_file_id`)만 | 내용 노출 금지 |
| 회의 세부 내용(제목·메모·결정사항) | 식별자만 | WARN/ERROR 로그에 섞여 들어가지 않도록 주의 |

### 구조화 로깅 선호

```kotlin
// 좋음: key=value 구조화. 민감 필드는 마스킹 함수 통과.
log.info("login success userId={} email={}", user.id, SensitiveDataMasker.email(user.email))

// 나쁨: free-form + 민감값 직접 삽입
log.info("User ${user.email} logged in with password=${password}")
```

- MDC(Mapped Diagnostic Context)에 `userId`, `requestId`, `meetingId`를 넣고 포맷터는 구조화 로그(JSON)로 출력.
- Free-form 메시지가 불가피하면 **[SensitiveDataMasker.scrub()](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt)** 통과 후 로그.
  - **보장 범위**: 이메일 / Bearer 토큰 / JWT / `password=…`·`pwd=…`·`secret=…` / `api_key=…`·`apiKey=…`·`X-API-Key: …` 패턴.
  - **보장 안 됨**: 커스텀 필드명, 음성 원문·회의 세부 내용. 이들은 **구조화 로깅 + 호출 측 마스킹이 1차 방어선**이고 `scrub()`은 최종 방어선일 뿐이다.

## 5. 민감정보 마스킹 유틸

파일: [`com.flowmeet.common.logging.SensitiveDataMasker`](../../../src/main/kotlin/com/flowmeet/common/logging/SensitiveDataMasker.kt).

| 메서드 | 입력 예 | 결과 예 |
| --- | --- | --- |
| `email(v)` | `john.doe@example.com` | `j******e@example.com` |
| `phone(v)` | `+82-10-1234-5678` | `+**-**-****-5678` |
| `token(v)` | `eyJhbGci...sig` | `eyJh…(132chars)` |
| `password()` | — (상수) | `***` |
| `scrub(msg)` | `"login by john@a.com Bearer eyJx..."` | `"login by j**n@a.com Bearer eyJx…(…chars)"` |

- **단위 테스트**: [`SensitiveDataMaskerTest`](../../../src/test/kotlin/com/flowmeet/common/logging/SensitiveDataMaskerTest.kt) — 각 메서드의 경계·null·마스킹 결과 검증.

## 6. 녹음 안내 (TRD §11)

- 음성 녹음 시작 전 **참여자에게 안내 UI/음성 재생**이 필요. API는 녹음 상태 플래그(`recording_consent_shown`)를 제공해 프런트가 사용자에게 안내했음을 서버에 기록할 수 있도록 한다.
- 녹음 메타데이터에 **동의 시각(`consented_at`)** 보관 — 분쟁 대응용.

## 7. 권한 검증 지점

- 모든 보호 리소스 접근은 **백엔드에서 검증** (TRD §6). 프런트 가드만 믿지 말 것.
- 회의 리소스는 **"참가자 또는 조직자 또는 관리자"** 중 하나로 permit — 비관리자가 타인 회의에 접근 못 하도록 Service 레이어에서 체크.
- 파일 다운로드는 **접근할 때마다 권한 재검증** — presigned URL이라도 만료·revoke 가능하게 설계.

## 8. 로그백 설정 (선택)

- `logback-spring.xml` 또는 Spring Boot `logging.pattern.*`을 사용해 **구조화 JSON 로그 출력**을 고려. ELK/CloudWatch 수집 호환.
- 패턴 레이어에서도 민감 필드가 숨어들 수 있으니, **코드 레벨에서 마스킹한 값만 로그 호출**로 넘기는 것이 최종 방어선.

## 9. 금지 사항 (요약)

- ❌ 비밀번호·토큰·음성 원문·transcript를 응답/로그에 노출.
- ❌ `log.info("...${password}...")` 처럼 string template에 민감 값 삽입.
- ❌ 사용자 존재 유무를 응답 메시지로 구별할 수 있게 만듦 (`"해당 이메일의 사용자 없음"` 등).
- ❌ 음성 파일 URL을 응답에 그대로 제공 (`"audioUrl": "/uploads/xxx.wav"`).
- ❌ 로그인 성공/실패를 ApiException WARN에서 cause 포함 로그 (overrideMessage에 민감값이 있을 수 있음 — [error-handling §5](../error-handling/SKILL.md)).

## 10. 리뷰 체크리스트

- [ ] 새 Controller/Service가 비밀번호·토큰·음성 원문을 응답에 포함하지 않는가?
- [ ] 신규 로그 라인이 이메일/전화/토큰을 그대로 쓰지 않고 Masker를 통과하는가?
- [ ] 보호 리소스 접근 지점에서 백엔드 권한 검증을 수행하는가?
- [ ] JWT secret·API 키가 코드가 아닌 환경변수로 주입되는가?
- [ ] 로그인 실패 응답이 사용자 존재 유무를 유출하지 않는가?
- [ ] 음성 파일을 proxy 또는 presigned URL로만 노출하는가?

## 11. 관련 스킬·문서

- [error-handling](../error-handling/SKILL.md) — ApiException 로그 레벨 (4xx는 code+path만, 5xx만 cause 포함)
- [spring-layering](../spring-layering/SKILL.md) — Response DTO 변환 지점 (민감 필드 애초에 제외)
- [tdd-service](../tdd-service/SKILL.md) — 보안 테스트 (인증 필요/공개 엔드포인트 분리)
- TRD §6 (권한), §11 (보안), §13 (저장소 추상화)
