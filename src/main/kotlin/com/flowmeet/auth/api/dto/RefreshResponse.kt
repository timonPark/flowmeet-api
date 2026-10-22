package com.flowmeet.auth.api.dto

import com.flowmeet.auth.application.RefreshRotationResult

data class RefreshResponse(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val tokenType: String,
) {
    companion object {
        private const val TOKEN_TYPE_BEARER = "Bearer"

        fun from(result: RefreshRotationResult): RefreshResponse =
            RefreshResponse(
                accessToken = result.accessToken,
                accessTokenExpiresIn = result.accessTokenExpiresInSeconds,
                tokenType = TOKEN_TYPE_BEARER,
            )
    }
}
