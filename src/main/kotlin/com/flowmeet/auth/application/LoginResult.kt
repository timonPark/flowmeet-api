package com.flowmeet.auth.application

import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.UserRole
import com.flowmeet.common.logging.SensitiveDataMasker
import java.util.UUID

data class LoginResult(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenRaw: String,
    val user: AuthenticatedUser,
) {
    override fun toString(): String =
        "LoginResult(accessToken=${SensitiveDataMasker.token(accessToken)}, " +
            "accessTokenExpiresInSeconds=$accessTokenExpiresInSeconds, " +
            "refreshTokenRaw=***, user=$user)"

    data class AuthenticatedUser(
        val id: UUID,
        val name: String,
        val email: String,
        val role: UserRole,
        val systemPermission: SystemPermission,
        val requirePasswordChange: Boolean,
        val profileComplete: Boolean,
    ) {
        override fun toString(): String =
            "AuthenticatedUser(id=$id, name=$name, " +
                "email=${SensitiveDataMasker.email(email)}, " +
                "role=$role, systemPermission=$systemPermission, " +
                "requirePasswordChange=$requirePasswordChange, profileComplete=$profileComplete)"
    }
}
