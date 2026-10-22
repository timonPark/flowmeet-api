package com.flowmeet.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RefreshTokenTest {
    private val userId: UUID = UUID.randomUUID()
    private val issuedAt: Instant = Instant.parse("2026-10-21T03:00:00Z")
    private val expiresAt: Instant = issuedAt.plusSeconds(14 * 24 * 3600)

    @Test
    fun `발급 직후에는 revokedAt이 null이고 isActive=true, isExpired=false`() {
        val token = newToken()

        assertThat(token.revokedAt).isNull()
        assertThat(token.isActive(issuedAt)).isTrue()
        assertThat(token.isExpired(issuedAt)).isFalse()
    }

    @Test
    fun `revoke 호출 시 revokedAt이 세팅되고 isActive=false`() {
        val token = newToken()
        val revokedAt = issuedAt.plusSeconds(60)

        token.revoke(revokedAt)

        assertThat(token.revokedAt).isEqualTo(revokedAt)
        assertThat(token.isActive(revokedAt)).isFalse()
    }

    @Test
    fun `이미 revoke된 토큰에 다시 revoke를 호출해도 기존 revokedAt을 유지한다 (멱등)`() {
        val token = newToken()
        val firstRevoke = issuedAt.plusSeconds(60)
        val secondRevoke = issuedAt.plusSeconds(120)

        token.revoke(firstRevoke)
        token.revoke(secondRevoke)

        assertThat(token.revokedAt).isEqualTo(firstRevoke)
    }

    @Test
    fun `expiresAt에 도달하면 isExpired=true, isActive=false`() {
        val token = newToken()

        assertThat(token.isExpired(expiresAt)).isTrue()
        assertThat(token.isActive(expiresAt)).isFalse()
    }

    @Test
    fun `expiresAt 이전이고 revoke 안 됐으면 isActive=true`() {
        val token = newToken()
        val justBeforeExpiry = expiresAt.minusSeconds(1)

        assertThat(token.isActive(justBeforeExpiry)).isTrue()
        assertThat(token.isExpired(justBeforeExpiry)).isFalse()
    }

    private fun newToken(): RefreshToken =
        RefreshToken.issue(
            userId = userId,
            tokenHash = "hash-$userId",
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
}
