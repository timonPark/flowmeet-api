---
name: spring-layering
description: flowmeet-api의 Spring Boot 레이어링 규칙 (Controller/Service/Repository/DTO/Entity). Spring Bean을 쓰거나 신규 API를 설계할 때 로드.
---

# Spring Layering — flowmeet-api

패키지 구조·네이밍·data class 선택 등 **구조 규칙은 [kotlin-style](../kotlin-style/SKILL.md)이 상위**다. 두 문서가 상충하면 kotlin-style이 우선. 이 스킬은 그 위에서 **각 레이어의 책임·금지사항·변환 지점**을 성문화한다.

## 1. 레이어 책임 요약

| 레이어 | 디렉터리 | 책임 | 금지 |
| --- | --- | --- | --- |
| **API** | `<bc>/api/` | HTTP 바인딩, 요청 검증(@Valid), Command/Query 매핑, Response 변환 | 비즈니스 로직, 트랜잭션, Entity 직접 반환 |
| **Application** | `<bc>/application/` | 트랜잭션 경계(@Transactional), 유스케이스 조정, 외부 포트 호출 분기 | HTTP 지식, JPA EntityManager 직접 사용 |
| **Domain** | `<bc>/domain/` | 엔티티, VO, 도메인 서비스, 포트(인터페이스) | 외부 레이어·프레임워크 참조 (JPA 매핑 어노테이션은 §4 표의 허용 범위에 한함) |
| **Infrastructure** | `<bc>/infrastructure/` | JPA Repository 구현, 외부 API 어댑터, 메시징 | 유스케이스 로직, HTTP 바인딩 |

**의존 방향**: `api → application → domain ← infrastructure`. domain은 **상위 레이어·프레임워크를 참조하지 않는 순수 모듈**로 유지한다 (스스로 다른 레이어를 import하지 않는다는 뜻 — infrastructure가 domain의 포트를 구현하거나 application이 domain 엔티티를 사용하는 것은 OK).

## 2. Controller 규칙

