package com.messeb.ridesaga.booking.config

import com.messeb.ridesaga.common.KafkaCorrelationConfig
import com.messeb.ridesaga.common.Topics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.kafka.config.TopicBuilder

/**
 * booking-service declares the topics it produces to; every producing service owns its
 * own topics the same way.
 */
@Configuration(proxyBeanMethods = false)
@Import(KafkaCorrelationConfig::class)
class KafkaConfig {

    @Bean
    fun rideRequestedTopic(): NewTopic = topic(Topics.RIDE_REQUESTED)

    @Bean
    fun rideConfirmedTopic(): NewTopic = topic(Topics.RIDE_CONFIRMED)

    @Bean
    fun rideCancelledTopic(): NewTopic = topic(Topics.RIDE_CANCELLED)

    private fun topic(name: String): NewTopic = TopicBuilder.name(name).partitions(Topics.PARTITIONS).replicas(1).build()
}
