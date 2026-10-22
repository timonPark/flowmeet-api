package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshTokenJpaRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update RefreshToken r set r.revokedAt = :at " +
            "where r.userId = :userId and r.revokedAt is null and r.expiresAt > :at",
    )
    fun revokeAllActiveByUserId(
        @Param("userId") userId: UUID,
        @Param("at") at: Instant,
    ): Int
}
