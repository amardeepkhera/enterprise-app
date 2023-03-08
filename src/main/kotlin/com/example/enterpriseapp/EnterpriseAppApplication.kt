package com.example.enterpriseapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@EnableCaching
@ComponentScan("com.example.enterpriseapp.airbnb")
class EnterpriseAppApplication

fun main(args: Array<String>) {
    runApplication<EnterpriseAppApplication>(*args)
}
