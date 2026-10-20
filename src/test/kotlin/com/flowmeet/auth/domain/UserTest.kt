package com.flowmeet.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class UserTest {
    private val encoder: PasswordEncoder = BCryptPasswordEncoder()

    @Test
    fun `create는 비밀번호를 BCrypt 해시로 저장한다`() {
        val raw = "p@ss-W0rd-local"
        val user =
            User.create(
                name = "Alice",
                email = "alice@flowmeet.local",
                rawPassword = raw,
                encoder = encoder,
            )

        assertThat(user.passwordHash).isNotEqualTo(raw)
        assertThat(user.passwordHash).startsWith("\$2a\$").describedAs("BCrypt hash prefix")
        assertThat(encoder.matches(raw, user.passwordHash)).isTrue()
    }

    @Test
    fun `create는 기본값으로 systemPermission=USER, temporaryPassword=true, active=true를 가진다`() {
        val user =
            User.create(
                name = "Bob",
                email = "bob@flowmeet.local",
                rawPassword = "pw-at-least-8",
                encoder = encoder,
            )

        assertThat(user.systemPermission).isEqualTo(SystemPermission.USER)
        assertThat(user.temporaryPassword).isTrue()
        assertThat(user.active).isTrue()
        assertThat(user.teamId).isNull()
        assertThat(user.roleId).isNull()
        assertThat(user.managerId).isNull()
    }

    @Test
    fun `create는 team_role_manager id와 systemPermission을 선택적으로 지정할 수 있다`() {
        val teamId = java.util.UUID.randomUUID()
        val roleId = java.util.UUID.randomUUID()
        val managerId = java.util.UUID.randomUUID()
        val user =
            User.create(
                name = "Carol",
                email = "carol@flowmeet.local",
                rawPassword = "pw-at-least-8",
                encoder = encoder,
                systemPermission = SystemPermission.ADMIN,
                teamId = teamId,
                roleId = roleId,
                managerId = managerId,
                temporaryPassword = false,
            )

        assertThat(user.systemPermission).isEqualTo(SystemPermission.ADMIN)
        assertThat(user.teamId).isEqualTo(teamId)
        assertThat(user.roleId).isEqualTo(roleId)
        assertThat(user.managerId).isEqualTo(managerId)
        assertThat(user.temporaryPassword).isFalse()
    }
}
