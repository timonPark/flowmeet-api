# flowmeet-api

조직 지시 및 업무 흐름 관리 플랫폼 API 서버. 웹 프론트엔드는 별도 리포 `flowmeet-web`.

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
flowmeet-api/
├─ src/main/kotlin/com/flowmeet/
├─ src/main/resources/application.yml
├─ database/schema.sql
├─ uploads/
├─ docker-compose.yml
└─ build.gradle.kts
```

TRD는 상위 레포 `outputs/TRD_조직지시_업무흐름관리플랫폼.md` 참고.

## Claude Code 하네스

이 레포는 Claude Code 하네스를 포함한다. 상세는 [CLAUDE.md](./CLAUDE.md) 참고.

- `.claude/skills/<이름>/SKILL.md` — 로컬 스킬 정의 (SSOT)
- `.claude/agents/<이름>.md` — 전문 에이전트 정의
- `.claude/commands/` — 슬래시 커맨드 (선택)
- `.claude/settings.json` — 공유 허용 툴·env (팀 기본값, 추적)
- `.claude/settings.local.json` — 개인 오버라이드 (gitignore)
- `work_history/` — 작업 이력 (**추적함**). Phase 2 메트릭 체계가 이 기록을 집계.
