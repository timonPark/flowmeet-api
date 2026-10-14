---
name: tdd-service
description: flowmeet-api의 테스트 전략. JUnit 5 + MockK + Testcontainers. 레이어별 테스트 유형(단위/슬라이스/통합) 선택 기준, Red→Green→Refactor 사이클. 신규 기능 테스트를 쓰거나 테스트 설계 결정이 필요할 때 로드.
---

# TDD / Test Rules — flowmeet-api

**실패 테스트 먼저 → 최소 구현 → 리팩터**의 사이클을 Kotlin/Spring 환경에서 유지한다. 레이어별로 다른 테스트 유형을 쓰고, Spring 컨텍스트 로드 비용은 최소화한다.

## 1. 테스트 스택

| 도구 | 용도 | 비고 |
| --- | --- | --- |
| **JUnit 5** | 테스트 러너 | 백틱 한국어 테스트 이름 OK — [kotlin-style §2](../kotlin-style/SKILL.md) |
| **MockK** (`io.mockk:mockk`) | Kotlin 친화 모킹 | `every { }`, `verify { }` DSL |
| **AssertJ** | 플루언트 어서션 | Spring Boot Starter Test에 포함 |
| **Testcontainers** (`org.testcontainers:junit-jupiter`, `:postgresql`) | 통합·Repository 테스트용 PostgreSQL 16 컨테이너 | 로컬 docker 필요 |
| **MockMvc** | Controller slice test | `@WebMvcTest` 와 조합 |

> `./gradlew test` 로 전부 실행. `./gradlew build` 는 ktlintCheck + test 모두 포함.

## 2. 레이어별 테스트 전략

| 레이어 | 선호 유형 | 예시 |
| --- | --- | --- |
| **Service / Domain** | 순수 단위 테스트 (Spring 컨텍스트 X) | [PingServiceTest](../../../src/test/kotlin/com/flowmeet/common/ping/PingServiceTest.kt) |
| **Controller** | `@WebMvcTest` + MockMvc + `@TestConfiguration`으로 Service bean 교체 | [PingControllerTest](../../../src/test/kotlin/com/flowmeet/common/ping/PingControllerTest.kt) |
| **전역 핸들러** | `MockMvcBuilders.standaloneSetup` (Spring 컨텍스트 X) | [GlobalExceptionHandlerTest](../../../src/test/kotlin/com/flowmeet/common/exception/GlobalExceptionHandlerTest.kt) |
| **Repository** | `@DataJpaTest` + Testcontainers (실제 PostgreSQL) | [RoleJpaRepositoryTest](../../../src/test/kotlin/com/flowmeet/auth/infrastructure/persistence/RoleJpaRepositoryTest.kt) |
| **통합 (E2E 흐름)** | `@SpringBootTest` + Testcontainers — **최소화** | 신규 BC 통합 시만 |

- `@SpringBootTest`는 비싸다 — Controller/Service 유닛으로 충분한 경우 쓰지 말 것.
- H2 금지 — 운영 DB가 PostgreSQL이므로 **Testcontainers로 동일 엔진** 사용.

## 3. Red → Green → Refactor 사이클

1. **Red**: 요구사항을 가장 좁은 테스트 1개로 표현한다. 실패하는 것을 확인.
2. **Green**: 테스트를 통과시키는 **최소한의 구현**. 중복은 다음 단계에서 정리.
3. **Refactor**: 중복 제거·네이밍 개선·확장 지점 정리. 테스트는 그대로 통과해야 함.

- 커밋 단위: Red+Green은 하나로 묶어도 OK. Refactor는 분리 커밋 선호.
- 한 사이클당 테스트 1개가 원칙 — 여러 요구를 한 번에 끌어오지 않는다.

## 4. MockK 사용 기준

```kotlin
// 단위 테스트: 생성자에 Mock 주입
val clock = mockk<Clock>()
every { clock.instant() } returns Instant.parse("2026-10-14T07:00:00Z")

val service = PingService(clock)
val result = service()

verify(exactly = 1) { clock.instant() }
```

- **생성자 주입된 포트/의존성만 모킹**. Entity·VO·값 객체는 실제 인스턴스를 쓴다.
- `relaxed = true` 는 **금지** — 호출되지 않아야 할 메서드가 조용히 `null`을 반환해 테스트가 거짓양성이 된다. 필요하면 `every { ... }` 로 명시.
- `verify { ... }` 는 **상호작용이 핵심인 경우만**. 상태 어서션으로 충분하면 verify 생략.

### Spring Security 필터 처리 (현재/미래)

