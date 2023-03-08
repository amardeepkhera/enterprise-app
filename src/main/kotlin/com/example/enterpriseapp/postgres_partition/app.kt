package com.example.enterpriseapp.postgres_partition

import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@Table("customer")
data class Customer(val id: UUID, val name: String, @Column("country_code") val countryCode: String)

interface CustomerRepository : ReactiveCrudRepository<Customer, UUID> {

    fun findByCountryCode(countryCode: String): Flux<Customer>
}

data class CreateCustomerRequest(val name: String, val countryCode: String)
data class CreateCustomerResponse(val id: UUID)


@RestController
class CustomerController(private val customerRepository: CustomerRepository) {

    @PostMapping("/customer")
    fun create(@RequestBody createCustomerRequest: Mono<CreateCustomerRequest>) =
        createCustomerRequest.map {
            Customer(
                id = UUID.randomUUID(),
                name = it.name,
                countryCode = it.countryCode
            )
        }.flatMap { customerRepository.save(it) }
            .map { CreateCustomerResponse(id = it.id) }

    @GetMapping("/customer")
    fun searchCustomers(@RequestParam(name = "countryCode", required = true) countryCode: String) =
        customerRepository.findByCountryCode(countryCode)
}