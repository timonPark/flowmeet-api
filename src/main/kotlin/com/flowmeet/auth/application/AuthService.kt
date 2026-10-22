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
import java.util.UUID

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

    // ApiException throw 경로에서도 재사용 감지 시의 revokeAllActive() 부수효과는 반드시 커밋되어야 한다.
    // AUTH-410 전 다른 ApiException throw(UNAUTHORIZED, EXPIRED_TOKEN)는 모두 state 변경 이전 지점이므로
    // noRollbackFor 로 선언해도 안전하다.
    @Transactional(noRollbackFor = [ApiException::class])
    fun rotate(rawRefresh: String): RefreshRotationResult {
        val hashed = refreshTokenHasher.hash(rawRefresh)
        val stored =
            refreshTokens.findByTokenHash(hashed)
                ?: throw ApiException(ErrorCode.UNAUTHORIZED)

        val now = clock.instant()

        if (stored.revokedAt != null) {
            // 세션 하이재킹 가능성: 재사용 감지. 해당 user의 모든 활성 refresh를 즉시 전부 revoke.
            log.warn("auth.refresh reuse detected userId={} — revoking all active sessions", stored.userId)
            refreshTokens.revokeAllActiveByUserId(stored.userId, now)
            throw ApiException(ErrorCode.REFRESH_TOKEN_REVOKED)
        }
        if (stored.isExpired(now)) {
            throw ApiException(ErrorCode.EXPIRED_TOKEN)
        }

        val user =
            users.findById(stored.userId).orElse(null)
                ?: throw ApiException(ErrorCode.UNAUTHORIZED)
        if (!user.active) {
            throw ApiException(ErrorCode.UNAUTHORIZED)
        }

        val role = resolveRole(user)

        stored.revoke(now)
        val newRaw = refreshTokenGenerator.generate()
        refreshTokens.save(
            RefreshToken.issue(
                userId = user.id,
                tokenHash = refreshTokenHasher.hash(newRaw),
                issuedAt = now,
                expiresAt = now.plus(jwtProperties.refreshTokenTtl),
            ),
        )
        val accessToken = jwtTokenProvider.issueAccessToken(user.id.toString(), role.name)

        log.info("auth.refresh success userId={} role={}", user.id, role.name)

        return RefreshRotationResult(
            accessToken = accessToken,
            accessTokenExpiresInSeconds = jwtProperties.accessTokenTtl.seconds,
            refreshTokenRaw = newRaw,
        )
    }

    @Transactional
    fun logout(
        rawRefresh: String?,
        bearerUserId: UUID?,
    ) {
        val now = clock.instant()
        when {
            !rawRefresh.isNullOrBlank() -> {
                val hashed = refreshTokenHasher.hash(rawRefresh)
                val stored = refreshTokens.findByTokenHash(hashed)
                if (stored == null) {
                    log.info("auth.logout cookie refresh not found — treating as idempotent")
                    return
                }
                stored.revoke(now)
                refreshTokens.save(stored)
                log.info("auth.logout cookie userId={}", stored.userId)
            }
            bearerUserId != null -> {
                log.warn("auth.logout cookie absent, revoking all active userId={}", bearerUserId)
                refreshTokens.revokeAllActiveByUserId(bearerUserId, now)
            }
            else -> throw ApiException(ErrorCode.UNAUTHORIZED)
        }
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
