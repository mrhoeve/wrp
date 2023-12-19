package nl.hicts.websiteregisterrijksoverheidparser.configuration

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.test.util.ReflectionTestUtils
import java.util.concurrent.TimeUnit

class CaffeineCacheConfigTest {
    private val caffeineCacheConfig = CaffeineCacheConfig()

    private val caffeineBuilderMock = mockk<Caffeine<Any, Any>>()
    private val caffeineCacheMock = mockk<Cache<Any, Any>>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Caffeine::class)
        every { Caffeine.newBuilder() } returns caffeineBuilderMock
        every { caffeineBuilderMock.initialCapacity(any()) } returns caffeineBuilderMock
        every { caffeineBuilderMock.maximumSize(any()) } returns caffeineBuilderMock
        every { caffeineBuilderMock.expireAfterAccess(any(), any()) } returns caffeineBuilderMock
        every { caffeineBuilderMock.weakKeys() } returns caffeineBuilderMock
        every { caffeineBuilderMock.build<Any, Any>() } returns caffeineCacheMock
    }

    @Test
    fun `test default configuration`() {
        setCacheDuration("")
        setCacheTimeUnit("")

        caffeineCacheConfig.cacheManager()

        // General values
        verify { caffeineBuilderMock.initialCapacity(2500) }
        verify { caffeineBuilderMock.maximumSize(3000) }
        verify { caffeineBuilderMock.weakKeys() }

        // Default time values
        verify { caffeineBuilderMock.expireAfterAccess(15L, TimeUnit.MINUTES) }
    }

    @Test
    fun `test non default configuration`() {
        val duration = 43L
        val timeUnit = TimeUnit.HOURS
        setCacheDuration(duration.toString())
        setCacheTimeUnit(timeUnit.toString())

        caffeineCacheConfig.cacheManager()

        // General values
        verify { caffeineBuilderMock.initialCapacity(2500) }
        verify { caffeineBuilderMock.maximumSize(3000) }
        verify { caffeineBuilderMock.weakKeys() }

        // Default time values
        verify { caffeineBuilderMock.expireAfterAccess(duration, timeUnit) }
    }

    private fun setCacheDuration(value: String) {
        ReflectionTestUtils.setField(caffeineCacheConfig, "cacheDuration", value)
    }

    private fun setCacheTimeUnit(value: String) {
        ReflectionTestUtils.setField(caffeineCacheConfig, "cacheTimeUnit", value)
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }
}