- 반환 타입은 반드시 **Response DTO** 또는 `ResponseEntity<ResponseDto>`. **Entity 반환 금지**.
- 요청 바디는 **Request DTO** (`CreateXxxRequest`, `UpdateXxxRequest`) + `@Valid`.
- URL 쿼리/경로는 primitive 또는 전용 **Query DTO** (`XxxSearchQuery`).
- Controller 메서드 1개당 Service 호출은 보통 1개. 2개 이상 조립이 필요하면 Application 레이어에 Facade/UseCase 신설.
- 예외는 던지기만 — 전역 처리는 `@RestControllerAdvice` (별도 이슈 #5).

```kotlin
@RestController
@RequestMapping("/api/meetings")
class MeetingController(
    private val createMeeting: CreateMeetingService,
    private val findMeeting: FindMeetingService,
) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateMeetingRequest): ResponseEntity<MeetingResponse> {
        val meeting = createMeeting(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(MeetingResponse.from(meeting))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): MeetingResponse =
        MeetingResponse.from(findMeeting(id))
}
```

## 3. Application / Service 규칙

- **트랜잭션 경계는 여기서만** (`@Transactional` / `@Transactional(readOnly = true)`).
- 쓰기 유스케이스는 **Command 객체**를 받고, 조회 유스케이스는 **Query 객체** 또는 primitive 식별자를 받는다.
- **반환 값 규칙 (LAZY 안전)**:
  - **조회 유스케이스**는 반드시 **조회 전용 Projection**(domain의 VO/Record) 반환. Entity 반환 금지 — Controller에서 LAZY 접근 시 `LazyInitializationException` 유발.
  - **쓰기 유스케이스**는 도메인 Entity 반환 가능. 단 Controller 변환 시 **LAZY 연관관계에 절대 접근하지 말 것** — Response에는 식별자 + 쓰기 결과(생성/갱신된 필드)만 포함.
  - 쓰기 결과에서 연관관계까지 노출해야 한다면 Service가 해당 그래프를 fetch한 Projection을 반환하도록 설계.
- 외부 포트는 생성자 주입. 구현체(어댑터) 선택은 Spring config이 담당.
- 유스케이스 하나당 서비스 클래스 하나(`CreateMeetingService`) 선호 — God Service 금지.

```kotlin
@Service
class CreateMeetingService(
    private val meetings: MeetingRepository,          // domain port
    private val notifyAssignees: NotifyAssignees,     // domain port
) {
    @Transactional
    operator fun invoke(command: CreateMeetingCommand): Meeting {
        val meeting = Meeting.schedule(
            title = command.title,
            scheduledAt = command.scheduledAt,
            organizerId = command.organizerId,
        )
        meetings.save(meeting)
        notifyAssignees(meeting.id, command.assigneeIds)
        return meeting
    }
}
```

## 4. Domain 규칙

- 포트는 인터페이스로 선언 — 구현은 infrastructure.
- 상태 변경은 반드시 **엔티티 메서드**로 (`meeting.cancel(reason)`) — 외부에서 setter 호출 금지.
- 불변 식별자(VO)·금액·기간 같은 값은 `data class` (JPA Entity는 제외 — 상위 규칙 [kotlin-style §4](../kotlin-style/SKILL.md#4-data-class-vs-class) 참조).

### 4.1 Domain에서의 어노테이션 허용 범위

| 분류 | 허용 | 금지 |
| --- | --- | --- |
| JPA 매핑 | `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@Enumerated`, `@Embeddable`, `@Embedded`, `@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`, `@JoinColumn`, `@MappedSuperclass`, `@Version` | — |
| Spring 스테레오타입 | — | `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration` |
| Spring 기능 | — | `@Autowired`, `@Transactional`, `@Value`, `@EventListener` |
| 직렬화·검증 | — | Jackson(`@JsonProperty` 등), Bean Validation(`@NotNull` 등 — DTO에만) |

→ 매핑 외 Spring/직렬화 어노테이션은 **api 또는 infrastructure 레이어**로 격리한다.

## 5. Infrastructure 규칙

- JPA Repository는 **domain 포트를 구현**하거나 Spring Data `JpaRepository` + 별도 어댑터로 포트를 만족시킨다.
  - 가장 간단한 패턴: `interface SpringMeetingJpaRepository : JpaRepository<Meeting, Long>` + `class JpaMeetingRepository(private val jpa: SpringMeetingJpaRepository) : MeetingRepository`.
- 동적 쿼리는 QueryDSL 또는 Specification. **`@Query` 네이티브 SQL은 최후수단** (이유를 주석에 명시).
- 외부 API 어댑터는 포트 인터페이스를 구현. 재시도·타임아웃·회로차단은 어댑터 내부에 격리.

## 6. DTO ↔ Entity 변환 규칙

- 변환 책임 위치:
  - Request DTO → Command: **Request DTO의 확장/멤버**(`toCommand()`).
  - Projection / Entity → Response DTO: **Response DTO의 companion**(`MeetingResponse.from(projection)`).
- 변환 과정에서 노출하면 안 되는 필드(비밀번호 해시, 내부 ID, 로우 토큰 등)는 애초에 Response에 넣지 않는다.
- **LAZY 접근 금지선**: Controller 변환 시점에는 영속성 컨텍스트가 닫힌 상태다. §3 규칙에 따라 조회는 Projection, 쓰기는 LAZY 없는 필드만 — Controller가 LAZY 프록시를 터치하는 순간 `LazyInitializationException`.
- 중첩 엔티티는 평탄화해 노출 — Service가 Projection으로 미리 평탄화해서 반환한다.

## 7. 패키지 예시 — `auth` BC

```text
com.flowmeet.auth
├─ api
│  ├─ AuthController.kt
│  ├─ request/LoginRequest.kt, SignupRequest.kt
│  └─ response/LoginResponse.kt, UserResponse.kt
├─ application
│  ├─ LoginService.kt            # @Transactional, 조회성이면 readOnly
│  ├─ SignupService.kt
│  └─ command/LoginCommand.kt, SignupCommand.kt
├─ domain
│  ├─ User.kt                    # @Entity, 상태 변경 메서드 포함
│  ├─ UserRepository.kt          # port
│  ├─ HashPassword.kt            # port (알고리즘 교체 가능)
│  └─ exception/InvalidCredentialsException.kt
└─ infrastructure
   ├─ persistence/
   │  ├─ SpringUserJpaRepository.kt    # Spring Data interface
   │  └─ JpaUserRepository.kt          # UserRepository 구현
   └─ security/
      └─ BcryptHashPassword.kt         # HashPassword 구현
```

### 레이어 간 흐름 (로그인)

```
POST /api/auth/login
  → AuthController.login(LoginRequest)
    → LoginService(LoginCommand)
       ├─ UserRepository.findByEmail()        [port → JpaUserRepository]
       ├─ HashPassword.verify()               [port → BcryptHashPassword]
       └─ IssueToken.issue(user)              [port → JJwtIssueToken]
    ← User + Token
  ← LoginResponse.from(user, token)
```

## 8. 금지 사항 (요약)

- Controller에서 `@Transactional` 선언.
- Service에서 `HttpServletRequest`/`ResponseEntity` 참조.
- Entity 또는 Entity 리스트를 Controller가 그대로 반환.
- Repository 인터페이스를 Controller가 직접 주입받아 사용.
- "Service가 다른 BC의 Repository를 직접 호출" → 포트/도메인 서비스로 추상화.
- 같은 BC 내 service가 다른 service를 호출하는 체인 3단 이상 — Facade로 평탄화.

## 9. 리뷰 체크리스트

- [ ] Controller가 Entity를 반환하지 않는가? Response DTO만 쓰는가?
- [ ] 트랜잭션이 Application 레이어에만 선언돼 있는가?
- [ ] 도메인 포트가 `domain/`에, 구현이 `infrastructure/`에 있는가?
- [ ] 유스케이스 하나당 Service 하나 원칙을 따르는가 (God Service 아닌가)?
- [ ] Request/Response DTO가 입력·출력용으로 분리돼 있는가 (한 DTO 양방향 재사용 아닌가)?
- [ ] JPA 지연 로딩 필드가 Response에 섞여 들어가지 않는가?

## 10. 관련 스킬

- 네이밍·패키지 상위 규칙: [kotlin-style](../kotlin-style/SKILL.md)
- 에러 처리(ApiException, @RestControllerAdvice): 이슈 #5 (별도)
- JPA 쿼리 가드(N+1, EXPLAIN): 이슈 #6 (별도)
- 테스트 레이어링(slice test): 이슈 #7 (별도)
