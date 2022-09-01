package nl.hicts.websiteregisterrijksoverheidparser.configuration

import com.github.benmanes.caffeine.cache.Caffeine
import nl.hicts.websiteregisterrijksoverheidparser.service.WebsiteregisterRijksoverheidService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CaffeineCacheConfig {
    private val logger = LoggerFactory.getLogger(CaffeineCacheConfig::class.java)

    @Value("\${cacheduration:}")
    private lateinit var cacheDuration: String

    @Value("\${cachetimeunit:}")
    private lateinit var cacheTimeUnit: String

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("data", "metadata")
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        logger.info("Configure cache to ${determineCacheDuration()} ${determineCacheTimeUnit()}")
        return Caffeine.newBuilder()
            .initialCapacity(2500)
            .maximumSize(3000)
            .expireAfterAccess(determineCacheDuration(), determineCacheTimeUnit())
            .weakKeys()
    }

    private fun determineCacheDuration(): Long {
        return cacheDuration.toLongOrNull()?.let { it } ?: run { 15L }
    }

    private fun determineCacheTimeUnit(): TimeUnit {
        return when (cacheTimeUnit.uppercase().trim()) {
            "SECONDS" -> TimeUnit.SECONDS
            "HOURS" -> TimeUnit.HOURS
            "DAYS" -> TimeUnit.DAYS
            else -> TimeUnit.MINUTES
        }
    }
}
