package com.messeb.ridesaga.common

import org.apache.avro.specific.SpecificRecord
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

/**
 * Shared Kafka wiring, imported explicitly by every service (`@Import(KafkaCorrelationConfig::class)`):
 * the [EventPublisher] and the correlation-id interceptor on all listener containers.
 */
@Configuration(proxyBeanMethods = false)
class KafkaCorrelationConfig {

    @Bean
    fun eventPublisher(kafkaTemplate: KafkaTemplate<String, SpecificRecord>): EventPublisher = EventPublisher(kafkaTemplate)

    @Bean
    fun correlationContainerCustomizer(): ContainerCustomizer<Any, Any, ConcurrentMessageListenerContainer<Any, Any>> =
        ContainerCustomizer { container ->
            container.setRecordInterceptor(CorrelationRecordInterceptor())
        }
}
