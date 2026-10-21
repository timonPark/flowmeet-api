package com.flowmeet.auth.api

import com.flowmeet.auth.api.dto.LoginRequest
import com.flowmeet.auth.api.dto.LoginResponse
import com.flowmeet.auth.application.AuthService
import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.RefreshTokenProperties
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshTokenProperties: RefreshTokenProperties,
    private val jwtProperties: JwtProperties,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        val result = authService.login(email = request.email, rawPassword = request.password)
        val cookie =
            ResponseCookie
                .from(REFRESH_COOKIE_NAME, result.refreshTokenRaw)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(jwtProperties.refreshTokenTtl)
                .build()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(LoginResponse.from(result))
    }

    companion object {
        private const val REFRESH_COOKIE_NAME = "refreshToken"
        private const val REFRESH_COOKIE_PATH = "/api/auth"
    }
}
