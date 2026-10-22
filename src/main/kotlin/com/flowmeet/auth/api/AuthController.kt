package com.flowmeet.auth.api

import com.flowmeet.auth.api.dto.LoginRequest
import com.flowmeet.auth.api.dto.LoginResponse
import com.flowmeet.auth.api.dto.RefreshResponse
import com.flowmeet.auth.api.support.RefreshCookieManager
import com.flowmeet.auth.application.AuthService
import com.flowmeet.common.exception.ApiException
import com.flowmeet.common.exception.ErrorCode
import com.flowmeet.common.security.SecurityContextUtil
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshCookieManager: RefreshCookieManager,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        val result = authService.login(email = request.email, rawPassword = request.password)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookieManager.build(result.refreshTokenRaw).toString())
            .body(LoginResponse.from(result))
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = RefreshCookieManager.COOKIE_NAME, required = false) rawRefresh: String?,
    ): ResponseEntity<RefreshResponse> {
        if (rawRefresh.isNullOrBlank()) {
            throw ApiException(ErrorCode.UNAUTHORIZED)
        }
        val result = authService.rotate(rawRefresh)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookieManager.build(result.refreshTokenRaw).toString())
            .body(RefreshResponse.from(result))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = RefreshCookieManager.COOKIE_NAME, required = false) rawRefresh: String?,
    ): ResponseEntity<Void> {
        val bearerUserId = currentBearerUserIdOrNull()
        authService.logout(rawRefresh = rawRefresh?.takeIf { it.isNotBlank() }, bearerUserId = bearerUserId)
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieManager.clear().toString())
            .build()
    }

    private fun currentBearerUserIdOrNull(): UUID? {
        val subject = SecurityContextUtil.currentUserId() ?: return null
        return runCatching { UUID.fromString(subject) }.getOrNull()
    }
}
