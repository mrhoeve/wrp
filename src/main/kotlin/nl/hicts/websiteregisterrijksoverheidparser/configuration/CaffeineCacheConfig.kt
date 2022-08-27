package nl.hicts.websiteregisterrijksoverheidparser.configuration

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CaffeineCacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("data", "metadata")
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .initialCapacity(2500)
            .maximumSize(3000)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .weakKeys()
    }
}
