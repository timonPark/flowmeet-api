package com.flowmeet.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "flowmeet.security.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
    val allowedMethods: List<String>,
    val allowedHeaders: List<String>,
    val allowCredentials: Boolean,
) {
    init {
        require(allowedOrigins.isNotEmpty()) { "flowmeet.security.cors.allowedOrigins must not be empty" }
        require(allowedMethods.isNotEmpty()) { "flowmeet.security.cors.allowedMethods must not be empty" }
    }
}
