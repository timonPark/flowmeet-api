package com.flowmeet.auth.api.dto

import com.flowmeet.auth.application.LoginResult
import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.UserRole
import java.util.UUID

data class LoginResponse(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val tokenType: String,
    val user: AuthUserDto,
) {
    data class AuthUserDto(
        val id: UUID,
        val name: String,
        val email: String,
        val role: UserRole,
        val systemPermission: SystemPermission,
        val requirePasswordChange: Boolean,
        val profileComplete: Boolean,
    )

    companion object {
        private const val TOKEN_TYPE_BEARER = "Bearer"

        fun from(result: LoginResult): LoginResponse =
            LoginResponse(
                accessToken = result.accessToken,
                accessTokenExpiresIn = result.accessTokenExpiresInSeconds,
                tokenType = TOKEN_TYPE_BEARER,
                user =
                    AuthUserDto(
                        id = result.user.id,
                        name = result.user.name,
                        email = result.user.email,
                        role = result.user.role,
                        systemPermission = result.user.systemPermission,
                        requirePasswordChange = result.user.requirePasswordChange,
                        profileComplete = result.user.profileComplete,
                    ),
            )
    }
}
