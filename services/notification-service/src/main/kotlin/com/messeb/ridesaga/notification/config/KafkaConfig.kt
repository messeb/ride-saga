package com.messeb.ridesaga.notification.config

import com.messeb.ridesaga.common.KafkaCorrelationConfig
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/** notification-service produces nothing — it only needs the shared consumer wiring. */
@Configuration(proxyBeanMethods = false)
@Import(KafkaCorrelationConfig::class)
class KafkaConfig
