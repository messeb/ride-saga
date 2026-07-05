package com.messeb.ridesaga.booking.api

import com.messeb.ridesaga.common.EventHeaders
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * HTTP ingress is where a saga's correlation id is born: taken from the X-Correlation-Id
 * request header if the caller supplies one, generated otherwise. From here it travels in
 * the MDC and is stamped onto every event the request triggers.
 */
@Component
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val correlationId = request.getHeader(HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        MDC.put(EventHeaders.MDC_CORRELATION_ID, correlationId)
        response.setHeader(HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(EventHeaders.MDC_CORRELATION_ID)
        }
    }

    companion object {
        const val HEADER = "X-Correlation-Id"
    }
}
