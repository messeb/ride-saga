plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.spring.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}
