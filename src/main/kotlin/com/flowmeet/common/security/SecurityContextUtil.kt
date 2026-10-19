package com.flowmeet.common.security

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

object SecurityContextUtil {
    private const val ROLE_PREFIX = "ROLE_"

    fun currentUserId(): String? {
        val auth = authenticatedOrNull() ?: return null
        val principal = auth.principal ?: return null
        return principal.toString().takeIf { it.isNotBlank() }
    }

    fun currentRole(): String? {
        val auth = authenticatedOrNull() ?: return null
        val first = auth.authorities?.firstOrNull()?.authority ?: return null
        return if (first.startsWith(ROLE_PREFIX)) first.removePrefix(ROLE_PREFIX) else first
    }

    private fun authenticatedOrNull(): Authentication? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        if (!auth.isAuthenticated) return null
        if (auth is AnonymousAuthenticationToken) return null
        return auth
    }
}
