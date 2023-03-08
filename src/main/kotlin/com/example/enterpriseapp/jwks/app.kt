package com.example.enterpriseapp.jwks

import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.util.Base64Utils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

private const val PRIVATE_KEY =
    "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCywYu+rSPDl0/P6Se7Xm+L0mOx+lHgXuMVxL4S6Ru4/hoa+XKjMgk3VDQpyOLzhddmpT42EHvuli3+f6q2GAtHB3dSSnBPuWov/hD3LPf0vJRLvsSL67lZgmp773uCt4rJXfzfPDSBFNftbxOnGZVpwjSQ3HBPt+CP79Os1eXXebAT2eRxzYyhvSgPKS44A6j1LzU7iTwYg86CADpJZL1GtLP2gTzP1gDWV0Q3sn6eG68f5NIU690fniChQ/a8WgV5u6lZWSKZjjOwDVErTSi4TkPFS/j1mjXNeGUQm881yNtrQK07imkfw6Nt9Yn0lsFkOocTr+aKfiHF3zHPgWQ5AgMBAAECggEAE7p6stunA9IyU872vJ4qj3Lz39Oxr6KpS2DAXZPupFcfCHUZatt92uZnL2llat0Nrd105UCifO2EO/9ZFunGbNttFt7yUEo1ZwCSXMVQxGj/sPBn/s1QUomrOOxwZDffkGPYIcciQFDNl/3XXGzdaaOua4J4vsObfCdK5FtjF4m+ZcCVj9rNdRNxR62+SymwnmuAaT7fdz/xPqXXx6f7ZxP+g1XkmRVkL/lEimeva1C/VmzKXsZLAJnoE2HrJx5VVhOE1RzG/NRZ0KdHbR1D0L7A7R1YjmadD1o8vMSCzD+FQR4GXWLsErTCFy3SwDJbA2vjIBocL4mINxnughKEUQKBgQDXU+4AF70ts3UB0UtiU29M46BzklJpnwlPyD3oY961WKoca2hF219YU5ACp1biaDb3Vh7oDcqGTc23o4L2vizt47UDYctmu5k38lMRRYnoYxKOlKETnexFLGo/thcQV11AnA8wJa9BbaSafnXNNsBGri/e8ckt6WHiPAd7kaBL7QKBgQDUhTRdzmORy5mTrG8xeZ8Xb+BqNoeZl9TfQfwjMfI58EHR7kFZzzHYEQsfUJIxZLaBqmj6gEslhTnkyXFYlMHwvrvCXZIwm7BMJ5Xu51S4bBPCpOzPVjjpzKY6V3dGrnyKVDEL/TZP4WCXLv9nTSRENpf/lvldAis6dGypLnJn/QKBgFTewNUKhkcID5s6yhKkPh85LNnAl3kH9RycGUKKcpJZsxrmfr/h+k+PCBjzqfwtBVUxfZcLMIMFEYtLCGiGhqDw+jyuBASm9nolqfYJyZRt0Degf2iC+0g8fFhGRgrr1FaN7DKW99+6/oDiTT4oUVrKdxXRiPDupuULsgtTV7H1AoGBAIdH+9uy46MNTce5PlbqKqGKr/osmAjno7QylsP4qU7EZ8GwvpzizcHSp5fZfBZBHARSa9z6CdvqgL3olWRj3UjYwUCqu8KBeKohkMmxLDbxZWrD/ZLGOhhqE26T+vNdYx3TFh2hpA8ZUpkqa55gdrONZRhoDHhuRwJA1mKjlGQpAoGBAISPg/Jhid/HuJNR7N8mNqK5fPrAT62fc52lqQyNdeNSXRm25gTBxdPEA7XUtbEiEgUvgSDqcFklEN2XSecpoCl3Zcg8bGsRIBVGWNj4ydqFNvboS+7nfUYDE2UEecAuDDPaJ6k6LGMEM969HLR3oVrs6eB/zLI58zevOAyktBkK"

