package com.flowmeet.common.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * 운영 secret(JWT·refresh pepper) 보호를 위한 회귀 가드.
 *
 * application-prod.yml 은 `${JWT_SECRET}` / `${REFRESH_TOKEN_PEPPER}` 를 default 없이 선언한다.
 * 환경변수가 없으면 Spring placeholder 미해결 → context load 실패여야 한다.
 * 이 테스트는 그 메커니즘(Properties 바인딩 시 placeholder resolution 실패)을 격리 검증한다.
 */
class SecretProfileStartupTest {
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(SecretsConfig::class.java)

    @Test
    fun `JWT_SECRET placeholder 미해결이면 context load 실패`() {
        runner
            .withPropertyValues(
                "flowmeet.jwt.secret=\${JWT_SECRET}",
                "flowmeet.jwt.access-token-ttl=PT30M",
                "flowmeet.jwt.refresh-token-ttl=P14D",
                "flowmeet.security.refresh-token.pepper=any-pepper-value-at-least-32-bytes-ok-xx",
                "flowmeet.security.refresh-token.cookie-secure=true",
            ).run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }

    @Test
    fun `REFRESH_TOKEN_PEPPER placeholder 미해결이면 context load 실패`() {
        runner
            .withPropertyValues(
                "flowmeet.jwt.secret=any-jwt-secret-value-at-least-32-bytes-ok-xx",
                "flowmeet.jwt.access-token-ttl=PT30M",
                "flowmeet.jwt.refresh-token-ttl=P14D",
                "flowmeet.security.refresh-token.pepper=\${REFRESH_TOKEN_PEPPER}",
                "flowmeet.security.refresh-token.cookie-secure=true",
            ).run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }

    @Test
    fun `jwt secret 선언 자체가 없으면(default profile 상황) context load 실패`() {
        runner
            .withPropertyValues(
                "flowmeet.jwt.access-token-ttl=PT30M",
                "flowmeet.jwt.refresh-token-ttl=P14D",
                "flowmeet.security.refresh-token.pepper=any-pepper-value-at-least-32-bytes-ok-xx",
                "flowmeet.security.refresh-token.cookie-secure=true",
            ).run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }

    @Test
    fun `모든 secret 이 올바르게 주입되면 context load 성공 (sanity)`() {
        runner
            .withPropertyValues(
                "flowmeet.jwt.secret=any-jwt-secret-value-at-least-32-bytes-ok-xx",
                "flowmeet.jwt.access-token-ttl=PT30M",
                "flowmeet.jwt.refresh-token-ttl=P14D",
                "flowmeet.security.refresh-token.pepper=any-pepper-value-at-least-32-bytes-ok-xx",
                "flowmeet.security.refresh-token.cookie-secure=true",
            ).run { ctx ->
                assertThat(ctx).hasNotFailed()
            }
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties::class, RefreshTokenProperties::class)
    class SecretsConfig
}
