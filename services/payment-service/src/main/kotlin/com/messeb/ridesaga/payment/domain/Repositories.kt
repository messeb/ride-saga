package com.messeb.ridesaga.payment.domain

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, String> {
    fun findByRideId(rideId: String): Payment?
}

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String>
