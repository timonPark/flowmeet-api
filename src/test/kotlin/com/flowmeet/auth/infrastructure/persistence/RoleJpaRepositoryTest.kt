package com.flowmeet.auth.infrastructure.persistence

import com.flowmeet.auth.domain.Role
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Repository 샘플 — @DataJpaTest + Testcontainers(PostgreSQL 16).
 * schema.sql이 initdb로 적용되므로 Hibernate ddl-auto는 none으로 둔다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RoleJpaRepositoryTest {
    @Autowired
    lateinit var roles: RoleJpaRepository

    @Test
    fun `name으로 Role을 저장하고 조회한다`() {
        val saved = roles.save(Role(name = "ADMIN"))

        val found = requireNotNull(roles.findByName("ADMIN")) { "ADMIN role should exist" }

        assertThat(found.id).isEqualTo(saved.id)
        assertThat(found.name).isEqualTo("ADMIN")
    }

    @Test
    fun `존재하지 않는 name 조회 시 null을 반환한다`() {
        val found = roles.findByName("NON_EXISTENT")

        assertThat(found).isNull()
    }

    @Test
    fun `중복된 name으로 저장하면 unique 제약 위반이 발생한다`() {
        roles.saveAndFlush(Role(name = "DUP"))

        assertThatThrownBy {
            roles.saveAndFlush(Role(name = "DUP"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
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
        }
    }
}
