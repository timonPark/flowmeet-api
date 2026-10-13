# flowmeet-backend

조직 지시 및 업무 흐름 관리 플랫폼 백엔드.

- Kotlin + Spring Boot 3.4
- Spring Data JPA, Spring Security(JWT), Validation
- PostgreSQL 16
- Deepgram STT, Claude Headless 연동 예정

## 사전 요구사항

- JDK 21+
- Docker, Docker Compose
- Gradle 8.12+ (최초 1회 wrapper 생성용)

## 최초 세팅

```bash
# Gradle Wrapper 생성 (최초 1회만)
gradle wrapper --gradle-version 8.12

# DB 기동 (schema.sql 자동 적용)
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

## 환경 변수

| 변수명 | 설명 |
| --- | --- |
| `JWT_SECRET` | JWT 서명 비밀키 (32 bytes 이상) |
| `DEEPGRAM_API_KEY` | Deepgram STT API 키 |
| `CLAUDE_API_KEY` | Claude Headless API 키 |

## 디렉터리 구조

```text
backend/
├─ src/main/kotlin/com/flowmeet/
├─ src/main/resources/application.yml
├─ database/schema.sql
├─ uploads/
├─ docker-compose.yml
└─ build.gradle.kts
```

TRD는 상위 레포 `outputs/TRD_조직지시_업무흐름관리플랫폼.md` 참고.
