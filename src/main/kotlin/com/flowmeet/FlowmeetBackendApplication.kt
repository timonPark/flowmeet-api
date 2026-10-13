package com.flowmeet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlowmeetBackendApplication

fun main(args: Array<String>) {
    runApplication<FlowmeetBackendApplication>(*args)
}
