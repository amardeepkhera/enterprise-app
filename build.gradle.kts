import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.github.davidmc24.gradle.plugin.avro.ResolveAvroDependenciesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.6.0"
    id("com.bmuschko.docker-remote-api") version "9.2.1"
    id("com.avast.gradle.docker-compose") version "0.16.11"

}

group = "com.example"
version = "1.0"
java.sourceCompatibility = JavaVersion.VERSION_17
repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.3")
    }

}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
//    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.0")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")
    implementation("org.springframework.cloud:spring-cloud-starter-sleuth:3.1.2")
    implementation("io.projectreactor.kafka:reactor-kafka:1.3.11")
    implementation("org.springframework.kafka:spring-kafka:2.8.6")
    implementation("io.confluent:kafka-avro-serializer:7.3.2")
    implementation("io.jsonwebtoken:jjwt:0.9.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:3.0.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.23")
//    implementation ("org.springframework.cloud:spring-cloud-starter-bootstrap:3.1.3")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")
    implementation("org.springframework.boot:spring-boot-starter-aop")

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

avro {
    setEnableDecimalLogicalType(true)
}
tasks.register("resolveAvroDependencies", ResolveAvroDependenciesTask::class) {
    source = fileTree("${projectDir}/src/main/avro")
    setOutputDir(file("${projectDir}/build/avro/resolved"))
}

tasks.create("copyJar", Copy::class.java) {

    from("${project.buildDir}/libs/enterprise-app-${project.version}.jar")
    destinationDir = File("${project.buildDir}/docker")
    rename {
        it.replace("-${project.version}", "")
    }
}

tasks.create("createDockerFile", Dockerfile::class.java) {
    val projectProperties = project.gradle.startParameter.projectProperties

    destFile.set(File("${project.buildDir}/docker/Dockerfile"))
    from("eclipse-temurin:17-jre-alpine")
    volume("/app")
    copyFile("/enterprise-app.jar", "/app/enterprise-app.jar")
    entryPoint(
        "java",
        "-jar",
        "-DKAFKA_BOOTSTRAP_SERVERS=${projectProperties["kafkaBootstrapServers"]}",
        "-DKAFKA_SCHEMAREGISTRY_URL=${projectProperties["kafkaSchemaregistryUrl"]}",
        "-DKAFKA_SCHEMAREGISTRY_KEY=${projectProperties["kafkaSchemaregistryKey"]}",
        "-DKAFKA_SCHEMAREGISTRY_SECRET=${projectProperties["kafkaSchemaregistrySecret"]}",
        "-DKAFKA_PASSWORD=${projectProperties["kafkaPassword"]}",
        "-DREDIS_HOST=redis",
        "/app/enterprise-app.jar"
    )
}

tasks.create("buildDockerImage", DockerBuildImage::class.java) {
    inputDir.set(File("${project.buildDir}/docker"))
    dockerFile.set(File("${project.buildDir}/docker/Dockerfile"))
    images.add("enterprise-app:latest")
}

tasks.findByPath("createDockerFile")?.dependsOn(tasks.findByPath("copyJar"))
tasks.findByPath("buildDockerImage")?.dependsOn(tasks.findByPath("createDockerFile"))

dockerCompose {
    useComposeFiles.add("${project.projectDir}/docker/docker-compose.yml")
    scale.put("enterprise-app", 2)
    scale.put("redis", 1)
    captureContainersOutput.set(true)
}