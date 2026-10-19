package com.flowmeet.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val properties: JwtProperties,
    private val clock: Clock,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))

    fun issueAccessToken(
        subject: String,
        role: String,
    ): String {
        val now = clock.instant()
        val expiry = now.plus(properties.accessTokenTtl)
        return Jwts
            .builder()
            .subject(subject)
            .claim(CLAIM_ROLE, role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(signingKey)
            .clock { Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(token)
            .payload

    fun validate(token: String): Boolean =
        try {
            parse(token)
            true
        } catch (_: JwtException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    companion object {
        const val CLAIM_ROLE = "role"
    }
}
