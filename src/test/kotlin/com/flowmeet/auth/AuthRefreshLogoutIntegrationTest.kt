package com.flowmeet.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowmeet.auth.domain.User
import com.flowmeet.auth.infrastructure.persistence.RefreshTokenJpaRepository
import com.flowmeet.auth.infrastructure.persistence.UserJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(
    properties = [
        "flowmeet.security.refresh-token.cookie-secure=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
class AuthRefreshLogoutIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var users: UserJpaRepository

    @Autowired
    lateinit var refreshTokens: RefreshTokenJpaRepository

    private val encoder = BCryptPasswordEncoder()
    private val email = "owner@flowmeet.local"
    private val password = "Temp#2026"

    @BeforeEach
    fun resetAndSeedUser() {
        refreshTokens.deleteAllInBatch()
        users.deleteAllInBatch()
        users.saveAndFlush(
            User.create(
                name = "통합 테스트 사용자",
                email = email,
                rawPassword = password,
                encoder = encoder,
            ),
        )
    }

    @Test
    fun `login → refresh → 이전 refresh 재사용 시 AUTH-410 + 전 세션 revoke`() {
        // 1. login
        val loginResult =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","password":"$password"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        val firstCookie = extractRefreshCookie(loginResult.response.getHeader("Set-Cookie"))

        // 2. refresh (회전)
        val rotateResult =
            mockMvc
                .perform(post("/api/auth/refresh").cookie(jakarta.servlet.http.Cookie("refreshToken", firstCookie)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn()
        val secondCookie = extractRefreshCookie(rotateResult.response.getHeader("Set-Cookie"))
        assertThat(secondCookie).isNotEqualTo(firstCookie)

        // 3. 이전 refresh 재사용 → AUTH-410
        mockMvc
            .perform(post("/api/auth/refresh").cookie(jakarta.servlet.http.Cookie("refreshToken", firstCookie)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTH-410"))

        // 4. DB 상태 검증 — 해당 user의 모든 refresh가 revoke됨
        val user = users.findByEmail(email)!!
        val allTokens = refreshTokens.findAll().filter { it.userId == user.id }
        assertThat(allTokens).isNotEmpty
        assertThat(allTokens).allSatisfy { assertThat(it.revokedAt).isNotNull() }
    }

    @Test
    fun `login → logout(Bearer-only, 쿠키 미동반) → 실제 JwtAuthenticationFilter 통과 + 모든 활성 refresh revoke`() {
        // 1. login (쿠키 받고 로컬에 저장만, logout 요청에는 쿠키를 보내지 않는다)
        val loginResult =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","password":"$password"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        val accessToken =
            ObjectMapper()
                .readTree(loginResult.response.contentAsString)
                .get("accessToken")
                .asText()
        assertThat(accessToken).isNotBlank()

        // 2. logout — Bearer 헤더만, 쿠키 미동반.
        //    SecurityConfig는 permitAll 이지만 JwtAuthenticationFilter가 토큰을 파싱해 SecurityContext에 subject를 세팅한다.
        //    컨트롤러의 수동 체크(SecurityContextUtil.currentUserId)로 bearerUserId가 추출되고 전 활성 refresh가 revoke된다.
        mockMvc
            .perform(
                post("/api/auth/logout")
                    .header("Authorization", "Bearer $accessToken"),
            ).andExpect(status().isNoContent)
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))

        val user = users.findByEmail(email)!!
        val tokens = refreshTokens.findAll().filter { it.userId == user.id }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].revokedAt).isNotNull()
    }

    @Test
    fun `login → logout(cookie) → 204 + DB에 revokedAt 세팅 + 쿠키 Max-Age=0`() {
        val loginResult =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","password":"$password"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        val cookie = extractRefreshCookie(loginResult.response.getHeader("Set-Cookie"))

        mockMvc
            .perform(post("/api/auth/logout").cookie(jakarta.servlet.http.Cookie("refreshToken", cookie)))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))

        val user = users.findByEmail(email)!!
        val tokens = refreshTokens.findAll().filter { it.userId == user.id }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].revokedAt).isNotNull()
    }

    private fun extractRefreshCookie(setCookieHeader: String?): String {
        require(!setCookieHeader.isNullOrBlank()) { "Set-Cookie 헤더가 없습니다." }
        val attribute = setCookieHeader.split(";").first().trim()
        require(attribute.startsWith("refreshToken=")) { "refreshToken 쿠키가 아님: $attribute" }
        return attribute.removePrefix("refreshToken=")
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("flowmeet")
                .withUsername("flowmeet")
                .withPassword("local-password")
                .withInitScript("schema.sql")

        @DynamicPropertySource
        @JvmStatic
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
