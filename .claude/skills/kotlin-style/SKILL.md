---
name: kotlin-style
description: flowmeet-api의 Kotlin 코드 스타일·네이밍·패키징 규칙. Kotlin 파일을 쓰거나 리뷰할 때 로드.
---

# Kotlin Style — flowmeet-api

Kotlin 공식 코딩 컨벤션 기반, flowmeet 특화 규칙. **ktlint가 자동 강제**하므로 포맷 관련 규칙은 생략하고, 사람 판단이 필요한 영역만 명시한다.

> 포맷/들여쓰기/임포트 정렬 등은 `./gradlew ktlintFormat` 으로 해결. 아래 규칙은 **ktlint가 못 잡는 설계·네이밍·구조** 지침.

## 1. 패키지 구조 (BC 기반)

```text
com.flowmeet
├─ <bounded-context>
│  ├─ api            # Controller, Request/Response DTO
│  ├─ application    # UseCase/Service, Command/Query
│  ├─ domain         # Entity, VO, Domain Service, Port(인터페이스)
│  └─ infrastructure # Repository 구현, 외부 API 어댑터
├─ common            # 공통 유틸, ErrorCode, ApiException
└─ config            # Spring 설정
```

- bounded context 예: `meeting`, `task`, `user`, `auth`, `location`, `dashboard`.
- 외부 API 어댑터(Deepgram, Claude)는 `infrastructure/ai/` 하위에 배치. 인터페이스는 `domain/` 쪽에 둔다 (의존 역전).
- `web`, `service`, `repository` 같은 **레이어명이 BC보다 상위**인 구조 금지. (BC-first)

## 2. 네이밍

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 패키지 | 소문자, 단수형 | `meeting`, `task` |
| 클래스/인터페이스 | PascalCase | `MeetingService`, `TaskRepository` |
| 함수/프로퍼티 | camelCase | `findActiveMeetings()` |
| 상수 (`const val`, `companion` 상수) | SCREAMING_SNAKE | `MAX_RETRY_COUNT` |
| 테스트 함수 | 백틱 + 한국어 OK | `` `이미 종료된 회의는 재시작할 수 없다`() `` |
| DTO | 명시적 접미사 | `CreateMeetingRequest`, `MeetingResponse` |
| 인터페이스 | `I` 접두사 금지. 구현체에 `Impl` 접미사 금지 | `MeetingRepository` + `JpaMeetingRepository` |
| 포트/어댑터 | 포트는 동사 중심, 어댑터는 기술 접두사 | `TranscribeAudio` + `DeepgramTranscribeAudio` |

## 3. Null 처리 (경계 원칙)

- **입력 경계**(Request DTO, 외부 API 응답, DB 조회 결과)에서만 nullable 허용.
- **도메인 내부**는 non-null — 입력 경계에서 즉시 검증·실패하거나 기본값으로 변환.
- `!!` (not-null assertion) 사용 금지. 꼭 필요하면 `requireNotNull(x) { "<이유>" }`로 교체.
- `?.let { }` 체이닝이 3단 이상이면 함수 분리.

## 4. data class vs class

| 상황 | 선택 |
| --- | --- |
| JPA 엔티티 (`@Entity`) | **일반 class** (final equals/hashCode·copy 충돌 방지) |
| Request/Response DTO | `data class` |
| 도메인 Value Object | `data class` (불변성 명시) |
| Command/Query 객체 | `data class` |
| Spring Bean (Service, Component) | 일반 class (주로 생성자 주입만) |

## 5. 함수 길이·복잡도

- 함수 상한 **40줄** 권장 (ktlint가 강제하지는 않음 — 코드리뷰 체크포인트).
- 함수 하나가 두 책임을 가지면 쪼갠다 ("~하고 ~하는" 네이밍 금지).
- 중첩 깊이 3단 이상 지양 — guard clause / early return 선호.

## 6. 코틀린적 선호

- 가변 컬렉션 반환 금지 — public API는 `List`, `Set`, `Map` (불변) 반환.
- `?.also`/`?.apply`는 사이드 이펙트가 명확할 때만. 변환은 `.let`/`.map`.
- 스코프 함수 체이닝 3단 이상이면 지역 변수로 쪼갠다.
- 확장 함수는 BC 내부(`domain/extensions.kt`)에 모은다. 전역 확장 금지.

## 7. 금지 사항

- `println`, `System.out` → `KotlinLogging` 또는 SLF4J 사용 (추후 로깅 규칙 #8에서 상세화).
- `Thread.sleep` (프로덕션 코드) — 비동기는 `CompletableFuture`/코루틴.
- `@Autowired` 필드 주입 — 반드시 생성자 주입.
- JPA 엔티티에서 `@Setter` 전역 적용 금지 — 상태 변경은 도메인 메서드.
- 동일 레이어 간 순환 참조 — 있으면 상위 추상으로 끌어올린다.

## 8. 로컬 명령

```bash
# 포맷 자동 수정
./gradlew ktlintFormat

# 검사만 (CI와 동일)
./gradlew ktlintCheck

# check 태스크에 ktlintCheck가 포함돼 있어 build 시 자동 실행
./gradlew build
```

## 9. 도구 설정 위치

- `build.gradle.kts` — `org.jlleitschuh.gradle.ktlint` 플러그인 블록
- `.editorconfig` — ktlint 룰 커스터마이즈 (indent, line length, trailing comma)
- 예외 억제가 꼭 필요하면 `@Suppress("ktlint:<rule-id>")` + 코멘트에 **왜** 필요한지 명시.

## 10. 리뷰 체크리스트

- [ ] 패키지 경로가 BC-first 구조에 맞는가?
- [ ] nullable이 경계가 아닌 도메인 내부로 새어 들어왔는가?
- [ ] JPA 엔티티가 `data class`로 선언돼 있지 않은가?
- [ ] `!!`, `println`, 필드 주입(`@Autowired`) 없는가?
- [ ] 함수 하나가 두 책임을 지지 않는가 (네이밍에 "and"가 있나)?
- [ ] 신규 확장 함수가 적절한 위치(BC 내부)에 있는가?
