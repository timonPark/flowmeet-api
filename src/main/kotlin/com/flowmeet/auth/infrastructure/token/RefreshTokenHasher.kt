package com.flowmeet.auth.infrastructure.token

import com.flowmeet.common.security.RefreshTokenProperties
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class RefreshTokenHasher(
    properties: RefreshTokenProperties,
) {
    private val keySpec: SecretKeySpec =
        SecretKeySpec(properties.pepper.toByteArray(Charsets.UTF_8), ALGORITHM)

    fun hash(raw: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keySpec)
        val digest = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(digest)
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
