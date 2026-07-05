plugins {
    id("buildlogic.spring-boot-service")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.postgresql)
}
