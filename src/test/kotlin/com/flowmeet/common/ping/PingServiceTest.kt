package com.flowmeet.common.ping

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Service 샘플 — Spring 컨텍스트 없이 순수 단위 테스트. MockK로 Clock 모킹.
 */
class PingServiceTest {
    @Test
    fun `pong 메시지와 Clock에서 받은 시각을 반환한다`() {
        val fixed = Instant.parse("2026-10-14T07:00:00Z")
        val clock = mockk<Clock>()
        every { clock.instant() } returns fixed
        every { clock.zone } returns ZoneOffset.UTC

        val result = PingService(clock)()

        assertEquals("pong", result.message)
        assertEquals(fixed, result.at)
    }
}
