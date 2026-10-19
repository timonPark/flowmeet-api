package com.flowmeet.common.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "flowmeet.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
) {
    init {
        require(secret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "flowmeet.jwt.secret must be at least $MIN_SECRET_BYTES bytes (HS256 requirement)"
        }
        require(!accessTokenTtl.isZero && !accessTokenTtl.isNegative) {
            "flowmeet.jwt.accessTokenTtl must be positive"
        }
        require(!refreshTokenTtl.isZero && !refreshTokenTtl.isNegative) {
            "flowmeet.jwt.refreshTokenTtl must be positive"
        }
    }

    companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
