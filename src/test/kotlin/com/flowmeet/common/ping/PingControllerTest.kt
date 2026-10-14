package com.flowmeet.common.ping

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Controller 샘플 — @WebMvcTest + MockMvc. Service는 @TestConfiguration으로 MockK 인스턴스 교체.
 * Spring Security filter는 비활성화(addFilters = false) — Security 설정은 별도 이슈.
 */
@WebMvcTest(controllers = [PingController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(PingControllerTest.MockBeansConfig::class)
class PingControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var pingService: PingService

    @Test
    fun `GET api ping 호출 시 Service 결과를 응답으로 변환한다`() {
        val at = Instant.parse("2026-10-14T07:00:00Z")
        every { pingService.invoke() } returns PingService.PingResult(message = "pong", at = at)

        mockMvc
            .perform(get("/api/ping"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("pong"))
            .andExpect(jsonPath("$.at").value(at.toString()))
    }

    @TestConfiguration
    class MockBeansConfig {
        @Bean
        fun pingService(): PingService = mockk()
    }
}
