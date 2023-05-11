package com.example.enterpriseapp.payment

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import reactor.kotlin.core.publisher.toMono


@Service
class ProcessPaymentService {

    private val logger = KotlinLogging.logger { }

    @CircuitBreaker(name = "processPayment", fallbackMethod = "fallbackProcess")
    fun process(transferPaymentRequest: TransferPaymentRequest): Mono<String> = defer {
        "".toMono()
            .doOnNext { logger.info { "processPayment" } }
            .flatMap { Mono.error<String> { RuntimeException("processPayment ex") } }
    }

    fun fallbackProcess(transferPaymentRequest: TransferPaymentRequest, ex: Exception): Mono<String> = "1".toMono()
        .doOnNext { logger.error(ex) { "fallbackProcess" } }
}