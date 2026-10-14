package com.flowmeet.common.ping

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * 하네스 샘플 서비스. 외부 의존성(Clock)을 주입받아 MockK 테스트 패턴 시연.
 */
@Service
class PingService(
    private val clock: Clock,
) {
    operator fun invoke(): PingResult = PingResult(message = "pong", at = Instant.now(clock))

    data class PingResult(
        val message: String,
        val at: Instant,
    )
}
