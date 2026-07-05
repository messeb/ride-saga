package com.messeb.ridesaga.dispatch.domain

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dispatch")
data class DispatchProperties(val driverPoolSize: Int = 5, val etaMinutes: Int = 4)
