package com.messeb.ridesaga.contracts

import org.apache.avro.Schema
import org.apache.avro.SchemaValidationException
import org.apache.avro.SchemaValidatorBuilder
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail
import java.io.File

/**
 * The CI guardrail for schema evolution: every schema in src/main/avro must stay
 * BACKWARD compatible with its frozen predecessor in src/test/resources/previous-schemas.
 *
 * BACKWARD compatible means a consumer using the *new* schema can read data written
 * with the *old* one — the same rule the Schema Registry enforces at runtime.
 * Allowed: adding fields with defaults, removing fields that have defaults.
 * Forbidden: renames, type changes, adding mandatory fields.
 *
 * When you evolve a schema intentionally, update the frozen copy in the same commit
 * and document the change in docs/events.md.
 */
class SchemaCompatibilityTest {

    private val currentDir = File("src/main/avro")
    private val previousDir = File("src/test/resources/previous-schemas")

    @TestFactory
    fun `current schemas can read data written with previous schemas`(): List<DynamicTest> {
        val validator = SchemaValidatorBuilder().canReadStrategy().validateAll()
        val currentSchemas = currentDir.listFiles { f -> f.extension == "avsc" }.orEmpty()
        check(currentSchemas.isNotEmpty()) { "no schemas found in $currentDir" }

        return currentSchemas.map { schemaFile ->
            DynamicTest.dynamicTest(schemaFile.name) {
                val previousFile = File(previousDir, schemaFile.name)
                check(previousFile.exists()) {
                    "missing frozen predecessor for ${schemaFile.name} — copy the schema to $previousDir"
                }
                val current = Schema.Parser().parse(schemaFile)
                val previous = Schema.Parser().parse(previousFile)
                try {
                    validator.validate(current, listOf(previous))
                } catch (e: SchemaValidationException) {
                    fail("${schemaFile.name} is not BACKWARD compatible with its previous version", e)
                }
            }
        }
    }

    @TestFactory
    fun `no schema was deleted`(): List<DynamicTest> {
        val previousSchemas = previousDir.listFiles { f -> f.extension == "avsc" }.orEmpty()
        return previousSchemas.map { previousFile ->
            DynamicTest.dynamicTest(previousFile.name) {
                check(File(currentDir, previousFile.name).exists()) {
                    "${previousFile.name} was removed — deleting event types breaks consumers"
                }
            }
        }
    }
}
