---
pr: 17
branch: chore/4-spring-layering
base: main
reviewer: codex-cli (codex-cli 0.162.0-alpha.17.2)
generated_at: 2026-10-13T23:09:05Z
---

# Code Review — PR #17 chore/4-spring-layering

## 요약

Spring Boot 레이어별 책임, 의존 방향, DTO 변환 및 패키지 구조를 문서화하고 `CLAUDE.md`에서 참조하도록 한 변경이다. 실행 코드 변경은 없어 런타임 회귀 위험은 낮다. 다만 JPA 지연 로딩과 Controller 변환 시점의 트랜잭션 경계가 충돌할 수 있으며, domain 의존 방향 설명도 일부 모순된다. 기존 `kotlin-style` 규칙과의 중복은 제한적이지만, 상충 가능성을 줄이기 위한 우선순위 표현이 보강되면 좋다.

## 강점

- BC-first 패키지 구조를 상위 규칙으로 명시하고, 레이어별 책임과 금지사항을 구체적인 예시와 함께 정리했다.
- Controller, Application, Domain, Infrastructure 사이의 의존 방향과 포트-어댑터 패턴을 명확히 설명했다.
- Entity를 API 응답으로 직접 노출하지 않고 Request/Command 및 Entity/Response 변환 지점을 분리한 점이 좋다.
- 트랜잭션 경계, 외부 API 어댑터의 재시도·타임아웃 격리, N+1 및 지연 로딩 관련 체크리스트를 포함했다.
- `CLAUDE.md`에 포인터를 추가해 새 규칙의 발견 가능성을 높였다.

## 지적사항

- [severity: high] Controller의 Response 변환과 트랜잭션 경계가 충돌할 수 있음: `.claude/skills/spring-layering/SKILL.md` 3·6절 및 예시 `CreateMeetingService`/`MeetingResponse.from(entity)`. Application 서비스에서 트랜잭션이 종료된 뒤 Controller가 Entity의 LAZY 연관관계를 변환하면 `LazyInitializationException`이 발생할 수 있다. 응답 변환을 트랜잭션 내부에서 수행하거나, 필요한 연관관계를 명시적으로 fetch한 조회 모델/Projection을 반환하는 규칙이 필요하다.

- [severity: medium] Domain 의존 방향 설명이 자기모순임: 1절에서 `api → application → domain ← infrastructure`라고 정의한 뒤 “domain은 아무도 참조하지 않는 순수 모듈”이라고 설명한다. Application과 Infrastructure가 domain을 참조하는 구조이므로 “domain은 상위 레이어나 프레임워크를 참조하지 않는다”처럼 주체를 명확히 표현해야 한다.

- [severity: medium] `kotlin-style`과의 규칙 우선순위가 충분히 명시되지 않음: 문서 전반에서 패키지 구조는 상위 규칙을 따른다고 했지만, `CLAUDE.md`에도 BC-first 구조와 Entity 규칙을 다시 선언하고 있다. 향후 두 문서의 세부 규칙이 달라질 경우 어느 문서가 우선인지 명시하거나, `spring-layering`에서는 레이어링에만 집중해 중복 선언을 줄이는 편이 안전하다.

- [severity: medium] “Domain은 Spring/JPA 의존 금지”와 JPA Entity 예시의 경계가 모호함: 1절에서는 Domain의 JPA 의존을 금지하면서 4절에서는 JPA 어노테이션을 허용한다. `@Entity`, `@Id`, 관계 매핑 등 허용 범위와 `@Repository`, `@Transactional`, Spring Data 타입 등 금지 범위를 표로 명확히 분리하는 것이 좋다.

- [severity: low] “불변 식별자(VO)·금액·기간 같은 값은 data class” 규칙의 적용 범위가 불명확함: JPA Entity에는 `data class`를 지양한다고 `CLAUDE.md`에 명시되어 있으므로, 해당 문장이 Value Object에만 적용된다는 점을 문서에 직접 적으면 오해를 줄일 수 있다.

- [severity: low] “같은 BC 내 service가 다른 service를 호출하는 체인 3단 이상”의 판단 기준이 모호함: 호출 깊이를 정적으로 어떻게 판단하는지, 공통 정책 조합이나 재사용 가능한 유스케이스 호출까지 금지하는지 기준을 보완할 필요가 있다.

## 질문·제안

- 지연 로딩 문제를 방지하기 위해 Response DTO 변환을 Application 서비스 내부에서 수행하는 방식을 허용할지, 아니면 조회 전용 Projection을 표준으로 삼을지 결정할 것을 제안한다.
- `domain`은 “아무도 참조하지 않음”이 아니라 “외부 레이어와 프레임워크를 의존하지 않음”으로 문구를 수정할 것을 제안한다.
- `kotlin-style`과 `spring-layering` 간 충돌 시 우선순위를 `CLAUDE.md`에 명시할 것을 제안한다.
- 별도 이슈로 둔 전역 예외 처리, N+1 가드, slice test 규칙이 추가될 때 본 문서와 연결되는 기준점을 유지할지 확인이 필요하다.

## 체크리스트

- [ ] LAZY 연관관계가 있는 Entity를 Controller에서 변환하지 않도록 변환 시점 또는 조회 Projection 규칙 확정
- [ ] Domain 의존 방향 문구 수정
- [ ] `kotlin-style`과 `spring-layering`의 중복·우선순위 정리
- [ ] Domain에서 허용되는 JPA 어노테이션 범위 명시
- [ ] 유스케이스 간 호출 깊이 규칙의 적용 기준 구체화
