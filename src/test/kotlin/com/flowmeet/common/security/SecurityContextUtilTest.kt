package com.flowmeet.common.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class SecurityContextUtilTest {
    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `Authentication이 없으면 currentUserId와 currentRole은 null`() {
        assertThat(SecurityContextUtil.currentUserId()).isNull()
        assertThat(SecurityContextUtil.currentRole()).isNull()
    }

    @Test
    fun `Authentication이 세팅되면 principal과 role을 반환한다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "user-42",
                null,
                listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
            )

        assertThat(SecurityContextUtil.currentUserId()).isEqualTo("user-42")
        assertThat(SecurityContextUtil.currentRole()).isEqualTo("ADMIN")
    }

    @Test
    fun `authority 접두사 ROLE_가 없으면 그대로 반환한다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "user-9",
                null,
                listOf(SimpleGrantedAuthority("TEAM_LEADER")),
            )

        assertThat(SecurityContextUtil.currentRole()).isEqualTo("TEAM_LEADER")
    }
}
