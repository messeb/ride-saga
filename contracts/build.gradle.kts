plugins {
    id("buildlogic.kotlin-library")
    `java-library`
    alias(libs.plugins.avro)
}

dependencies {
    api(libs.avro)
}
