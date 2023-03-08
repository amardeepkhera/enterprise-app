import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17
repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.3")

    }

}
// https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-dependencies

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.0")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")
    implementation("org.springframework.cloud:spring-cloud-starter-sleuth:3.1.2")
    implementation("io.projectreactor.kafka:reactor-kafka:1.3.11")
    implementation("org.springframework.kafka:spring-kafka:2.8.6")
    implementation ("io.confluent:kafka-avro-serializer:5.3.0")
    implementation("io.jsonwebtoken:jjwt:0.9.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:3.0.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.23")

    implementation("org.postgresql:r2dbc-postgresql:1.0.1.RELEASE")

    implementation("org.postgresql:postgresql:42.5.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
