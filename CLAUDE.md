# CLAUDE.md

조직 지시 및 업무 흐름 관리 플랫폼 API 리포 (`flowmeet-api`). 웹 프론트엔드는 별도 리포 (`../flowmeet-web`).

## 작업 시작 규칙 (필수)

**어떤 작업이든 반드시 전용 브랜치를 만들어 진행한다. `main`에 직접 커밋 금지.**

- 작업 착수 시 가장 먼저 `main`에서 새 브랜치를 분기한다.
- 브랜치 명명 규약: `<type>/<issue-number>-<slug>`
  - `type`: `feature` / `fix` / `refactor` / `chore` / `docs` / `test`
  - 예: `feature/2-harness-skeleton`, `fix/15-login-error`, `docs/8-security-rules`
- 이슈 번호가 없는 작업은 `<type>/<slug>` (예: `chore/update-readme`).
- 작업 완료 후 PR을 생성해 `main`으로 머지 — 직접 푸시 금지.

```bash
# 올바른 착수 흐름
git checkout main && git pull
git checkout -b feature/<issue>-<slug>
# ... 작업 ...
git push -u origin feature/<issue>-<slug>
gh pr create --fill
```

## 기술 스택

- Kotlin 1.9 + Spring Boot 3.4 + Gradle Kotlin DSL
- Java 21 toolchain
- Spring Data JPA, Spring Security (JWT 기반), Validation, Actuator
- PostgreSQL 16 (docker-compose로 로컬 기동)
- JJWT 0.12 (jsonwebtoken)
- 외부 연동: Deepgram STT, Claude Headless

## 디렉터리 구조

```text
flowmeet-api/
├─ src/main/kotlin/com/flowmeet/   # 애플리케이션 코드 (base package)
├─ src/main/resources/application.yml
├─ src/test/kotlin/com/flowmeet/
├─ database/schema.sql             # outputs/schema.sql과 동기화된 DDL
├─ docker-compose.yml              # Postgres 16 (schema.sql 자동 적용)
├─ uploads/                        # 로컬 파일 저장소 (TRD §9)
├─ build.gradle.kts
└─ settings.gradle.kts
```

## 자주 쓰는 명령

```bash
# DB 기동 (최초 1회 schema.sql 자동 적용)
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun

# 테스트
./gradlew test

# 전체 빌드
./gradlew build

# Gradle wrapper 생성 (최초 1회, gradle CLI 필요)
gradle wrapper --gradle-version 8.12
```

## 환경 변수

| 변수 | 기본값 / 설명 |
| --- | --- |
| `JWT_SECRET` | 로컬 기본값 제공. 운영은 반드시 교체 (32 bytes↑) |
| `DEEPGRAM_API_KEY` | Deepgram STT 키 |
| `CLAUDE_API_KEY` | Claude Headless 키 |

로컬 DB 접속 정보는 `application.yml`에 하드코딩 (flowmeet / local-password).

## 설계 원칙 (TRD 발췌)

- **회의 상태**와 **processingStage**는 분리. 상태는 `SCHEDULED`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`만 사용. (TRD §4.1)
- **권한 검사는 반드시 백엔드에서**. 역할: 관리자/임원/팀장/팀원. (TRD §6)
- **비동기 작업 상태 머신**: `WAITING → PROCESSING → SPEAKER_MAPPING_REQUIRED → ANALYZING → REVIEW_REQUIRED → COMPLETED`. 자동 재시도 3회(1분/5분/15분). (TRD §5)
- **AI 제공자 교체 가능**하도록 Deepgram·Claude는 인터페이스로 추상화. (TRD §13)
- **파일 저장소**는 로컬 `uploads/` → 운영 시 S3로 교체 가능하도록 인터페이스 분리. 파일 접근 시 회의 권한 검증 필수.
- **보안**: 비밀번호 해시 저장, 음성 파일 URL 직접 노출 금지, 로그에 음성 원문·민감 회의 내용 금지. (TRD §11)

## API 네임스페이스

`/api/auth/*`, `/api/meetings/*`, `/api/tasks/*`, `/api/dashboard`, `/api/locations/*` 등. 상세는 TRD §8 참고.

## 참고 문서

- TRD: `../outputs/TRD_조직지시_업무흐름관리플랫폼.md`
- PRD: `../outputs/PRD_조직지시_업무흐름관리플랫폼.md`
- 스키마: `../outputs/schema.sql` (`database/schema.sql`과 동기화 유지)

## 코드 규칙

- Kotlin 공식 코딩 컨벤션 (4-space indent, trailing comma 허용).
- Spring Boot 설정은 `application.yml` 사용 (properties 금지).
- 엔티티는 `@Entity` + `data class` 사용 지양 → 일반 `class`로 작성 (JPA 호환).
- 테스트는 JUnit 5 + Spring Security Test. `@SpringBootTest`는 최소화하고 slice test 우선.
- 외부 API 호출(Deepgram, Claude)은 인터페이스 → 구현체 분리 (`infrastructure/`).
