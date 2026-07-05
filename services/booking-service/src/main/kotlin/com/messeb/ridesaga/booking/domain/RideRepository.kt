package com.messeb.ridesaga.booking.domain

import org.springframework.data.jpa.repository.JpaRepository

interface RideRepository : JpaRepository<Ride, String>
