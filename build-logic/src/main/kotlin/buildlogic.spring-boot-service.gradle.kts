plugins {
    id("buildlogic.kotlin-library")
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

dependencies {
    // every service serves /actuator/health + /actuator/prometheus over HTTP
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Boot 4 modular tracing: autoconfiguration module + the actual bridge and exporter libs
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

tasks.bootJar {
    // stable name so every service Dockerfile can COPY the same path
    archiveFileName.set("app.jar")
}
