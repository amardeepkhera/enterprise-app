package com.example.enterpriseapp.payment

import com.example.enterpriseapp.event_driven.message.TransferPaymentRequest
import com.example.enterpriseapp.event_driven.message.ValidateTransferPaymentResponse
import com.example.enterpriseapp.payment.ValidateTransferPaymentResponseValue.ValidateTransferPaymentResponseNotReceived
import com.example.enterpriseapp.payment.ValidateTransferPaymentResponseValue.ValidateTransferPaymentResponseReceived
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import mu.KotlinLogging
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord
import reactor.kotlin.core.publisher.cast
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
class TransferPaymentEventConsumer(
    private val transferPaymentEventReceiver: KafkaReceiver<String, TransferPaymentRequest>,
    private val validateTransferPaymentEventProducer: KafkaSender<String, ValidateTransferPaymentResponse>,
) {

    private val logger = KotlinLogging.logger { }


    private val disposables = mutableListOf<Disposable>()

    @PostConstruct
    fun register() {
        transferPaymentEventReceiver
            .receive()
            .concatMap { message ->
                message.value().validate()
            }
            .subscribe().also {
                disposables += it
            }
    }

    private fun TransferPaymentRequest.validate() = ValidateTransferPaymentResponse.newBuilder()
        .apply {
            id = this@validate.id
            isValid = true
        }.build().run {
            ProducerRecord(
                "validate-transfer-payment-response", UUID.randomUUID().toString(),
                this
            )
        }.run { SenderRecord.create(this, UUID.randomUUID().toString()) }
        .run {
            logger.info { "Publishing validateTransferPaymentEventResponse for ${this.value().id}" }
            validateTransferPaymentEventProducer.send(toMono())
        }

    @PreDestroy
    fun deRegister() {
        disposables.forEach { it.dispose() }
    }

}

