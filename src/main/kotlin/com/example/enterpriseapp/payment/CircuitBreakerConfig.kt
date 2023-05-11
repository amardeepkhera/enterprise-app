package com.example.enterpriseapp.payment

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory
import org.springframework.cloud.client.circuitbreaker.Customizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class CircuitBreakerConfig {

    @Bean
    fun configure(): Customizer<ReactiveResilience4JCircuitBreakerFactory> = Customizer {
        it.circuitBreakerRegistry
            .circuitBreaker("processPayment") {
                CircuitBreakerConfig.Builder()
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(1)
                    .build()
            }
    }
}