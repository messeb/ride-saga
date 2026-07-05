import io.gitlab.arturbosch.detekt.Detekt
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

// Each service's test suite boots Testcontainers Kafka + a Spring context. Running all
// of them at once starves small machines (CI runners) and the saga tests time out —
// cap concurrent test tasks build-wide instead of inflating test timeouts.
abstract class TestSuiteLimiter : BuildService<BuildServiceParameters.None>

val testSuiteLimiter = gradle.sharedServices.registerIfAbsent("testSuiteLimiter", TestSuiteLimiter::class) {
    maxParallelUsages.set(2)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    usesService(testSuiteLimiter)
    testLogging {
        events("failed", "skipped")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    // generated Avro sources are Java and irrelevant for Kotlin static analysis
    exclude("**/generated/**")
}
