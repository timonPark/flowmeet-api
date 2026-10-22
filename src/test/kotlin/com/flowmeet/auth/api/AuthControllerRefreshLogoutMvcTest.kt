package com.flowmeet.auth.api

import com.flowmeet.auth.api.support.RefreshCookieManager
import com.flowmeet.auth.application.AuthService
import com.flowmeet.auth.application.RefreshRotationResult
import com.flowmeet.common.exception.ApiException
import com.flowmeet.common.exception.ErrorCode
import com.flowmeet.common.exception.GlobalExceptionHandler
import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.RefreshTokenProperties
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(controllers = [AuthController::class])
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(RefreshTokenProperties::class, JwtProperties::class)
@TestPropertySource(
    properties = [
        "flowmeet.security.refresh-token.pepper=test-refresh-pepper-at-least-32-bytes-dev",
        "flowmeet.security.refresh-token.cookie-secure=true",
        "flowmeet.jwt.secret=test-jwt-secret-at-least-32-bytes-for-unit",
        "flowmeet.jwt.access-token-ttl=PT30M",
        "flowmeet.jwt.refresh-token-ttl=PT336H",
    ],
)
@Import(
    AuthControllerRefreshLogoutMvcTest.MockBeansConfig::class,
    GlobalExceptionHandler::class,
    RefreshCookieManager::class,
)
class AuthControllerRefreshLogoutMvcTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var authService: AuthService

    @BeforeEach
    fun resetMockState() {
        clearMocks(authService)
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `POST api auth refresh 성공 시 200 RefreshResponse + 신규 Set-Cookie 반환 (user 미포함)`() {
        every { authService.rotate("old-refresh-raw") } returns
            RefreshRotationResult(
                accessToken = "new.access.jwt",
                accessTokenExpiresInSeconds = 1800L,
                refreshTokenRaw = "new-refresh-raw",
            )

        mockMvc
            .perform(post("/api/auth/refresh").cookie(Cookie("refreshToken", "old-refresh-raw")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new.access.jwt"))
            .andExpect(jsonPath("$.accessTokenExpiresIn").value(1800))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh-raw")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Secure")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=1209600")))
    }

    @Test
    fun `POST api auth refresh 쿠키 없음 시 401 COMMON-401를 반환하고 Set-Cookie 미포함`() {
        mockMvc
            .perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("COMMON-401"))
            .andExpect(header().doesNotExist("Set-Cookie"))

        verify(exactly = 0) { authService.rotate(any()) }
    }

    @Test
    fun `POST api auth refresh 재사용 감지 시 401 AUTH-410를 반환한다`() {
        every { authService.rotate("stale-raw") } throws ApiException(ErrorCode.REFRESH_TOKEN_REVOKED)

        mockMvc
            .perform(post("/api/auth/refresh").cookie(Cookie("refreshToken", "stale-raw")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTH-410"))
            .andExpect(jsonPath("$.message").value("세션이 종료되었습니다. 다시 로그인해 주세요."))
    }

    @Test
    fun `POST api auth refresh 만료 시 401 AUTH-401-001을 반환한다 (M-02 트리거)`() {
        every { authService.rotate("expired-raw") } throws ApiException(ErrorCode.EXPIRED_TOKEN)

        mockMvc
            .perform(post("/api/auth/refresh").cookie(Cookie("refreshToken", "expired-raw")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTH-401-001"))
    }

    @Test
    fun `POST api auth logout 쿠키 있으면 204 반환하고 Max-Age=0 Set-Cookie로 쿠키 삭제`() {
        every { authService.logout(rawRefresh = "cookie-raw", bearerUserId = null) } just Runs

        mockMvc
            .perform(post("/api/auth/logout").cookie(Cookie("refreshToken", "cookie-raw")))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))

        verify(exactly = 1) { authService.logout(rawRefresh = "cookie-raw", bearerUserId = null) }
    }

    @Test
    fun `POST api auth logout Bearer userId만 있으면 그 user의 모든 활성 refresh를 revoke하도록 위임한다`() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
            )
        every { authService.logout(rawRefresh = null, bearerUserId = userId) } just Runs

        mockMvc
            .perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))

        verify(exactly = 1) { authService.logout(rawRefresh = null, bearerUserId = userId) }
    }

    @Test
    fun `POST api auth logout 쿠키와 Bearer가 모두 없으면 401 COMMON-401`() {
        every { authService.logout(rawRefresh = null, bearerUserId = null) } throws
            ApiException(ErrorCode.UNAUTHORIZED)

        mockMvc
            .perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("COMMON-401"))
    }

    @Test
    fun `쿠키 매니저 설정 — HttpOnly Secure SameSite Lax Path 모두 반영 확인 (login과 일치)`() {
        // 매니저가 세 엔드포인트에서 동일 속성을 쓰도록 보장하기 위한 회귀 테스트.
        every { authService.rotate("x") } returns
            RefreshRotationResult("a", 1800L, "y")

        val result =
            mockMvc
                .perform(post("/api/auth/refresh").cookie(Cookie("refreshToken", "x")))
                .andReturn()

        val setCookie = result.response.getHeader("Set-Cookie") ?: ""
        assertThat(setCookie)
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Lax")
            .contains("Path=/api/auth")
    }

    @TestConfiguration
    class MockBeansConfig {
        @Bean
        fun authService(): AuthService = mockk()
    }
}
