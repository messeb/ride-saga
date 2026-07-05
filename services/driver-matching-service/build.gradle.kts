plugins {
    id("buildlogic.spring-boot-service")
}

dependencies {
    implementation(project(":common"))

    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
}
