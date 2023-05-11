package com.example.enterpriseapp.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializationContext.RedisSerializationContextBuilder
import org.springframework.data.redis.serializer.StringRedisSerializer


@Configuration
class RedisConfig(
    @Value("\${REDIS_HOST}") private val redisHost: String
) {

    @Bean
    fun reactiveRedisConnectionFactory() = LettuceConnectionFactory(redisHost, 6379)

    @Bean
    fun validateTransferPaymentResponseRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, ValidateTransferPaymentResponseValue> {
        val keySerializer = StringRedisSerializer()
        val valueSerializer: Jackson2JsonRedisSerializer<ValidateTransferPaymentResponseValue> =
            Jackson2JsonRedisSerializer(ValidateTransferPaymentResponseValue::class.java)
        val builder: RedisSerializationContextBuilder<String, ValidateTransferPaymentResponseValue> =
            RedisSerializationContext.newSerializationContext(keySerializer)
        val context: RedisSerializationContext<String, ValidateTransferPaymentResponseValue> =
            builder.value(valueSerializer).build()
        return ReactiveRedisTemplate(factory, context)
    }

}