package com.messeb.ridesaga.dispatch

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.RideRequested
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Proves the two failure-handling behaviors against a real broker:
 * a healthy request gets a driver, and a poison message travels through the retry
 * topics into the dead-letter topic without blocking the main topic.
 */
@Testcontainers
@SpringBootTest
class DriverMatchingIntegrationTest {

    @Test
    fun `assigns a driver to a healthy ride request`() {
        val consumer = subscribe(Topics.DRIVER_ASSIGNED)
        val rideId = UUID.randomUUID().toString()

        publish(rideRequested(rideId, pickup = "Alexanderplatz"))

        val assigned = awaitEvent<DriverAssigned>(consumer) { it.rideId == rideId }
        assertEquals(rideId, assigned.rideId)
        consumer.close()
    }

    @Test
    fun `poison message exhausts retries and lands in the DLT while healthy traffic continues`() {
        val dltConsumer = subscribe("${Topics.RIDE_REQUESTED}.dlt")
        val assignedConsumer = subscribe(Topics.DRIVER_ASSIGNED)
        val poisonRideId = UUID.randomUUID().toString()
        val healthyRideId = UUID.randomUUID().toString()

        publish(rideRequested(poisonRideId, pickup = "POISON"))
        publish(rideRequested(healthyRideId, pickup = "Ostbahnhof"))

        // healthy message is processed although the poison one is still failing/retrying
        awaitEvent<DriverAssigned>(assignedConsumer) { it.rideId == healthyRideId }

        // 4 attempts with exponential backoff, then dead-lettered
        val deadLettered = awaitEvent<RideRequested>(dltConsumer, timeoutSeconds = 60) { it.rideId == poisonRideId }
        assertEquals("POISON", deadLettered.pickupLocation)

        dltConsumer.close()
        assignedConsumer.close()
    }

    private fun rideRequested(rideId: String, pickup: String): RideRequested = RideRequested.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setOccurredAt(Instant.now())
        .setRideId(rideId)
        .setRiderId("rider-1")
        .setPickupLocation(pickup)
        .setDropoffLocation("Kreuzberg")
        .setFareAmount(BigDecimal("23.50"))
        .setCurrency("EUR")
        .build()

    private fun publish(event: RideRequested) {
        KafkaProducer<String, SpecificRecord>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                "schema.registry.url" to REGISTRY,
            ),
        ).use { it.send(ProducerRecord(Topics.RIDE_REQUESTED, event.rideId, event)).get() }
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
        timeoutSeconds: Long = 30,
        crossinline predicate: (T) -> Boolean,
    ): T {
        val deadline = Instant.now().plusSeconds(timeoutSeconds)
        while (Instant.now().isBefore(deadline)) {
            val match = consumer.poll(Duration.ofMillis(500)).mapNotNull { it.value() as? T }.firstOrNull { predicate(it) }
            if (match != null) return match
        }
        error("no matching ${T::class.simpleName} event received within ${timeoutSeconds}s")
    }

    companion object {
        private const val REGISTRY = "mock://dispatch-it"

        @Container
        @JvmStatic
        private val kafka = KafkaContainer("apache/kafka:4.1.0")

        @AfterAll
        @JvmStatic
        fun cleanupRegistry() {
            MockSchemaRegistry.dropScope("dispatch-it")
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
