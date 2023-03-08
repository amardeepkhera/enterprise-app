package com.example.enterpriseapp.airbnb

import brave.Tracer
import brave.kafka.clients.KafkaTracing
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumerFactory
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.ReceiverRecord
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions
import reactor.kafka.sender.SenderRecord
import reactor.kotlin.core.publisher.toMono
import java.net.URI
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


@Configuration
class MessagingConfig(@Value("\${KAFKA_PASSWORD}") private val kafkaPassword: String) {

    private val logger = KotlinLogging.logger { }

    private fun consumerProperties() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to "pkc-ymrq7.us-east-2.aws.confluent.cloud:9092",
        KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
        VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
        GROUP_ID_CONFIG to "application-consumer",
        SECURITY_PROTOCOL_CONFIG to "SASL_SSL",
        SASL_MECHANISM to "PLAIN",
        SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule   required username='TLNXGQIX5OBCGIR7'   password='$kafkaPassword';"
    )

    private fun receiverOptions() = ReceiverOptions.create<String, String>(consumerProperties())
        .subscription(listOf("first-topic"))
        .addAssignListener {
            it.forEach { logger.info { "${it.topicPartition()}" } }
        }

    @Bean
    fun reactiveKafkaConsumer(t: TracingKafkaConsumerFactory) = KafkaReceiver.create(t, receiverOptions())

    private fun senderProperties() = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "pkc-ymrq7.us-east-2.aws.confluent.cloud:9092",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        SECURITY_PROTOCOL_CONFIG to "SASL_SSL",
        SASL_MECHANISM to "PLAIN",
        SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule   required username='TLNXGQIX5OBCGIR7'   password='V5rxNzoXbbeqQzGh+y6bFzlSkFpQRue687Q9Mf7e5u6UhuyfVT5gYd6NuZ+DxuVV';"
    )

    private fun senderOptions() = SenderOptions.create<String, String>(senderProperties())

    @Bean
    fun reactiveKafkaProducer(t: TracingKafkaProducerFactory) = KafkaSender.create(t, senderOptions())
}

@Component
class AirBnbMessageConsumer(
    private val reactiveKafkaConsumerTemplate: KafkaReceiver<String, String>,
    private val airBnbMessageHandler: AirBnbMessageHandler,
    private val kafkaTracing: KafkaTracing,
    private val tracer: Tracer
) {

    private val logger = KotlinLogging.logger { }


    private val disposables = mutableListOf<Disposable>()

    @PostConstruct
    fun register() {
        reactiveKafkaConsumerTemplate
            .receive()
            .concatMap { message ->
                val span = kafkaTracing.nextSpan(message).start()
                    .also { tracer.withSpanInScope(it) }
                airBnbMessageHandler.handle(message)
                    .doFinally { span.finish() }
            }
            .subscribe().also {
                disposables += it
            }
    }

    @PreDestroy
    fun deRegister() {
        disposables.forEach { it.dispose() }
    }

}

@Service
class AirBnbMessageHandler(
    private val objectMapper: ObjectMapper,
    private val airBnbRepository: AirBnbRepository,
    private val messageProducer: KafkaSender<String, String>,
) {
    private val logger = KotlinLogging.logger { }


    fun handle(message: ReceiverRecord<String, String>) = defer {
        message.toMono()
            .doOnNext { logger.info { it.value() } }
            .flatMap {
                airBnbRepository.save(objectMapper.readValue(message.value()))
                    .doOnNext { logger.info { "Listing created ${it.id}" } }
                    .then(sendToRetry(message))
                    .doOnSuccess {
                        message.receiverOffset().acknowledge()
                    }
            }
    }

    private fun sendToRetry(message: ReceiverRecord<String, String>) =
        ProducerRecord<String, String>("retry-topic", message.value()).apply {
            message.headers().forEach { headers().add(it) }
        }.run { SenderRecord.create(this, UUID.randomUUID().toString()) }
            .run { messageProducer.send(toMono()) }
            .next()
}

@RestController
@RequestMapping("/airbnb/listing")
class AirBnbController(
    private val objectMapper: ObjectMapper,
    private val airBnbRepository: AirBnbRepository,
    private val messageProducer: KafkaSender<String, String>,
) {

    private val logger = KotlinLogging.logger { }

    @GetMapping("/search")
    fun get(
        @RequestParam("name") name: String,
        @RequestParam("property_type", required = false) propertyType: String?,
        @RequestParam("amenities", required = false) amenities: Set<String>?
    ): Flux<Listing> {
        logger.info { "name=$name, property_type=$propertyType, amenities=$amenities" }
        return airBnbRepository.find(name = name, propertyType, amenities.orEmpty())
    }


    @PostMapping
    fun create(@RequestBody listing: Mono<Listing>) = listing
        .doOnNext { logger.info { it } }
        .map {
            ProducerRecord<String, String>("first-topic", objectMapper.writeValueAsString(it))
        }.map { SenderRecord.create(it, UUID.randomUUID().toString()) }
        .flatMapMany { messageProducer.send(it.toMono()) }
        .next()
        .map {
            ResponseEntity.created(URI.create("/${it.correlationMetadata()}")).build<Void>()
        }


}

@Document("listingsAndReviews")
data class Listing(
    @Id val id: String? = null,
    val name: String,
    val propertyType: String? = null,
    val amenities: List<String>? = null
)

@Repository
class AirBnbRepository(private val reactiveMongoTemplate: ReactiveMongoTemplate) {

    fun save(listing: Listing) = reactiveMongoTemplate.save(listing)

    fun find(name: String, propertyType: String?, amenities: Set<String>) = query(
        where("name")
            .regex("^$name")
            .run {
                if (propertyType.isNullOrBlank().not())
                    and("propertyType")
                        .isEqualTo(propertyType)
                else this
            }.run {
                if (amenities.isEmpty().not())
                    and("amenities")
                        .all(amenities)
                else this
            }
    ).run { reactiveMongoTemplate.find(this, Listing::class.java) }
}


@Configuration
class CacheConfig(private val airBnbRepository: AirBnbRepository) {

    private val logger = KotlinLogging.logger { }

//    @Bean
//    fun searchCache(): AsyncLoadingCache<String, List<Listing>> = Caffeine.newBuilder()
//        .refreshAfterWrite(Duration.ofMinutes(1))
//        .expireAfterWrite(Duration.ofMinutes(2))
//        .removalListener { param: Any?, param1: Any?, removalCause: RemovalCause ->
//            logger.info { "Search cache $removalCause for key $param" }
//        }.scheduler(Scheduler.systemScheduler())
//        .buildAsync { s: String, _: Executor ->
//            airBnbRepository.findByNameStartingWith(s)
//                .collectList().toFuture()
//        }

}