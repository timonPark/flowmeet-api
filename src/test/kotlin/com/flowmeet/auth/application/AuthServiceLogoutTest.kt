package com.flowmeet.auth.application

import com.flowmeet.auth.domain.RefreshToken
import com.flowmeet.auth.infrastructure.persistence.RefreshTokenJpaRepository
import com.flowmeet.auth.infrastructure.persistence.RoleJpaRepository
import com.flowmeet.auth.infrastructure.persistence.UserJpaRepository
import com.flowmeet.auth.infrastructure.token.RefreshTokenGenerator
import com.flowmeet.auth.infrastructure.token.RefreshTokenHasher
import com.flowmeet.common.exception.ApiException
import com.flowmeet.common.exception.ErrorCode
import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.JwtTokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuthServiceLogoutTest {
    private val fixedNow: Instant = Instant.parse("2026-10-21T03:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private val users: UserJpaRepository = mockk()
    private val roles: RoleJpaRepository = mockk()
    private val refreshTokens: RefreshTokenJpaRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk(relaxed = true)
    private val jwtTokenProvider: JwtTokenProvider = mockk()
    private val refreshTokenGenerator: RefreshTokenGenerator = mockk()
    private val refreshTokenHasher: RefreshTokenHasher = mockk()

    private val jwtProperties =
        JwtProperties(
            secret = "test-jwt-secret-at-least-32-bytes-for-unit",
            accessTokenTtl = Duration.ofMinutes(30),
            refreshTokenTtl = Duration.ofDays(14),
        )

    private val service =
        AuthService(
            users = users,
            roles = roles,
            refreshTokens = refreshTokens,
            passwordEncoder = passwordEncoder,
            jwtTokenProvider = jwtTokenProvider,
            jwtProperties = jwtProperties,
            refreshTokenGenerator = refreshTokenGenerator,
            refreshTokenHasher = refreshTokenHasher,
            clock = clock,
        )

    @Test
    fun `쿠키만 있으면 그 토큰 하나만 revoke한다`() {
        val userId = UUID.randomUUID()
        val token =
            RefreshToken.issue(
                userId = userId,
                tokenHash = "hash-cookie",
                issuedAt = fixedNow.minusSeconds(60),
                expiresAt = fixedNow.plus(Duration.ofDays(14)),
            )
        every { refreshTokenHasher.hash("cookie-raw") } returns "hash-cookie"
        every { refreshTokens.findByTokenHash("hash-cookie") } returns token
        every { refreshTokens.save(any()) } answers { firstArg() }

        service.logout(rawRefresh = "cookie-raw", bearerUserId = null)

        assertThat(token.revokedAt).isEqualTo(fixedNow)
        verify(exactly = 1) { refreshTokens.save(token) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `쿠키 raw는 있지만 매칭이 없으면 (예 - 이미 삭제된 토큰) 멱등하게 noop 처리한다`() {
        every { refreshTokenHasher.hash("vanished-raw") } returns "hash-vanished"
        every { refreshTokens.findByTokenHash("hash-vanished") } returns null

        service.logout(rawRefresh = "vanished-raw", bearerUserId = null)

        verify(exactly = 0) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `Bearer userId만 있으면 그 user의 모든 활성 refresh를 revokeAllActive로 끝낸다`() {
        val userId = UUID.randomUUID()
        every { refreshTokens.revokeAllActiveByUserId(userId, fixedNow) } returns 2

        service.logout(rawRefresh = null, bearerUserId = userId)

        verify(exactly = 1) { refreshTokens.revokeAllActiveByUserId(userId, fixedNow) }
        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `쿠키와 Bearer가 모두 없으면 COMMON-401로 거부한다`() {
        assertThatThrownBy { service.logout(rawRefresh = null, bearerUserId = null) }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.UNAUTHORIZED)
            }

        verify(exactly = 0) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `이미 revoke된 쿠키 토큰이면 추가 revoke 없이 멱등 처리한다`() {
        val token =
            RefreshToken
                .issue(
                    userId = UUID.randomUUID(),
                    tokenHash = "hash-already-revoked",
                    issuedAt = fixedNow.minusSeconds(600),
                    expiresAt = fixedNow.plus(Duration.ofDays(14)),
                ).apply { revoke(fixedNow.minusSeconds(60)) }

        every { refreshTokenHasher.hash("stale-raw") } returns "hash-already-revoked"
        every { refreshTokens.findByTokenHash("hash-already-revoked") } returns token
        every { refreshTokens.save(any()) } answers { firstArg() }

        service.logout(rawRefresh = "stale-raw", bearerUserId = null)

        assertThat(token.revokedAt).isEqualTo(fixedNow.minusSeconds(60))
    }
}
