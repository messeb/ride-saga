package com.messeb.ridesaga.dispatch.config

import com.messeb.ridesaga.common.KafkaCorrelationConfig
import com.messeb.ridesaga.common.Topics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.kafka.config.TopicBuilder

@Configuration(proxyBeanMethods = false)
@Import(KafkaCorrelationConfig::class)
class KafkaConfig {

    @Bean
    fun driverAssignedTopic(): NewTopic = topic(Topics.DRIVER_ASSIGNED)

    @Bean
    fun driverAssignmentFailedTopic(): NewTopic = topic(Topics.DRIVER_ASSIGNMENT_FAILED)

    private fun topic(name: String): NewTopic = TopicBuilder.name(name).partitions(Topics.PARTITIONS).replicas(1).build()
}
