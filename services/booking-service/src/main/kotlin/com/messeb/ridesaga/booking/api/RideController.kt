package com.messeb.ridesaga.booking.api

import com.messeb.ridesaga.booking.application.RideService
import com.messeb.ridesaga.booking.domain.Ride
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

data class CreateRideRequest(
    val riderId: String,
    val pickupLocation: String,
    val dropoffLocation: String,
    val fareAmount: BigDecimal,
    val currency: String = "EUR",
)

data class RideResponse(
    val rideId: String,
    val status: String,
    val riderId: String,
    val pickupLocation: String,
    val dropoffLocation: String,
    val fareAmount: BigDecimal,
    val currency: String,
    val driverId: String?,
    val cancellationReason: String?,
    val requestedAt: Instant,
) {
    companion object {
        fun from(ride: Ride) = RideResponse(
            rideId = ride.id,
            status = ride.status.name,
            riderId = ride.riderId,
            pickupLocation = ride.pickupLocation,
            dropoffLocation = ride.dropoffLocation,
            fareAmount = ride.fareAmount,
            currency = ride.currency,
            driverId = ride.driverId,
            cancellationReason = ride.cancellationReason,
            requestedAt = ride.requestedAt,
        )
    }
}

@RestController
@RequestMapping("/api/rides")
class RideController(private val rides: RideService) {

    @PostMapping
    fun requestRide(@RequestBody request: CreateRideRequest): ResponseEntity<RideResponse> {
        val ride = rides.requestRide(
            riderId = request.riderId,
            pickup = request.pickupLocation,
            dropoff = request.dropoffLocation,
            fare = request.fareAmount,
            currency = request.currency,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RideResponse.from(ride))
    }

    @GetMapping("/{rideId}")
    fun getRide(@PathVariable rideId: String): ResponseEntity<RideResponse> {
        val ride = rides.findRide(rideId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(RideResponse.from(ride))
    }
}
