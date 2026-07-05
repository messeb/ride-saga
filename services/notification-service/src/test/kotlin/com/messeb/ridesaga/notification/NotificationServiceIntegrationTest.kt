package com.messeb.ridesaga.notification

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.RideConfirmed
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.micrometer.core.instrument.MeterRegistry
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
class NotificationServiceIntegrationTest {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `notifies the rider when the ride is confirmed`() {
        val rideId = UUID.randomUUID().toString()
        val event = RideConfirmed.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.now())
            .setRideId(rideId)
            .setDriverId("driver-7")
            .setConfirmedAt(Instant.now())
            .build()

        KafkaProducer<String, SpecificRecord>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                "schema.registry.url" to REGISTRY,
            ),
        ).use { it.send(ProducerRecord(Topics.RIDE_CONFIRMED, rideId, event)).get() }

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertEquals(1.0, meterRegistry.counter("notifications_sent_total").count())
        }
    }

    companion object {
        private const val REGISTRY = "mock://notification-it"

        @Container
        @JvmStatic
        private val kafka = KafkaContainer("apache/kafka:4.1.0")

        @AfterAll
        @JvmStatic
        fun cleanupRegistry() {
            MockSchemaRegistry.dropScope("notification-it")
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.kafka.properties.schema.registry.url") { REGISTRY }
            registry.add("management.tracing.enabled") { false }
        }
    }
}
