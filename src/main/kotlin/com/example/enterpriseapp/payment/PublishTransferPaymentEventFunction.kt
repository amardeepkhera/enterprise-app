package com.example.enterpriseapp.payment

import com.example.enterpriseapp.event_driven.message.TransferPaymentRequest
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord
import reactor.kafka.sender.SenderResult
import reactor.kotlin.core.publisher.toMono
import java.util.*

@Component
class PublishTransferPaymentEventFunction(private val transferPaymentEventProducer: KafkaSender<String, TransferPaymentRequest>) {

    fun execute(transferPaymentRequest: TransferPaymentRequest): Flux<SenderResult<String>> =
        ProducerRecord(
            "transfer-payment-command", UUID.randomUUID().toString(),
            transferPaymentRequest
        ).run { SenderRecord.create(this, UUID.randomUUID().toString()) }
            .run { transferPaymentEventProducer.send(toMono()) }
}