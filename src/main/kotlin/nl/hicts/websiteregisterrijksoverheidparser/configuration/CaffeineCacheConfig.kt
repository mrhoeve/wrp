package nl.hicts.websiteregisterrijksoverheidparser.configuration

import com.github.benmanes.caffeine.cache.Caffeine
import nl.hicts.websiteregisterrijksoverheidparser.service.RegisterCache
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CaffeineCacheConfig(
    @param:Value("\${cacheduration:}") private val cacheDuration: String,
    @param:Value("\${cachetimeunit:}") private val cacheTimeUnit: String,
) {
    companion object {
        private const val DEFAULT_CACHE_DURATION = 15L
        private val DEFAULT_CACHE_TIME_UNIT = TimeUnit.MINUTES
    }

    private val logger = LoggerFactory.getLogger(CaffeineCacheConfig::class.java)

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(RegisterCache.DATA, RegisterCache.METADATA)
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        val duration = determineCacheDuration()
        val timeUnit = determineCacheTimeUnit()
        logger.info("Configure cache to $duration $timeUnit")
        return Caffeine.newBuilder()
            .recordStats()
            .expireAfterAccess(duration, timeUnit)
    }

    private fun determineCacheDuration(): Long {
        val configuredDuration = cacheDuration.trim().toLongOrNull()
        if (configuredDuration != null && configuredDuration > 0) return configuredDuration

        if (cacheDuration.isNotBlank()) {
            logger.warn(
                "Invalid cacheduration '{}'; using default value {}",
                cacheDuration,
                DEFAULT_CACHE_DURATION,
            )
        }
        return DEFAULT_CACHE_DURATION
    }

    private fun determineCacheTimeUnit(): TimeUnit {
        return when (cacheTimeUnit.uppercase().trim()) {
            "SECONDS" -> TimeUnit.SECONDS
            "MINUTES" -> TimeUnit.MINUTES
            "HOURS" -> TimeUnit.HOURS
            "DAYS" -> TimeUnit.DAYS
            else -> {
                if (cacheTimeUnit.isNotBlank()) {
                    logger.warn(
                        "Invalid cachetimeunit '{}'; using default value {}",
                        cacheTimeUnit,
                        DEFAULT_CACHE_TIME_UNIT,
                    )
                }
                DEFAULT_CACHE_TIME_UNIT
            }
        }
    }
}
