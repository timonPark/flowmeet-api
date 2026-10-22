package com.flowmeet.auth.application

import com.flowmeet.common.logging.SensitiveDataMasker

data class RefreshRotationResult(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenRaw: String,
) {
    override fun toString(): String =
        "RefreshRotationResult(accessToken=${SensitiveDataMasker.token(accessToken)}, " +
            "accessTokenExpiresInSeconds=$accessTokenExpiresInSeconds, " +
            "refreshTokenRaw=***)"
}
