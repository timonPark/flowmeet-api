package com.flowmeet.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken private constructor(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    fun revoke(at: Instant) {
        if (revokedAt == null) revokedAt = at
    }

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant): Boolean = revokedAt == null && !isExpired(now)

    companion object {
        fun issue(
            userId: UUID,
            tokenHash: String,
            issuedAt: Instant,
            expiresAt: Instant,
        ): RefreshToken =
            RefreshToken(
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
                createdAt = issuedAt,
            )
    }
}
