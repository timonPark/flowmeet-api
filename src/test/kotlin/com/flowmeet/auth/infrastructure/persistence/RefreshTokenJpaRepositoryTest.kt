package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.RefreshToken
import com.flowmeet.auth.domain.User
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RefreshTokenJpaRepositoryTest {
    @Autowired
    lateinit var refreshTokens: RefreshTokenJpaRepository

    @Autowired
    lateinit var users: UserJpaRepository

    @PersistenceContext
    lateinit var em: EntityManager

    private val encoder = BCryptPasswordEncoder()
    private val now: Instant = Instant.parse("2026-10-21T03:00:00Z")

    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    private fun seedUser(email: String): User =
        users.saveAndFlush(
            User.create(
                name = "u-$email",
                email = email,
                rawPassword = "pw-at-least-8",
                encoder = encoder,
            ),
        )

    @Test
    fun `revokeAllActiveByUserId는 userId의 활성 토큰만 revoke하고 수량을 반환한다`() {
        val user = seedUser("owner@flowmeet.local")

        val active1 =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = user.id,
                    tokenHash = "hash-active-1",
                    issuedAt = now,
                    expiresAt = now.plusSeconds(3600),
                ),
            )
        val active2 =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = user.id,
                    tokenHash = "hash-active-2",
                    issuedAt = now,
                    expiresAt = now.plusSeconds(3600),
                ),
            )
        val alreadyRevoked =
            refreshTokens
                .save(
                    RefreshToken.issue(
                        userId = user.id,
                        tokenHash = "hash-already-revoked",
                        issuedAt = now,
                        expiresAt = now.plusSeconds(3600),
                    ),
                ).apply { revoke(now.minusSeconds(60)) }
        refreshTokens.saveAndFlush(alreadyRevoked)
        val expired =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = user.id,
                    tokenHash = "hash-expired",
                    issuedAt = now.minusSeconds(7200),
                    expiresAt = now.minusSeconds(60),
                ),
            )
        flushAndClear()

        val revokedCount = refreshTokens.revokeAllActiveByUserId(user.id, now)
        flushAndClear()

        assertThat(revokedCount).isEqualTo(2)
        val a1 = refreshTokens.findById(active1.id).orElseThrow()
        val a2 = refreshTokens.findById(active2.id).orElseThrow()
        val ar = refreshTokens.findById(alreadyRevoked.id).orElseThrow()
        val ex = refreshTokens.findById(expired.id).orElseThrow()
        assertThat(a1.revokedAt).isEqualTo(now)
        assertThat(a2.revokedAt).isEqualTo(now)
        assertThat(ar.revokedAt).isEqualTo(now.minusSeconds(60))
        assertThat(ex.revokedAt).isNull()
    }

    @Test
    fun `revokeAllActiveByUserId는 다른 user의 토큰은 건드리지 않는다`() {
        val user1 = seedUser("one@flowmeet.local")
        val user2 = seedUser("two@flowmeet.local")

        val u1Active =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = user1.id,
                    tokenHash = "hash-u1",
                    issuedAt = now,
                    expiresAt = now.plusSeconds(3600),
                ),
            )
        val u2Active =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = user2.id,
                    tokenHash = "hash-u2",
                    issuedAt = now,
                    expiresAt = now.plusSeconds(3600),
                ),
            )
        flushAndClear()

        val revokedCount = refreshTokens.revokeAllActiveByUserId(user1.id, now)
        flushAndClear()

        assertThat(revokedCount).isEqualTo(1)
        assertThat(refreshTokens.findById(u1Active.id).orElseThrow().revokedAt).isEqualTo(now)
        assertThat(refreshTokens.findById(u2Active.id).orElseThrow().revokedAt).isNull()
    }

    @Test
    fun `활성 토큰이 없으면 revokeAllActiveByUserId는 0을 반환한다`() {
        val revokedCount = refreshTokens.revokeAllActiveByUserId(UUID.randomUUID(), now)
        assertThat(revokedCount).isEqualTo(0)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("flowmeet")
                .withUsername("flowmeet")
                .withPassword("local-password")
                .withInitScript("schema.sql")

        @DynamicPropertySource
        @JvmStatic
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }
}
