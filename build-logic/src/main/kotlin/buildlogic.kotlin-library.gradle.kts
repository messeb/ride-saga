import io.gitlab.arturbosch.detekt.Detekt
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
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
