package com.messeb.ridesaga.dispatch.domain

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DriverPoolTest {

    private fun pool(size: Int) = DriverPool(DispatchProperties(driverPoolSize = size), SimpleMeterRegistry())

    @Test
    fun `assigns distinct drivers until the pool is exhausted`() {
        val pool = pool(2)

        val first = pool.assign("ride-1")
        val second = pool.assign("ride-2")

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(setOf("driver-1", "driver-2"), setOf(first, second))
        assertNull(pool.assign("ride-3"))
    }

    @Test
    fun `duplicate assignment for the same ride returns the same driver (idempotent)`() {
        val pool = pool(2)

        val first = pool.assign("ride-1")
        val duplicate = pool.assign("ride-1")

        assertEquals(first, duplicate)
        assertEquals(1, pool.availableCount())
    }

    @Test
    fun `releasing a cancelled ride returns the driver to the pool`() {
        val pool = pool(1)
        pool.assign("ride-1")
        assertNull(pool.assign("ride-2"))

        pool.release("ride-1")

        assertEquals(1, pool.availableCount())
        assertNotNull(pool.assign("ride-3"))
    }

    @Test
    fun `releasing an unknown ride is a no-op`() {
        val pool = pool(1)

        pool.release("ride-never-assigned")

        assertEquals(1, pool.availableCount())
    }
}
