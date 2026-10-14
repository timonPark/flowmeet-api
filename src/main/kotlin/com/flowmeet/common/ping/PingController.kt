package com.flowmeet.common.ping

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ping")
class PingController(
    private val ping: PingService,
) {
    @GetMapping
    fun get(): PingResponse {
        val result = ping()
        return PingResponse(message = result.message, at = result.at.toString())
    }

    data class PingResponse(
        val message: String,
        val at: String,
    )
}
