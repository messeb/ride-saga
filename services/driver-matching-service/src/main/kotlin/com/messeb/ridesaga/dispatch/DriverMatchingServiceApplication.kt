package com.messeb.ridesaga.dispatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DriverMatchingServiceApplication

fun main(args: Array<String>) {
    runApplication<DriverMatchingServiceApplication>(*args)
}
