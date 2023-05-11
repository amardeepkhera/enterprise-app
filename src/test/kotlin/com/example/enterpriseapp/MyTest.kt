package com.example.enterpriseapp

import com.example.enterpriseapp.payment.ValidateTransferPaymentResponseValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import reactor.test.StepVerifier
import reactor.util.retry.Retry
import java.time.Duration
import java.util.*

class MyTest {

    @Test
    fun testMono() {
        val set = mutableSetOf(2)

        1.toMono()
            .delayElement(Duration.ofSeconds(4))
            .doOnNext { set.add(1) }
            .doOnNext { println(set) }
            .subscribe()

        set.toMono()
            .doOnNext { println(it) }
            .filter { it.contains(1) }
            .switchIfEmpty { Mono.error(RuntimeException()) }
            .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
            .run {
                StepVerifier.create(this)
                    .assertNext {
                        Assertions.assertTrue(it.contains(1))
                    }
                    .verifyComplete()

            }
    }

    @Test
    fun serialiseSealedClasses() {
        val objectMapper = ObjectMapper()
        objectMapper.writeValueAsString(ValidateTransferPaymentResponseValue.ValidateTransferPaymentResponseNotReceived)
            .also { println(it) }
    }

    @Test
    fun deSerialiseSealedClasses() {
        val objectMapper = ObjectMapper()
        val json = objectMapper.writeValueAsString(
            ValidateTransferPaymentResponseValue.ValidateTransferPaymentResponseReceived(
                id = UUID.randomUUID(),
                valid = true
            )
        )
            .also { println(it) }
        objectMapper.readValue<ValidateTransferPaymentResponseValue>(json)
            .also { println(it) }
    }
}