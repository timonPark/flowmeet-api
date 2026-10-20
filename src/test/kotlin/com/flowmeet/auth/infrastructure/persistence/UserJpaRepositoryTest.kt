package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.User
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserJpaRepositoryTest {
    @Autowired
    lateinit var users: UserJpaRepository

    @PersistenceContext
    lateinit var em: EntityManager

    private val encoder = BCryptPasswordEncoder()

    /**
     * flush + clear로 1차 캐시를 비워 DB에서 다시 복원되는지 검증한다.
     * 캐시 리턴이 끼면 enum·password hash 왕복이 실제 저장되었는지 보장할 수 없다.
     */
    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    @Test
    fun `email로 User를 저장하고 flush·clear 후 재조회해 DB에서 복원된 필드를 검증한다`() {
        val saved =
            users.save(
                User.create(
                    name = "Alice",
                    email = "alice@flowmeet.local",
                    rawPassword = "pw-at-least-8",
                    encoder = encoder,
                ),
            )
        flushAndClear()

        val found = requireNotNull(users.findByEmail("alice@flowmeet.local")) { "user should exist" }

        assertThat(found.id).isEqualTo(saved.id)
        assertThat(found.email).isEqualTo("alice@flowmeet.local")
        assertThat(found.name).isEqualTo("Alice")
        assertThat(encoder.matches("pw-at-least-8", found.passwordHash)).isTrue()
        assertThat(found.systemPermission).isEqualTo(SystemPermission.USER)
        assertThat(found.temporaryPassword).isTrue()
        assertThat(found.active).isTrue()
    }

    @Test
    fun `존재하지 않는 email 조회 시 null을 반환한다`() {
        val found = users.findByEmail("missing@flowmeet.local")

        assertThat(found).isNull()
    }

    @Test
    fun `중복된 email로 저장하면 unique 제약 위반이 발생한다`() {
        users.saveAndFlush(
            User.create(
                name = "Dup A",
                email = "dup@flowmeet.local",
                rawPassword = "pw-at-least-8",
                encoder = encoder,
            ),
        )

        assertThatThrownBy {
            users.saveAndFlush(
                User.create(
                    name = "Dup B",
                    email = "dup@flowmeet.local",
                    rawPassword = "pw-at-least-8",
                    encoder = encoder,
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `ADMIN systemPermission을 저장하고 flush·clear 후 DB에서 다시 ADMIN으로 복원한다`() {
        val saved =
            users.save(
                User.create(
                    name = "Root",
                    email = "root@flowmeet.local",
                    rawPassword = "pw-at-least-8",
                    encoder = encoder,
                    systemPermission = SystemPermission.ADMIN,
                ),
            )
        flushAndClear()

        val found = requireNotNull(users.findByEmail("root@flowmeet.local"))
        assertThat(found.systemPermission).isEqualTo(SystemPermission.ADMIN)
        assertThat(found.id).isEqualTo(saved.id)
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
            // validate로 schema ↔ Entity 매핑 회귀를 CI에서 자동 검출.
            // schema는 withInitScript("schema.sql")로 적용되고 Flyway는 비활성화해 중복 적용을 피한다.
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }
}