@Service
class ValidateTransferPaymentService(
    private val publishTransferPaymentEventFunction: PublishTransferPaymentEventFunction,
    private val validateTransferPaymentResponseReceiver: KafkaReceiver<String, ValidateTransferPaymentResponse>,
    private val validateTransferPaymentResponseRedisTemplate: ReactiveRedisTemplate<String, ValidateTransferPaymentResponseValue>,
) {

    private val logger = KotlinLogging.logger { }

    private val disposables = mutableListOf<Disposable>()

    private val transferPaymentRequestResponseMap = mutableMapOf<UUID, ValidateTransferPaymentResponse?>()

    private lateinit var redisTransferPaymentRequestId: String

    @PostConstruct
    fun register() {
        redisTransferPaymentRequestId =
            UUID.randomUUID().toString().also { logger.info { "redisTransferPaymentRequestId=$it" } }
        validateTransferPaymentResponseReceiver
            .receive()
            .concatMap { message ->
                message.value().toMono()
                    .flatMap { updateValidateTransferPaymentResponse(it) }
            }
            .subscribe().also {
                disposables += it
            }
    }

    private fun updateValidateTransferPaymentResponse(validateTransferPaymentResponse: ValidateTransferPaymentResponse) =
        ValidateTransferPaymentResponseReceived(
            id = UUID.fromString(
                validateTransferPaymentResponse.id
            ), valid = validateTransferPaymentResponse.isValid
        ).run {
            val cacheKey = UUID.fromString(validateTransferPaymentResponse.id).toCacheKey()
            validateTransferPaymentResponseRedisTemplate.opsForValue()
                .setIfPresent(cacheKey, this)
                .doOnNext { logger.info { "Cache updated for key $cacheKey = $it" } }
        }.then()


    private fun updateTransferPaymentRequestResponseMap(validateTransferPaymentResponse: ValidateTransferPaymentResponse) {
        if (transferPaymentRequestResponseMap.containsKey(UUID.fromString(validateTransferPaymentResponse.id)))
            transferPaymentRequestResponseMap.put(
                UUID.fromString(validateTransferPaymentResponse.id),
                validateTransferPaymentResponse
            ).also {
                logger.info { "Validate transfer payment response found for id ${validateTransferPaymentResponse.id}" }
            }
        else logger.info { "Validate transfer payment response not found for id ${validateTransferPaymentResponse.id}" }
    }


    fun validate(
        transferPaymentRequestId: UUID,
        transferPaymentRequest: com.example.enterpriseapp.payment.TransferPaymentRequest
    ): Mono<ValidateTransferPaymentResponse> = transferPaymentRequest.toMono()
        .doOnNext { logger.info { "Validating transfer payment request with id $transferPaymentRequestId" } }
        .map {
            TransferPaymentRequest.newBuilder()
                .apply {
                    id = transferPaymentRequestId.toString()
                    amount = transferPaymentRequest.amount.toDouble()
                    fromAccount = transferPaymentRequest.fromAccount
                    toAccount = transferPaymentRequest.toAccount
                }.build()
        }.flatMap {
            publishTransferPaymentEventFunction.execute(it)
                .next()
                .doOnNext { logger.info { "Message published to topic ${it.recordMetadata().topic()}" } }
        }.flatMap {
            val cacheKay = transferPaymentRequestId.toCacheKey()
            validateTransferPaymentResponseRedisTemplate.opsForValue()
                .set(cacheKay, ValidateTransferPaymentResponseNotReceived)
                .doOnNext { logger.info { "Cached key=$cacheKay" } }
        }
        .then(awaitValidateTransferPaymentResponse(transferPaymentRequestId))

//    private fun awaitValidateTransferPaymentResponse(transferPaymentRequestId: UUID) =
//
//        Mono.create<ValidateTransferPaymentResponse> {
//            val validateTransferPaymentResponse = transferPaymentRequestResponseMap[transferPaymentRequestId]
//            logger.info { "Validate transfer payment response = $validateTransferPaymentResponse" }
//            if (validateTransferPaymentResponse != null)
//                it.success(validateTransferPaymentResponse)
//            else it.error(ValidateTransferPaymentResponseNotFoundException())
//        }.retryWhen(
//            Retry.backoff(10, Duration.ofMillis(10))
//                .filter { it is ValidateTransferPaymentResponseNotFoundException }
//                .onRetryExhaustedThrow { t, u -> u.failure() }
//                .scheduler(Schedulers.boundedElastic())
//        ).doFinally { transferPaymentRequestResponseMap.remove(transferPaymentRequestId) }

    private fun awaitValidateTransferPaymentResponse(transferPaymentRequestId: UUID): Mono<ValidateTransferPaymentResponse> {
        val cacheKay = transferPaymentRequestId.toCacheKey()
        return validateTransferPaymentResponseRedisTemplate.opsForValue()
            .get(cacheKay)
            .filter { it is ValidateTransferPaymentResponseReceived }
            .doOnNext { logger.info { "Get successful from cache with key=$cacheKay" } }
            .cast<ValidateTransferPaymentResponseReceived>()
            .doOnNext { logger.info { logger.info { "Validate transfer payment response = $it" } } }
            .map {
                ValidateTransferPaymentResponse.newBuilder().apply {
                    id = it.id.toString()
                    isValid = it.valid
                }.build()
            }
            .switchIfEmpty {
                logger.info { logger.info { "Get unsuccessful from cache with key=$cacheKay" } }
                Mono.error(ValidateTransferPaymentResponseNotFoundException())
            }
            .retryWhen(
                Retry.backoff(20, Duration.ofMillis(10))
                    .filter { it is ValidateTransferPaymentResponseNotFoundException }
                    .onRetryExhaustedThrow { t, u -> u.failure() }
                    .scheduler(Schedulers.boundedElastic())
            ).doFinally {
                validateTransferPaymentResponseRedisTemplate.opsForValue()
                    .delete(cacheKay)
                    .subscribe { logger.info { "Deleted key $cacheKay from cache=$it" } }
            }
    }

    private fun UUID.toCacheKey() = "transferPaymentRequestId_$redisTransferPaymentRequestId" + "_" + this

    @PreDestroy
    fun deRegister() {
        disposables.forEach { it.dispose() }
    }
}

private class ValidateTransferPaymentResponseNotFoundException : RuntimeException()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(
            value = ValidateTransferPaymentResponseReceived::class,
            name = "ValidateTransferPaymentResponseReceived"
        ),
        JsonSubTypes.Type(
            value = ValidateTransferPaymentResponseNotReceived::class,
            name = "ValidateTransferPaymentResponseNotReceived"
        )
    ]
)
sealed class ValidateTransferPaymentResponseValue {

    data class ValidateTransferPaymentResponseReceived(
        @JsonProperty("id") val id: UUID,
        @JsonProperty("valid") val valid: Boolean
    ) : ValidateTransferPaymentResponseValue()


    object ValidateTransferPaymentResponseNotReceived : ValidateTransferPaymentResponseValue()
}

