package com.flowmeet.common.security

import com.flowmeet.common.ping.PingService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Slice test — WebMvc + Security 필터 체인 활성화 (addFilters 기본 true).
 * permitAll 상태에서 PingController가 정상 응답하고, CORS preflight가 허용되는지 확인.
 */
@WebMvcTest(controllers = [com.flowmeet.common.ping.PingController::class])
@Import(SecurityConfig::class, JwtTokenProvider::class, SecurityConfigTest.TestBeans::class)
@TestPropertySource(
    properties = [
        "flowmeet.jwt.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "flowmeet.jwt.access-token-ttl=PT30M",
        "flowmeet.jwt.refresh-token-ttl=P14D",
        "flowmeet.security.cors.allowed-origins=http://localhost:3000",
        "flowmeet.security.cors.allowed-methods=GET,POST,OPTIONS",
        "flowmeet.security.cors.allowed-headers=Authorization,Content-Type",
        "flowmeet.security.cors.allow-credentials=true",
    ],
)
@AutoConfigureMockMvc
class SecurityConfigTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var pingService: PingService

    @Test
    fun `permitAll 상태에서 GET api ping은 200을 반환한다`() {
        every { pingService.invoke() } returns
            PingService.PingResult(
                message = "pong",
                at = Instant.parse("2026-10-19T07:00:00Z"),
            )

        mockMvc
            .perform(get("/api/ping"))
            .andExpect(status().isOk)
    }

    @Test
    fun `CORS preflight OPTIONS는 허용 origin에 대해 200을 반환하고 헤더가 포함된다`() {
        mockMvc
            .perform(
                options("/api/ping")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Authorization"),
            ).andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    fun `허용되지 않은 origin의 preflight는 403을 반환한다`() {
        mockMvc
            .perform(
                options("/api/ping")
                    .header("Origin", "http://evil.example.com")
                    .header("Access-Control-Request-Method", "GET"),
            ).andExpect(status().isForbidden)
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        fun pingService(): PingService = mockk()

        @Bean
        fun clock(): java.time.Clock = java.time.Clock.systemUTC()
    }
}
