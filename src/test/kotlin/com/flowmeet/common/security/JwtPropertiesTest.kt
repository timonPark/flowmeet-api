package com.flowmeet.common.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtPropertiesTest {
    private val longEnoughSecret = "a".repeat(32)

    @Test
    fun `secret이 32바이트 미만이면 생성 시점에 거부한다`() {
        assertThatThrownBy {
            JwtProperties(
                secret = "a".repeat(31),
                accessTokenTtl = Duration.ofMinutes(30),
                refreshTokenTtl = Duration.ofDays(14),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32")
    }

    @Test
    fun `secret이 32바이트 이상이면 통과한다`() {
        val props =
            JwtProperties(
                secret = longEnoughSecret,
                accessTokenTtl = Duration.ofMinutes(30),
                refreshTokenTtl = Duration.ofDays(14),
            )
        assertThat(props.secret).isEqualTo(longEnoughSecret)
        assertThat(props.accessTokenTtl).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun `accessTokenTtl이 0 이하이면 거부한다`() {
        assertThatThrownBy {
            JwtProperties(
                secret = longEnoughSecret,
                accessTokenTtl = Duration.ZERO,
                refreshTokenTtl = Duration.ofDays(14),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("accessTokenTtl")
    }

    @Test
    fun `refreshTokenTtl이 0 이하이면 거부한다`() {
        assertThatThrownBy {
            JwtProperties(
                secret = longEnoughSecret,
                accessTokenTtl = Duration.ofMinutes(30),
                refreshTokenTtl = Duration.ZERO,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("refreshTokenTtl")
    }
}
