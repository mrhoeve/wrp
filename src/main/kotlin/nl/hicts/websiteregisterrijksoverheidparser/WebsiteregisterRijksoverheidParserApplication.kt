package nl.hicts.websiteregisterrijksoverheidparser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableScheduling


@SpringBootApplication
@EnableCaching
@EnableScheduling
class WebsiteregisterRijksoverheidParserApplication

fun main(args: Array<String>) {
    runApplication<WebsiteregisterRijksoverheidParserApplication>(*args)
}

@Bean
@Primary
fun objectMapper(): ObjectMapper {
    return ObjectMapper().registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
}
