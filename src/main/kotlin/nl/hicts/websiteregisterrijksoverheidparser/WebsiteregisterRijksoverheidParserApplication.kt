package nl.hicts.websiteregisterrijksoverheidparser

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableScheduling
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule


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
    return JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
        .build()
}
