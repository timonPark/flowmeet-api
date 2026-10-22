package com.flowmeet.auth.api.support

import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.RefreshTokenProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Refresh 쿠키 조립·삭제의 단일 출처.
 *
 * HttpOnly / Secure / SameSite=Lax / Path=/api/auth 는 모든 응답에서 동일하며, Secure 토글은
 * [RefreshTokenProperties.cookieSecure], Max-Age 는 [JwtProperties.refreshTokenTtl] 를 따른다.
 * 세 곳(login, refresh, logout)에서 쿠키 속성 drift가 발생하지 않도록 Controller는
 * 반드시 이 컴포넌트를 통해 쿠키를 조립한다.
 */
@Component
class RefreshCookieManager(
    private val jwtProperties: JwtProperties,
    private val refreshTokenProperties: RefreshTokenProperties,
) {
    fun build(rawRefresh: String): ResponseCookie =
        baseBuilder(rawRefresh)
            .maxAge(jwtProperties.refreshTokenTtl)
            .build()

    fun clear(): ResponseCookie =
        baseBuilder("")
            .maxAge(Duration.ZERO)
            .build()

    private fun baseBuilder(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie
            .from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(refreshTokenProperties.cookieSecure)
            .sameSite("Lax")
            .path(COOKIE_PATH)

    companion object {
        const val COOKIE_NAME = "refreshToken"
        const val COOKIE_PATH = "/api/auth"
    }
}
