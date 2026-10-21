package com.flowmeet.auth.application

import com.flowmeet.auth.domain.RefreshToken
import com.flowmeet.auth.domain.User
import com.flowmeet.auth.domain.UserRole
import com.flowmeet.auth.infrastructure.persistence.RefreshTokenJpaRepository
import com.flowmeet.auth.infrastructure.persistence.RoleJpaRepository
import com.flowmeet.auth.infrastructure.persistence.UserJpaRepository
import com.flowmeet.auth.infrastructure.token.RefreshTokenGenerator
import com.flowmeet.auth.infrastructure.token.RefreshTokenHasher
import com.flowmeet.common.exception.ApiException
import com.flowmeet.common.exception.ErrorCode
import com.flowmeet.common.logging.SensitiveDataMasker
import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AuthService(
    private val users: UserJpaRepository,
    private val roles: RoleJpaRepository,
    private val refreshTokens: RefreshTokenJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val refreshTokenGenerator: RefreshTokenGenerator,
    private val refreshTokenHasher: RefreshTokenHasher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 사용자 존재 여부와 무관하게 BCrypt 비용을 발생시켜 응답 시간을 균일화한다(열거 공격 방지).
    private val dummyPasswordHash: String by lazy {
        passwordEncoder.encode(DUMMY_RAW_FOR_TIMING_SAFE_AUTH)
    }

    @Transactional
    fun login(
        email: String,
        rawPassword: String,
    ): LoginResult {
        log.info("auth.login attempt email={}", SensitiveDataMasker.email(email))

        val user = users.findByEmail(email)
        val passwordMatches =
            passwordEncoder.matches(rawPassword, user?.passwordHash ?: dummyPasswordHash)

        if (user == null || !user.active || !passwordMatches) {
            throw invalidCredentials()
        }

        val role = resolveRole(user)
        val accessToken = jwtTokenProvider.issueAccessToken(user.id.toString(), role.name)

        val refreshRaw = refreshTokenGenerator.generate()
        val now = clock.instant()
        refreshTokens.save(
            RefreshToken.issue(
                userId = user.id,
                tokenHash = refreshTokenHasher.hash(refreshRaw),
                issuedAt = now,
                expiresAt = now.plus(jwtProperties.refreshTokenTtl),
            ),
        )

        log.info("auth.login success userId={} role={}", user.id, role.name)

        return LoginResult(
            accessToken = accessToken,
            accessTokenExpiresInSeconds = jwtProperties.accessTokenTtl.seconds,
            refreshTokenRaw = refreshRaw,
            user =
                LoginResult.AuthenticatedUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    role = role,
                    systemPermission = user.systemPermission,
                    requirePasswordChange = user.temporaryPassword,
                    profileComplete = true,
                ),
        )
    }

    private fun resolveRole(user: User): UserRole {
        val roleId = user.roleId ?: return UserRole.TEAM_MEMBER
        val roleName = roles.findById(roleId).orElse(null)?.name
        return UserRole.fromName(roleName)
    }

    private fun invalidCredentials(): ApiException = ApiException(ErrorCode.INVALID_CREDENTIALS)

    companion object {
        private const val DUMMY_RAW_FOR_TIMING_SAFE_AUTH = "flowmeet-timing-safe-dummy-value"
    }
}
