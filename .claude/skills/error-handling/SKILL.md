---
name: error-handling
description: flowmeet-api의 에러 처리 체계 — ApiException + ErrorCode + 전역 핸들러로만 응답을 만든다. 신규 예외/에러 코드 정의, 응답 형식, 로깅 레벨, 민감정보 마스킹 규칙. 예외를 던지거나 Controller에서 상태 코드를 다룰 때 로드.
---

# Error Handling — flowmeet-api

**모든 비즈니스 예외는 [`ApiException`](../../../src/main/kotlin/com/flowmeet/common/exception/ApiException.kt)으로만 던진다.** 그 외 모든 예외는 [`GlobalExceptionHandler`](../../../src/main/kotlin/com/flowmeet/common/exception/GlobalExceptionHandler.kt)가 500으로 변환하고 원인은 로그에만 남긴다.

## 1. 구성 요소

| 파일 | 책임 |
| --- | --- |
| `ErrorCode` | 비즈니스 에러 식별자 enum. `code` + `httpStatus` + `defaultMessage` 보유 |
| `ApiException(errorCode, overrideMessage?, cause?)` | 모든 비즈니스 예외의 베이스 |
| `ErrorResponse` | 응답 바디 — `errorCode`·`message`·`path`·`timestamp`·`fieldErrors?` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 모든 예외의 응답 생성 지점 |

## 2. 신규 ErrorCode 추가 규칙

- 코드 포맷: **`<BC>-<번호>`** (예: `MEETING-404`, `AUTH-401`).
  - `<BC>` = 바운디드 컨텍스트. 공통은 `COMMON`.
  - `<번호>` = HTTP 상태 코드와 일치시키되, 같은 BC·상태의 다른 코드가 필요하면 `-001`, `-002` 식으로 연번.
- 추가 위치: `common/exception/ErrorCode.kt`에 섹션 주석(`// 회의 (MEETING-xxx)`) 아래 enum 상수로.
- **`code` 값은 변경 금지** — 클라이언트가 분기 키로 쓴다. 메시지는 바꿔도 OK.
- `defaultMessage`는 사용자 노출용. **민감 정보 포함 금지** (TRD §11): 비밀번호 해시, 토큰, 음성 원문, 회의 세부 내용.

## 3. 예외 던지는 패턴

```kotlin
// 기본: ErrorCode의 defaultMessage 사용
throw ApiException(ErrorCode.MEETING_NOT_FOUND)

// 메시지 교체 (민감정보 금지)
throw ApiException(ErrorCode.MEETING_ALREADY_COMPLETED, overrideMessage = "이미 종료된 회의입니다. (id=$id)")

// 원인 예외 전파 — 로그에만 노출됨
throw ApiException(ErrorCode.INTERNAL_ERROR, cause = e)
```

- Service 레이어에서 던진다. Controller는 던지지 않는다(검증 실패는 Bean Validation으로).
- `ApiException`을 상속한 전용 예외(`MeetingNotFoundException` 등)를 BC별로 두는 것은 **선택** — 간단한 경우 그냥 `ApiException(code)`로 충분하다.

## 4. 응답 형식 (사용자 분기 기준)

```json
{
  "errorCode": "MEETING-404",
  "message": "회의를 찾을 수 없습니다.",
  "path": "/api/meetings/42",
  "timestamp": "2026-10-14T07:00:00Z",
  "fieldErrors": null
}
```

- Bean Validation 실패는 `fieldErrors` 배열에 각 필드 reason을 담아 반환.
- 그 외는 `fieldErrors`가 `null`.

## 5. 로깅 레벨 가이드

| 분류 | 레벨 | 포함 정보 |
| --- | --- | --- |
| `ApiException` (4xx) | `WARN` | `errorCode.code`, `path`, `userMessage`, stack trace |
| `ApiException` (5xx) | `ERROR` | 위 + 원인 예외 |
| `MethodArgumentNotValidException` | `INFO` 이하 (로그 생략 가능) | 사용자 입력 실수 — 로그 노이즈 금지 |
| 처리되지 않은 예외 | `ERROR` | `path`, 원인 예외 전체 |

- **원인 메시지(`cause.message`)는 응답에 노출 금지** — 로그에만. (TRD §11)
- JWT 토큰·비밀번호·음성 파일 경로 등은 로그에서도 마스킹한다 (별도 로깅 규칙 #8 참조).

## 6. 금지 사항

- ❌ Controller에서 `try/catch` 후 `ResponseEntity.status(...)` 생성 — 전역 핸들러로 위임.
- ❌ `ApiException` 없이 `RuntimeException` 직접 던지기 — 500으로 변환되지만 식별이 어렵다.
- ❌ `ErrorCode` 바깥에서 HTTP 상태를 하드코딩.
- ❌ `defaultMessage`나 `overrideMessage`에 토큰·회의 원문·비밀번호 등 민감 데이터 포함.
- ❌ 예외 바디에 `cause.message`나 스택 트레이스 노출.

## 7. 테스트 패턴

Slice test 선호. 전역 핸들러만 검증할 때는 `MockMvcBuilders.standaloneSetup`:

```kotlin
val mockMvc = MockMvcBuilders.standaloneSetup(TargetController())
    .setControllerAdvice(GlobalExceptionHandler())
    .setMessageConverters(mappingJackson2WithJavaTimeModule())
    .build()
```

- 예시: [`src/test/kotlin/com/flowmeet/common/exception/GlobalExceptionHandlerTest.kt`](../../../src/test/kotlin/com/flowmeet/common/exception/GlobalExceptionHandlerTest.kt).
- 검증 포인트: 상태 코드, `errorCode`, `message`, `fieldErrors`, **민감 원인 메시지가 응답에 유출되지 않는지**.

## 8. 리뷰 체크리스트

- [ ] 새 비즈니스 예외가 `ApiException`을 통해 던져지는가?
- [ ] 새 `ErrorCode`의 `code` 포맷이 `<BC>-<번호>` 규약을 따르는가?
- [ ] `defaultMessage`·`overrideMessage`에 민감 정보가 섞여 있지 않은가?
- [ ] Controller가 `try/catch` 로 예외를 삼키거나 상태 코드를 하드코딩하지 않는가?
- [ ] 테스트에서 응답 바디의 `errorCode`·`message`·`fieldErrors`를 검증하는가?
- [ ] 원인 메시지(`cause.message`)가 응답에 노출되지 않는지 테스트에서 확인하는가?

## 9. 관련

- 상위 규칙: [kotlin-style](../kotlin-style/SKILL.md), [spring-layering](../spring-layering/SKILL.md)
- 로깅 상세 규칙: #8 보안·로깅 (별도 이슈)
- TRD §11 — 민감 정보 처리
