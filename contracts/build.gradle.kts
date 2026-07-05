plugins {
    id("buildlogic.kotlin-library")
    `java-library`
    alias(libs.plugins.avro)
}

avro {
    stringType.set("String")
    setEnableDecimalLogicalType(true)
    fieldVisibility.set("PRIVATE")
}

dependencies {
    api(libs.avro)
}
