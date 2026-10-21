package com.flowmeet.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "flowmeet.security.refresh-token")
data class RefreshTokenProperties(
    val pepper: String,
    val cookieSecure: Boolean,
) {
    init {
        require(pepper.toByteArray(Charsets.UTF_8).size >= MIN_PEPPER_BYTES) {
            "flowmeet.security.refresh-token.pepper must be at least $MIN_PEPPER_BYTES bytes"
        }
    }

    override fun toString(): String = "RefreshTokenProperties(pepper=***, cookieSecure=$cookieSecure)"

    companion object {
        const val MIN_PEPPER_BYTES = 32
    }
}
