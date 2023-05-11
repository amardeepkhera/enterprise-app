package com.example.enterpriseapp.payment

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*

data class TransferPaymentRequest(val fromAccount: String, val toAccount: String, val amount: BigDecimal)
data class TransferPaymentResponse(val id: UUID)

@RestController
class PaymentController(
    private val validateTransferPaymentService: ValidateTransferPaymentService,
    private val processPaymentService: ProcessPaymentService
) {

    @PostMapping("/transfer-payment")
    fun transferPayment(@RequestBody transferPaymentRequest: Mono<TransferPaymentRequest>) = transferPaymentRequest
        .flatMap {
            val transferPaymentRequestId = UUID.randomUUID()
            validateTransferPaymentService.validate(
                transferPaymentRequestId = transferPaymentRequestId,
                transferPaymentRequest = it
            ).then(processPaymentService.process(it))
                .thenReturn(TransferPaymentResponse(id = transferPaymentRequestId))

        }
}