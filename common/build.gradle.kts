plugins {
    id("buildlogic.kotlin-library")
    `java-library`
}

dependencies {
    api(project(":contracts"))
    api("org.springframework.kafka:spring-kafka")
    api(libs.kafka.avro.serializer) {
        // Confluent ships its own kafka-clients fork (…-ccs); use the Boot-managed Apache client instead
        exclude(group = "org.apache.kafka", module = "kafka-clients")
    }
    implementation("org.slf4j:slf4j-api")
    implementation("io.micrometer:micrometer-core")

    testImplementation(libs.mockk)
    // real MDC adapter in tests — without a binding slf4j falls back to a no-op MDC
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