private const val PUBLIC_KEY =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAssGLvq0jw5dPz+knu15vi9JjsfpR4F7jFcS+EukbuP4aGvlyozIJN1Q0Kcji84XXZqU+NhB77pYt/n+qthgLRwd3UkpwT7lqL/4Q9yz39LyUS77Ei+u5WYJqe+97greKyV383zw0gRTX7W8TpxmVacI0kNxwT7fgj+/TrNXl13mwE9nkcc2Mob0oDykuOAOo9S81O4k8GIPOggA6SWS9RrSz9oE8z9YA1ldEN7J+nhuvH+TSFOvdH54goUP2vFoFebupWVkimY4zsA1RK00ouE5DxUv49Zo1zXhlEJvPNcjba0CtO4ppH8OjbfWJ9JbBZDqHE6/min4hxd8xz4FkOQIDAQAB"

fun execute() {
    val generator = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }

    generator.genKeyPair().run {

        println(Base64.getEncoder().encodeToString(private.encoded))
        println(Base64.getEncoder().encodeToString(public.encoded))
    }


    val privateKey = Base64Utils.decodeFromString(PRIVATE_KEY)
        .run { PKCS8EncodedKeySpec(this) }
        .run { KeyFactory.getInstance("RSA").generatePrivate(this) }

    val token = Jwts.builder()
        .setHeaderParam("kid", "399d8446-48bb-463d-b394-2113a61b90cf")
        .claim("email", "johndoe@gmail.com")
        .signWith(SignatureAlgorithm.RS256, privateKey)
        .compact()
        .also { println(it) }

    val publicKey = Base64Utils.decodeFromString(PUBLIC_KEY)
        .run { X509EncodedKeySpec(this) }
        .run { KeyFactory.getInstance("RSA").generatePublic(this) }

    Jwts.parser()
        .setSigningKey(publicKey)
        .parseClaimsJws(token)
        .body
        .also { println(it) }


    RSAKey.Builder(publicKey as RSAPublicKey)
        .keyUse(KeyUse.SIGNATURE)
        .keyID(UUID.randomUUID().toString())
        .build()
        .also { println(it) }

}

@RestController
class TokenAuthController {

    private val webClient = WebClient.builder().build()

    @GetMapping("/.well-known-server/keys")
    fun get(): Mono<String> = Mono.fromCallable {
        Base64Utils.decodeFromString(PUBLIC_KEY)
            .run { X509EncodedKeySpec(this) }
            .run { KeyFactory.getInstance("RSA").generatePublic(this) }
            .run {
                RSAKey.Builder(this as RSAPublicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID("399d8446-48bb-463d-b394-2113a61b90cf")
                    .build()
                    .toJSONString()

            }
    }.subscribeOn(Schedulers.boundedElastic())


    @GetMapping("/token/verify")
    fun verify(@RequestHeader(AUTHORIZATION) token: String) = webClient
        .get()
        .uri("http://localhost:8080/.well-known-server/keys")
        .retrieve()
        .bodyToMono(String::class.java)
        .doOnNext { println(it) }
        .map { RSAKey.parse(it) }
        .map {
            Jwts.parser()
                .setSigningKey(it.toRSAPublicKey())
                .parseClaimsJws(token)
                .body
        }

    @PostMapping("/token")
    fun create() = Mono.fromCallable {
        Base64Utils.decodeFromString(PRIVATE_KEY)
            .run { PKCS8EncodedKeySpec(this) }
            .run { KeyFactory.getInstance("RSA").generatePrivate(this) }
            .run {
                Jwts.builder()
                    .setHeaderParam("keyID", "399d8446-48bb-463d-b394-2113a61b90cf")
                    .claim("email", "johndoe@gmail.com")
                    .signWith(SignatureAlgorithm.RS256, this)
                    .compact()
            }
    }.subscribeOn(Schedulers.boundedElastic())
}