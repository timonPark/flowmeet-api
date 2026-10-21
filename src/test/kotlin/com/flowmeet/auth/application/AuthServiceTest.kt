package com.flowmeet.auth.application

import com.flowmeet.auth.domain.Role
import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.User
import com.flowmeet.auth.domain.UserRole
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import com.flowmeet.auth.domain.RefreshToken as RefreshTokenEntity

class AuthServiceTest {
    private val fixedNow: Instant = Instant.parse("2026-10-21T03:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private val users: UserJpaRepository = mockk()
    private val roles: RoleJpaRepository = mockk()
    private val refreshTokens: RefreshTokenJpaRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
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

    @BeforeEach
    fun stubDummyHashEncoding() {
        // 사용자 미존재·비활성 경로에서도 timing-safe 하도록 dummy encode 결과를 stub.
        every { passwordEncoder.encode(any()) } returns "stub-dummy-bcrypt-hash"
    }

    @Test
    fun `유효한 자격이면 Access JWT와 Refresh raw를 발급하고 refresh_tokens에 해시로 저장한다`() {
        val roleId = UUID.randomUUID()
        val adminRole =
            Role(
                id = roleId,
                name = "ADMIN",
            )
        val admin =
            User.create(
                name = "관리자",
                email = "admin@flowmeet.local",
                rawPassword = "Temp#2026",
                encoder = fakeEncoder(),
                systemPermission = SystemPermission.ADMIN,
                roleId = roleId,
                temporaryPassword = true,
            )

        every { users.findByEmail("admin@flowmeet.local") } returns admin
        every { passwordEncoder.matches("Temp#2026", admin.passwordHash) } returns true
        every { roles.findById(roleId) } returns Optional.of(adminRole)
        every { jwtTokenProvider.issueAccessToken(admin.id.toString(), "ADMIN") } returns "access.jwt.value"
        every { refreshTokenGenerator.generate() } returns "opaque-refresh-raw"
        every { refreshTokenHasher.hash("opaque-refresh-raw") } returns "hashed-value"
        val saveSlot = slot<RefreshTokenEntity>()
        every { refreshTokens.save(capture(saveSlot)) } answers { saveSlot.captured }

        val result = service.login(email = "admin@flowmeet.local", rawPassword = "Temp#2026")

        assertThat(result.accessToken).isEqualTo("access.jwt.value")
        assertThat(result.accessTokenExpiresInSeconds).isEqualTo(1800L)
        assertThat(result.refreshTokenRaw).isEqualTo("opaque-refresh-raw")
        assertThat(result.user.id).isEqualTo(admin.id)
        assertThat(result.user.email).isEqualTo("admin@flowmeet.local")
        assertThat(result.user.name).isEqualTo("관리자")
        assertThat(result.user.role).isEqualTo(UserRole.ADMIN)
        assertThat(result.user.systemPermission).isEqualTo(SystemPermission.ADMIN)
        assertThat(result.user.requirePasswordChange).isTrue()
        assertThat(result.user.profileComplete).isTrue()

        val saved = saveSlot.captured
        assertThat(saved.userId).isEqualTo(admin.id)
        assertThat(saved.tokenHash).isEqualTo("hashed-value")
        assertThat(saved.createdAt).isEqualTo(fixedNow)
        assertThat(saved.expiresAt).isEqualTo(fixedNow.plus(Duration.ofDays(14)))
        assertThat(saved.revokedAt).isNull()
        verify(exactly = 1) { refreshTokens.save(any()) }
    }

    @Test
    fun `이메일이 존재하지 않아도 dummy 해시로 비번 검증을 수행하고 AUTH-401로 거부한다`() {
        every { users.findByEmail("ghost@flowmeet.local") } returns null
        every { passwordEncoder.matches("whatever", "stub-dummy-bcrypt-hash") } returns false

        assertThatThrownBy { service.login("ghost@flowmeet.local", "whatever") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            }

        // timing-safe: user가 없어도 BCrypt 비용을 발생시킨다.
        verify(exactly = 1) { passwordEncoder.matches("whatever", "stub-dummy-bcrypt-hash") }
        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `비밀번호가 틀리면 AUTH-401로 거부한다`() {
        val user =
            User.create(
                name = "Alice",
                email = "alice@flowmeet.local",
                rawPassword = "right-password",
                encoder = fakeEncoder(),
            )
        every { users.findByEmail("alice@flowmeet.local") } returns user
        every { passwordEncoder.matches("wrong-password", user.passwordHash) } returns false

        assertThatThrownBy { service.login("alice@flowmeet.local", "wrong-password") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            }

        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `비활성 사용자는 비밀번호가 맞아도 AUTH-401로 거부하고 비번 검증 비용을 유지한다`() {
        val encoder = fakeEncoder()
        val user =
            User::class.java
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
                    UUID.randomUUID(),
                    "Deactivated",
                    "deactivated@flowmeet.local",
                    encoder.encode("Temp#2026"),
                    null,
                    null,
                    null,
                    SystemPermission.USER,
                    false,
                    false, // active = false
                    fixedNow,
                    fixedNow,
                )

        every { users.findByEmail("deactivated@flowmeet.local") } returns user
        every { passwordEncoder.matches("Temp#2026", user.passwordHash) } returns true

        assertThatThrownBy { service.login("deactivated@flowmeet.local", "Temp#2026") }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
            }

        verify(exactly = 1) { passwordEncoder.matches("Temp#2026", user.passwordHash) }
        verify(exactly = 0) { refreshTokens.save(any()) }
    }

    @Test
    fun `roleId가 null이면 role을 TEAM_MEMBER로 발급한다`() {
        val user =
            User.create(
                name = "No Role",
                email = "no-role@flowmeet.local",
                rawPassword = "Temp#2026",
                encoder = fakeEncoder(),
                roleId = null,
            )
        every { users.findByEmail("no-role@flowmeet.local") } returns user
        every { passwordEncoder.matches("Temp#2026", user.passwordHash) } returns true
        every { jwtTokenProvider.issueAccessToken(user.id.toString(), "TEAM_MEMBER") } returns "jwt"
        every { refreshTokenGenerator.generate() } returns "raw"
        every { refreshTokenHasher.hash("raw") } returns "hash"
        every { refreshTokens.save(any()) } answers { firstArg() }

        val result = service.login("no-role@flowmeet.local", "Temp#2026")

        assertThat(result.user.role).isEqualTo(UserRole.TEAM_MEMBER)
        verify(exactly = 0) { roles.findById(any()) }
    }

    @Test
    fun `알 수 없는 role 이름이면 TEAM_MEMBER로 폴백한다`() {
        val roleId = UUID.randomUUID()
        val user =
            User.create(
                name = "Weird",
                email = "weird@flowmeet.local",
                rawPassword = "Temp#2026",
                encoder = fakeEncoder(),
                roleId = roleId,
            )
        every { users.findByEmail("weird@flowmeet.local") } returns user
        every { passwordEncoder.matches("Temp#2026", user.passwordHash) } returns true
        every { roles.findById(roleId) } returns
            Optional.of(Role(id = roleId, name = "UNKNOWN"))
        every { jwtTokenProvider.issueAccessToken(user.id.toString(), "TEAM_MEMBER") } returns "jwt"
        every { refreshTokenGenerator.generate() } returns "raw"
        every { refreshTokenHasher.hash("raw") } returns "hash"
        every { refreshTokens.save(any()) } answers { firstArg() }

        val result = service.login("weird@flowmeet.local", "Temp#2026")

        assertThat(result.user.role).isEqualTo(UserRole.TEAM_MEMBER)
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
