package com.flowmeet.auth.infrastructure.token

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class RefreshTokenGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    companion object {
        const val TOKEN_BYTES = 32
        private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
