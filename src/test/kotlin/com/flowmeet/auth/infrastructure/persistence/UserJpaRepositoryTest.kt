package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.User
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

    private val encoder = BCryptPasswordEncoder()

    @Test
    fun `email로 User를 저장하고 조회한다`() {
        val saved =
            users.save(
                User.create(
                    name = "Alice",
                    email = "alice@flowmeet.local",
                    rawPassword = "pw-at-least-8",
                    encoder = encoder,
                ),
            )

        val found = requireNotNull(users.findByEmail("alice@flowmeet.local")) { "user should exist" }

        assertThat(found.id).isEqualTo(saved.id)
        assertThat(found.email).isEqualTo("alice@flowmeet.local")
        assertThat(found.name).isEqualTo("Alice")
        assertThat(encoder.matches("pw-at-least-8", found.passwordHash)).isTrue()
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
    fun `ADMIN systemPermission을 저장하고 그대로 복원한다`() {
        val saved =
            users.saveAndFlush(
                User.create(
                    name = "Root",
                    email = "root@flowmeet.local",
                    rawPassword = "pw-at-least-8",
                    encoder = encoder,
                    systemPermission = SystemPermission.ADMIN,
                ),
            )

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
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }
}
