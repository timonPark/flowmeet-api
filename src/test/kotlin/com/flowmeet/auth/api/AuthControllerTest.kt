package com.flowmeet.auth.api

import com.flowmeet.auth.api.support.RefreshCookieManager
import com.flowmeet.auth.application.AuthService
import com.flowmeet.auth.application.LoginResult
import com.flowmeet.auth.domain.SystemPermission
import com.flowmeet.auth.domain.UserRole
import com.flowmeet.common.exception.ApiException
import com.flowmeet.common.exception.ErrorCode
import com.flowmeet.common.exception.GlobalExceptionHandler
import com.flowmeet.common.security.JwtProperties
import com.flowmeet.common.security.RefreshTokenProperties
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
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
        // 운영 기본값과 동일하게 secure=true 로 검증한다. 로컬 override는 docker-compose 등에서 처리.
        "flowmeet.security.refresh-token.cookie-secure=true",
        "flowmeet.jwt.secret=test-jwt-secret-at-least-32-bytes-for-unit",
        "flowmeet.jwt.access-token-ttl=PT30M",
        "flowmeet.jwt.refresh-token-ttl=PT336H",
    ],
)
@Import(
    AuthControllerTest.MockBeansConfig::class,
    GlobalExceptionHandler::class,
    RefreshCookieManager::class,
)
class AuthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var authService: AuthService

    @Test
    fun `POST api auth login 성공 시 200과 LoginResponse+Set-Cookie(refreshToken Secure HttpOnly Lax)를 반환한다`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        every { authService.login("admin@flowmeet.local", "Temp#2026") } returns
            LoginResult(
                accessToken = "jwt.access.value",
                accessTokenExpiresInSeconds = 1800L,
                refreshTokenRaw = "opaque-refresh-raw-value",
                user =
                    LoginResult.AuthenticatedUser(
                        id = userId,
                        name = "관리자",
                        email = "admin@flowmeet.local",
                        role = UserRole.ADMIN,
                        systemPermission = SystemPermission.ADMIN,
                        requirePasswordChange = true,
                        profileComplete = true,
                    ),
            )

        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"admin@flowmeet.local","password":"Temp#2026"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("jwt.access.value"))
            .andExpect(jsonPath("$.accessTokenExpiresIn").value(1800))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.id").value(userId.toString()))
            .andExpect(jsonPath("$.user.name").value("관리자"))
            .andExpect(jsonPath("$.user.email").value("admin@flowmeet.local"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.user.systemPermission").value("ADMIN"))
            .andExpect(jsonPath("$.user.requirePasswordChange").value(true))
            .andExpect(jsonPath("$.user.profileComplete").value(true))
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=opaque-refresh-raw-value")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Secure")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=1209600")))
    }

    @Test
    fun `자격 불일치 시 401 AUTH-401를 반환하고 Set-Cookie를 포함하지 않는다`() {
        every { authService.login(any(), any()) } throws ApiException(ErrorCode.INVALID_CREDENTIALS)

        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"admin@flowmeet.local","password":"wrong-pass"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(header().doesNotExist("Set-Cookie"))
    }

    @Test
    fun `이메일 포맷 불량이면 400 COMMON-400과 fieldErrors를 반환한다`() {
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"not-an-email","password":"Temp#2026"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
    }

    @Test
    fun `비밀번호가 8자 미만이면 400 COMMON-400과 fieldErrors를 반환한다`() {
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"admin@flowmeet.local","password":"short"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("COMMON-400"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
    }

    @Test
    fun `LoginRequest toString은 password를 가린다`() {
        val req =
            com.flowmeet.auth.api.dto
                .LoginRequest(email = "sample@flowmeet.local", password = "RawSecret#2026")

        val output = req.toString()

        org.assertj.core.api.Assertions
            .assertThat(output)
            .doesNotContain("RawSecret#2026")
            .contains("password=***")
        // 이메일도 로컬 파트가 가려졌는지 확인
        org.assertj.core.api.Assertions
            .assertThat(output)
            .matches(Regex(".*email=[^,]*\\*+[^,]*@flowmeet\\.local.*").toPattern())
    }

    @TestConfiguration
    class MockBeansConfig {
        @Bean
        fun authService(): AuthService = mockk()
    }
}
