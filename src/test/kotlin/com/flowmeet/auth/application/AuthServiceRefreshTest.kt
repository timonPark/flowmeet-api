package com.flowmeet.auth.application

import com.flowmeet.auth.domain.RefreshToken
import com.flowmeet.auth.domain.Role
import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.User
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
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class AuthServiceRefreshTest {
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
    fun `매칭되는 refresh가 없으면 COMMON-401로 거부한다`() {
        every { refreshTokenHasher.hash("unknown-raw") } returns "hash-unknown"
        every { refreshTokens.findByTokenHash("hash-unknown") } returns null

        assertThatThrownBy { service.rotate("unknown-raw") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.UNAUTHORIZED)
            }

        verify(exactly = 0) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `이미 revoke된 refresh로 재요청하면 AUTH-410 + 전 세션 revoke를 호출한다`() {
        val userId = UUID.randomUUID()
        val revokedToken =
            RefreshToken
                .issue(
                    userId = userId,
                    tokenHash = "hash-revoked",
                    issuedAt = fixedNow.minusSeconds(300),
                    expiresAt = fixedNow.plus(Duration.ofDays(14)),
                ).apply { revoke(fixedNow.minusSeconds(60)) }

        every { refreshTokenHasher.hash("revoked-raw") } returns "hash-revoked"
        every { refreshTokens.findByTokenHash("hash-revoked") } returns revokedToken
        every { refreshTokens.revokeAllActiveByUserId(userId, fixedNow) } returns 3

        assertThatThrownBy { service.rotate("revoked-raw") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED)
            }

        verify(exactly = 1) { refreshTokens.revokeAllActiveByUserId(userId, fixedNow) }
        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `만료된 refresh로 요청하면 AUTH-401-001로 거부한다`() {
        val userId = UUID.randomUUID()
        val expiredToken =
            RefreshToken.issue(
                userId = userId,
                tokenHash = "hash-expired",
                issuedAt = fixedNow.minus(Duration.ofDays(20)),
                expiresAt = fixedNow.minus(Duration.ofDays(1)),
            )

        every { refreshTokenHasher.hash("expired-raw") } returns "hash-expired"
        every { refreshTokens.findByTokenHash("hash-expired") } returns expiredToken

        assertThatThrownBy { service.rotate("expired-raw") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.EXPIRED_TOKEN)
            }

        verify(exactly = 0) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `활성 refresh로 요청하면 기존 토큰을 revoke하고 새 access+refresh를 발급한다`() {
        val roleId = UUID.randomUUID()
        val user =
            User.create(
                name = "Owner",
                email = "owner@flowmeet.local",
                rawPassword = "pw-at-least-8",
                encoder = fakeEncoder(),
                roleId = roleId,
            )
        val activeToken =
            RefreshToken.issue(
                userId = user.id,
                tokenHash = "hash-active-old",
                issuedAt = fixedNow.minusSeconds(300),
                expiresAt = fixedNow.plus(Duration.ofDays(14)),
            )

        every { refreshTokenHasher.hash("active-raw") } returns "hash-active-old"
        every { refreshTokens.findByTokenHash("hash-active-old") } returns activeToken
        every { users.findById(user.id) } returns Optional.of(user)
        every { roles.findById(roleId) } returns Optional.of(Role(id = roleId, name = "ADMIN"))
        every { jwtTokenProvider.issueAccessToken(user.id.toString(), "ADMIN") } returns "new.access.jwt"
        every { refreshTokenGenerator.generate() } returns "new-opaque-raw"
        every { refreshTokenHasher.hash("new-opaque-raw") } returns "hash-active-new"

        val saveSlot = slot<RefreshToken>()
        every { refreshTokens.save(capture(saveSlot)) } answers { saveSlot.captured }

        val result = service.rotate("active-raw")

        assertThat(result.accessToken).isEqualTo("new.access.jwt")
        assertThat(result.accessTokenExpiresInSeconds).isEqualTo(1800L)
        assertThat(result.refreshTokenRaw).isEqualTo("new-opaque-raw")

        assertThat(activeToken.revokedAt).isEqualTo(fixedNow)
        val savedNew = saveSlot.captured
        assertThat(savedNew.userId).isEqualTo(user.id)
        assertThat(savedNew.tokenHash).isEqualTo("hash-active-new")
        assertThat(savedNew.createdAt).isEqualTo(fixedNow)
        assertThat(savedNew.expiresAt).isEqualTo(fixedNow.plus(Duration.ofDays(14)))
        assertThat(savedNew.revokedAt).isNull()

        verify(exactly = 1) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    @Test
    fun `회전 중 user가 존재하지 않으면 COMMON-401로 거부한다`() {
        val activeToken =
            RefreshToken.issue(
                userId = UUID.randomUUID(),
                tokenHash = "hash-user-missing",
                issuedAt = fixedNow,
                expiresAt = fixedNow.plus(Duration.ofDays(1)),
            )
        every { refreshTokenHasher.hash("raw") } returns "hash-user-missing"
        every { refreshTokens.findByTokenHash("hash-user-missing") } returns activeToken
        every { users.findById(activeToken.userId) } returns Optional.empty()

        assertThatThrownBy { service.rotate("raw") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.UNAUTHORIZED)
            }

        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `회전 중 user가 비활성이면 COMMON-401로 거부하고 기존 토큰 state도 변경하지 않는다`() {
        val userId = UUID.randomUUID()
        val activeToken =
            RefreshToken.issue(
                userId = userId,
                tokenHash = "hash-user-inactive",
                issuedAt = fixedNow,
                expiresAt = fixedNow.plus(Duration.ofDays(1)),
            )
        val inactiveUser = buildInactiveUser(userId)

        every { refreshTokenHasher.hash("raw") } returns "hash-user-inactive"
        every { refreshTokens.findByTokenHash("hash-user-inactive") } returns activeToken
        every { users.findById(userId) } returns Optional.of(inactiveUser)

        assertThatThrownBy { service.rotate("raw") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.UNAUTHORIZED)
            }

        // 기존 refresh는 revoke되지 않고 신규 발급도 없다.
        assertThat(activeToken.revokedAt).isNull()
        verify(exactly = 0) { refreshTokens.save(any()) }
        verify(exactly = 0) { refreshTokens.revokeAllActiveByUserId(any(), any()) }
    }

    private fun buildInactiveUser(userId: UUID): User {
        val encoder = fakeEncoder()
        return User::class.java
            .getDeclaredConstructor(
                UUID::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                UUID::class.java,
                UUID::class.java,
                UUID::class.java,
                SystemPermission::class.java,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
                Instant::class.java,
                Instant::class.java,
            ).apply { isAccessible = true }
            .newInstance(
                userId,
                "Inactive Owner",
                "inactive@flowmeet.local",
                encoder.encode("pw-at-least-8"),
                null,
                null,
                null,
                SystemPermission.USER,
                false,
                false,
                fixedNow,
                fixedNow,
            )
    }

    private fun fakeEncoder(): PasswordEncoder =
        object : PasswordEncoder {
            override fun encode(rawPassword: CharSequence?): String = "encoded:$rawPassword"

            override fun matches(
                rawPassword: CharSequence?,
                encodedPassword: String?,
            ): Boolean = encodedPassword == "encoded:$rawPassword"
        }
}