- **현재(Security 설정 없음)**: Controller slice test에서 `@AutoConfigureMockMvc(addFilters = false)` 로 filter chain 비활성화 — 기본 자동 설정의 불특정 동작을 피한다.
- **Security 설정 도입 후(#8 이후)**:
  - **공개 엔드포인트**는 `addFilters = true` 유지하고 인증 없는 상태에서 200 응답을 검증.
  - **보호 엔드포인트**는 `@WithMockUser(roles = "...")` 또는 JWT 테스트 토큰으로 접근, 미인증 상태의 401/403도 함께 검증.
  - 두 경로를 분리한 테스트를 Controller slice에 둔다 — 인증 계약 회귀를 감지.

### Spring 테스트에서의 Bean 교체

`@WebMvcTest`에서 Service bean을 교체할 때는 `@TestConfiguration` + `@Bean` + `mockk()` 패턴을 쓴다.

```kotlin
@WebMvcTest(controllers = [PingController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(PingControllerTest.MockBeansConfig::class)
class PingControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var pingService: PingService  // mockk 인스턴스 주입됨

    @Test fun `...`() { every { pingService.invoke() } returns ... }

    @TestConfiguration
    class MockBeansConfig {
        @Bean fun pingService(): PingService = mockk()
    }
}
```

## 5. Testcontainers 사용 기준

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class XxxRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("flowmeet")
                .withUsername("flowmeet")
                .withPassword("local-password")
                .withInitScript("schema.sql")  // database/ 가 test resources 로 포함돼 있음

        @DynamicPropertySource
        @JvmStatic
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }
    }
}
```

- `withInitScript("schema.sql")`: `database/schema.sql` 이 test classpath로 노출돼 있음 (build.gradle.kts `sourceSets.test.resources.srcDir("database")`).
- `AutoConfigureTestDatabase.Replace.NONE` 로 Spring의 내장 DB 자동 교체 비활성 — Testcontainers Postgres를 쓴다.
- 컨테이너는 테스트 클래스당 1개 공유 (`companion object` + `@JvmStatic`). 테스트 간 데이터 격리는 트랜잭션 롤백(@DataJpaTest 기본값)으로.

## 6. 금지 사항

- ❌ H2 또는 임베디드 DB로 Repository 테스트 — 운영 PostgreSQL과 쿼리 호환성 차이.
- ❌ `@SpringBootTest`를 Controller/Service 단위 테스트에 사용 — 로드 비용 ↑, 피드백 느려짐.
- ❌ `mockk(relaxed = true)` 기본값으로 사용 — 거짓양성 유발.
- ❌ 테스트 내부에서 `Thread.sleep` — 비동기 대기는 `Awaitility` 또는 조건 폴링.
- ❌ 테스트가 실행 순서에 의존 — 각 테스트는 독립.
- ❌ `@SpringBootTest(webEnvironment = RANDOM_PORT)` + 전체 DB 접근을 모든 BC에 적용 — 한두 개의 핵심 통합 시나리오로 제한.

## 7. 커버리지

- **현재 JaCoCo 미도입**. 실제 운영 경험으로 "커버리지 숫자 자체가 품질 보증 아님"을 인정하되, Phase 2 이후 수치 추세 보기용으로 도입 재검토.
- 지금은 **각 PR이 샘플 레벨의 테스트를 추가하는지 리뷰어가 확인**하는 방식.

## 8. 리뷰 체크리스트

- [ ] 테스트 이름이 "~한다" 식으로 **행위를 서술**하는가 (파일명·메서드명이 아니라)?
- [ ] 레이어 선택이 적절한가 (Service=단위 / Controller=@WebMvcTest / Repository=@DataJpaTest+Testcontainers)?
- [ ] MockK `relaxed = true` 사용 없는가?
- [ ] 테스트가 서로 독립이고 실행 순서에 의존하지 않는가?
- [ ] Testcontainers 테스트가 `companion object` + `@JvmStatic` 로 컨테이너를 공유하는가?
- [ ] Red→Green→Refactor 사이클을 지켰는가 (한 사이클 = 한 테스트)?

## 9. 관련 스킬·문서

- [kotlin-style](../kotlin-style/SKILL.md) — 테스트 네이밍, 백틱 한국어
- [spring-layering](../spring-layering/SKILL.md) — 레이어 책임
- [error-handling](../error-handling/SKILL.md) — standaloneSetup 패턴
- [jpa-query-guard](../jpa-query-guard/SKILL.md) — EXPLAIN·N+1 체크
