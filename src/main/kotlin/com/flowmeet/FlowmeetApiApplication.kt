package com.flowmeet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlowmeetApiApplication

fun main(args: Array<String>) {
    runApplication<FlowmeetApiApplication>(*args)
}
