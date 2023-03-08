package com.example.enterpriseapp.event_driven

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*

data class TransferPaymentRequest(val fromAccount: String, val toAccount: String, val amount: BigDecimal)
data class TransferPaymentResponse(val id: UUID)

@RestController
class PaymentController {

    @PostMapping("/transfer-payment")
    fun transferPayment(@RequestBody transferPaymentRequest: Mono<TransferPaymentRequest>) =
        TransferPaymentResponse(id = UUID.randomUUID())

}