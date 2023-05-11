package com.example.enterpriseapp.payment

import com.example.enterpriseapp.event_driven.message.TransferPaymentRequest
import com.example.enterpriseapp.event_driven.message.ValidateTransferPaymentResponse
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import mu.KotlinLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaConsumerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions
import java.util.*

@Configuration
class MessagingConfig(
    @Value("\${KAFKA_BOOTSTRAP_SERVERS}") private val kafkaBootstrapServers: String,
    @Value("\${KAFKA_SCHEMAREGISTRY_URL}") private val kafkaSchemaRegistryUrl: String,
    @Value("\${KAFKA_SCHEMAREGISTRY_KEY}") private val kafkaSchemaRegistryKey: String,
    @Value("\${KAFKA_SCHEMAREGISTRY_SECRET}") private val kafkaSchemaRegistrySecret: String,
    @Value("\${KAFKA_PASSWORD}") private val kafkaPassword: String
) {

    private val logger = KotlinLogging.logger { }

    private fun consumerProperties(groupName: String) = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java.name,
        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
        ConsumerConfig.GROUP_ID_CONFIG to groupName,
        "schema.registry.url" to kafkaSchemaRegistryUrl,
        "basic.auth.credentials.source" to "USER_INFO",
        "basic.auth.user.info" to "$kafkaSchemaRegistryKey:$kafkaSchemaRegistrySecret",
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_SSL",
        SaslConfigs.SASL_MECHANISM to "PLAIN",
        SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule   required username='TLNXGQIX5OBCGIR7'   password='$kafkaPassword';"
    )

    @Bean
    fun transferPaymentEventReceiver(t: TracingKafkaConsumerFactory): KafkaReceiver<String, TransferPaymentRequest> =
        ReceiverOptions.create<String, TransferPaymentRequest>(consumerProperties("validateTransferPaymentRequest"))
            .subscription(listOf("transfer-payment-command"))
            .addAssignListener { it.forEach { logger.info { "Listener assigned to ${it.topicPartition()}" } } }
            .run { KafkaReceiver.create(t, this) }

    @Bean
    fun validateTransferPaymentResponseReceiver(t: TracingKafkaConsumerFactory): KafkaReceiver<String, ValidateTransferPaymentResponse> =
        ReceiverOptions.create<String, ValidateTransferPaymentResponse>(consumerProperties("validateTransferPaymentResponse-${UUID.randomUUID()}"))
            .subscription(listOf("validate-transfer-payment-response"))
            .addAssignListener { it.forEach { logger.info { "Listener assigned to ${it.topicPartition()}" } } }
            .run { KafkaReceiver.create(t, this) }

    private fun senderProperties() = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java.name,
        "schema.registry.url" to kafkaSchemaRegistryUrl,
        "basic.auth.credentials.source" to "USER_INFO",
        "basic.auth.user.info" to "$kafkaSchemaRegistryKey:$kafkaSchemaRegistrySecret",
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_SSL",
        SaslConfigs.SASL_MECHANISM to "PLAIN",
        SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule   required username='TLNXGQIX5OBCGIR7'   password='$kafkaPassword';"
    )

    @Bean
    fun transferPaymentEventProducer(): KafkaSender<String, TransferPaymentRequest> =
        SenderOptions.create<String, TransferPaymentRequest>(senderProperties())
            .run { KafkaSender.create(this)}


    @Bean
    fun validateTransferPaymentEventProducer(): KafkaSender<String, ValidateTransferPaymentResponse> =
        SenderOptions.create<String, ValidateTransferPaymentResponse>(senderProperties())
            .run { KafkaSender.create(this) }
}