package com.messeb.ridesaga.booking

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.PaymentCompleted
import com.messeb.ridesaga.events.RideConfirmed
import com.messeb.ridesaga.events.RideRequested
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Proves booking-service's consume→produce contract against a real Kafka broker:
 * the REST request emits RideRequested, and the DriverAssigned + PaymentCompleted
 * responses drive the ride to CONFIRMED with a RideConfirmed event.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingServiceIntegrationTest {

    @LocalServerPort
    private var port = 0

    private val rest: RestClient by lazy { RestClient.create("http://localhost:$port") }

    @Test
    fun `full booking saga from the booking perspective`() {
        val consumer = subscribe(Topics.RIDE_REQUESTED, Topics.RIDE_CONFIRMED)

        // 1. rider requests a ride → 201 + RideRequested on the wire
        val response = rest.post()
            .uri("/api/rides")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "riderId" to "rider-1",
                    "pickupLocation" to "Alexanderplatz",
                    "dropoffLocation" to "Kreuzberg",
                    "fareAmount" to 23.50,
                    "currency" to "EUR",
                ),
            )
            .retrieve()
            .toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val rideId = response.body?.get("rideId") as String

        val requested = awaitEvent<RideRequested>(consumer) { it.rideId == rideId }
        assertEquals("rider-1", requested.riderId)

        // 2. the other services answer → booking confirms the ride
        publish(
            Topics.DRIVER_ASSIGNED,
            rideId,
            DriverAssigned.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setRideId(rideId)
                .setDriverId("driver-7")
                .setEtaMinutes(4)
                .build(),
        )
        publish(
            Topics.PAYMENT_COMPLETED,
            rideId,
            PaymentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setRideId(rideId)
                .setPaymentId("payment-1")
                .setAmount(java.math.BigDecimal("23.50"))
                .setCurrency("EUR")
                .build(),
        )

        val confirmed = awaitEvent<RideConfirmed>(consumer) { it.rideId == rideId }
        assertEquals("driver-7", confirmed.driverId)

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val ride = rest.get().uri("/api/rides/$rideId").retrieve().toEntity(Map::class.java)
            assertEquals("CONFIRMED", ride.body?.get("status"))
            assertEquals("driver-7", ride.body?.get("driverId"))
        }
        consumer.close()
    }

    private fun subscribe(vararg topics: String): KafkaConsumer<String, Any> {
        val consumer = KafkaConsumer<String, Any>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "it-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                "schema.registry.url" to REGISTRY,
                "specific.avro.reader" to true,
            ),
        )
        consumer.subscribe(topics.toList())
        return consumer
    }

    private inline fun <reified T : SpecificRecord> awaitEvent(
        consumer: KafkaConsumer<String, Any>,
        crossinline predicate: (T) -> Boolean,
    ): T {
        val deadline = Instant.now().plusSeconds(120)
        while (Instant.now().isBefore(deadline)) {
            val match = consumer.poll(Duration.ofMillis(500)).mapNotNull { it.value() as? T }.firstOrNull { predicate(it) }
            if (match != null) return match
        }
        error("no matching ${T::class.simpleName} event received within 120s")
    }

    private fun publish(topic: String, key: String, event: SpecificRecord) {
        KafkaProducer<String, SpecificRecord>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                "schema.registry.url" to REGISTRY,
            ),
        ).use { it.send(ProducerRecord(topic, key, event)).get() }
    }

    companion object {
        // one mock registry scope shared by app, test producer and test consumer (same JVM)
        private const val REGISTRY = "mock://booking-it"

        @Container
        @JvmStatic
        private val kafka = KafkaContainer("apache/kafka:4.1.0")

        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer("postgres:17-alpine")

        @AfterAll
        @JvmStatic
        fun cleanupRegistry() {
            io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry.dropScope("booking-it")
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.kafka.properties.schema.registry.url") { REGISTRY }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("management.tracing.enabled") { false }
        }
    }
}
