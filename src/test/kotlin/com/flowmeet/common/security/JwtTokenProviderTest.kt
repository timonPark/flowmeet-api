package com.flowmeet.common.security

import io.jsonwebtoken.JwtException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JwtTokenProviderTest {
    private val secret = "a".repeat(64)
    private val baseInstant: Instant = Instant.parse("2026-10-19T07:00:00Z")

    private fun fixedClock(at: Instant = baseInstant): Clock =
        mockk<Clock>(relaxed = true).also {
            every { it.instant() } returns at
            every { it.zone } returns ZoneOffset.UTC
        }

    private fun provider(
        clock: Clock = fixedClock(),
        accessTtl: Duration = Duration.ofMinutes(30),
    ): JwtTokenProvider {
        val props =
            JwtProperties(
                secret = secret,
                accessTokenTtl = accessTtl,
                refreshTokenTtl = Duration.ofDays(14),
            )
        return JwtTokenProvider(props, clock)
    }

    @Test
    fun `발급한 access 토큰을 파싱해 subject·role·만료 시각을 확인한다`() {
        val sut = provider()

        val token = sut.issueAccessToken(subject = "user-42", role = "ADMIN")
        val claims = sut.parse(token)

        assertThat(claims.subject).isEqualTo("user-42")
        assertThat(claims["role"]).isEqualTo("ADMIN")
        assertThat(claims.issuedAt.toInstant()).isEqualTo(baseInstant)
        assertThat(claims.expiration.toInstant()).isEqualTo(baseInstant.plus(Duration.ofMinutes(30)))
    }

    @Test
    fun `validate는 유효 토큰이면 true, 만료 토큰이면 false를 반환한다`() {
        val issuer = provider(clock = fixedClock(baseInstant))
        val token = issuer.issueAccessToken(subject = "user-42", role = "USER")

        // 발급 직후 → valid
        assertThat(issuer.validate(token)).isTrue()

        // 31분 뒤 시점의 provider → expired
        val laterVerifier = provider(clock = fixedClock(baseInstant.plus(Duration.ofMinutes(31))))
        assertThat(laterVerifier.validate(token)).isFalse()
    }

    @Test
    fun `만료 토큰을 parse하면 JwtException이 발생한다`() {
        val issuer = provider(clock = fixedClock(baseInstant))
        val token = issuer.issueAccessToken(subject = "user-42", role = "USER")

        val laterVerifier = provider(clock = fixedClock(baseInstant.plus(Duration.ofMinutes(31))))

        assertThatThrownBy { laterVerifier.parse(token) }
            .isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `서명이 다른 비밀키로 발급된 토큰은 validate false이고 parse는 JwtException`() {
        val issuer = provider()
        val token = issuer.issueAccessToken(subject = "user-42", role = "USER")

        val differentSecretProps =
            JwtProperties(
                secret = "b".repeat(64),
                accessTokenTtl = Duration.ofMinutes(30),
                refreshTokenTtl = Duration.ofDays(14),
            )
        val attacker = JwtTokenProvider(differentSecretProps, fixedClock())

        assertThat(attacker.validate(token)).isFalse()
        assertThatThrownBy { attacker.parse(token) }
            .isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `malformed 토큰은 validate false, parse는 JwtException`() {
        val sut = provider()
        assertThat(sut.validate("not.a.jwt")).isFalse()
        assertThatThrownBy { sut.parse("not.a.jwt") }.isInstanceOf(JwtException::class.java)
    }
}
