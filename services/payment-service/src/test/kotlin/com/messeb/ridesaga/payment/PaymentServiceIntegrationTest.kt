package com.messeb.ridesaga.payment

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.PaymentCompleted
import com.messeb.ridesaga.events.PaymentFailed
import com.messeb.ridesaga.events.RideRequested
import com.messeb.ridesaga.payment.domain.PaymentRepository
import com.messeb.ridesaga.payment.domain.PaymentStatus
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Proves the idempotent consumer: a redelivered DriverAssigned (same eventId) results in
 * exactly one charge and exactly one PaymentCompleted. Also covers the decline rule that
 * feeds the saga's compensation path.
 */
@Testcontainers
@SpringBootTest
class PaymentServiceIntegrationTest {

    @Autowired
    private lateinit var payments: PaymentRepository

    @Test
    fun `charges exactly once even when DriverAssigned is delivered twice`() {
        val consumer = subscribe(Topics.PAYMENT_COMPLETED)
        val rideId = UUID.randomUUID().toString()

        publish(Topics.RIDE_REQUESTED, rideId, rideRequested(rideId, fare = "23.50"))
        val driverAssigned = driverAssigned(rideId)
        publish(Topics.DRIVER_ASSIGNED, rideId, driverAssigned)
        // simulate an at-least-once redelivery: same event, same eventId
        publish(Topics.DRIVER_ASSIGNED, rideId, driverAssigned)

        awaitEvent<PaymentCompleted>(consumer) { it.rideId == rideId }

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val payment = payments.findByRideId(rideId)
            assertEquals(PaymentStatus.COMPLETED, payment?.status)
        }
        // no second PaymentCompleted for the duplicate delivery
        val extra = consumer.poll(Duration.ofSeconds(3)).count { (it.value() as? PaymentCompleted)?.rideId == rideId }
        assertEquals(0, extra)
        assertEquals(1, payments.findAll().count { it.rideId == rideId })
        consumer.close()
    }

    @Test
    fun `declines fares at or above the demo threshold`() {
        val consumer = subscribe(Topics.PAYMENT_FAILED)
        val rideId = UUID.randomUUID().toString()

        publish(Topics.RIDE_REQUESTED, rideId, rideRequested(rideId, fare = "500.00"))
        publish(Topics.DRIVER_ASSIGNED, rideId, driverAssigned(rideId))

        val failed = awaitEvent<PaymentFailed>(consumer) { it.rideId == rideId }
        assertEquals(rideId, failed.rideId)

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertEquals(PaymentStatus.FAILED, payments.findByRideId(rideId)?.status)
        }
        consumer.close()
    }

    private fun rideRequested(rideId: String, fare: String): RideRequested = RideRequested.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setOccurredAt(Instant.now())
        .setRideId(rideId)
        .setRiderId("rider-1")
        .setPickupLocation("Alexanderplatz")
        .setDropoffLocation("Kreuzberg")
        .setFareAmount(BigDecimal(fare))
        .setCurrency("EUR")
        .build()

    private fun driverAssigned(rideId: String): DriverAssigned = DriverAssigned.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setOccurredAt(Instant.now())
        .setRideId(rideId)
        .setDriverId("driver-7")
        .setEtaMinutes(4)
        .build()

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

    companion object {
        private const val REGISTRY = "mock://payment-it"

        @Container
        @JvmStatic
        private val kafka = KafkaContainer("apache/kafka:4.1.0")

        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer("postgres:17-alpine")

        @AfterAll
        @JvmStatic
        fun cleanupRegistry() {
            MockSchemaRegistry.dropScope("payment-it")
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
