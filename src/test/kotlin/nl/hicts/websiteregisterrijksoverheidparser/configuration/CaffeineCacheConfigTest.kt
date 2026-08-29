package nl.hicts.websiteregisterrijksoverheidparser.configuration

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit

class CaffeineCacheConfigTest {
    private val caffeineBuilderMock = mockk<Caffeine<Any, Any>>()
    private val caffeineCacheMock = mockk<Cache<Any, Any>>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Caffeine::class)
        every { Caffeine.newBuilder() } returns caffeineBuilderMock
        every { caffeineBuilderMock.expireAfterAccess(any(), any()) } returns caffeineBuilderMock
        every { caffeineBuilderMock.build<Any, Any>() } returns caffeineCacheMock
    }

    @Test
    fun `test default configuration`() {
        CaffeineCacheConfig("", "").cacheManager()

        verify { caffeineBuilderMock.expireAfterAccess(15L, TimeUnit.MINUTES) }
    }

    @ParameterizedTest
    @MethodSource("nonDefaultConfigurationTestInput")
    fun `test non default configuration`(duration: Long, timeUnit: TimeUnit) {
        CaffeineCacheConfig(duration.toString(), timeUnit.toString()).cacheManager()

        verify { caffeineBuilderMock.expireAfterAccess(duration, timeUnit) }
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }

    companion object {
        @JvmStatic
        fun nonDefaultConfigurationTestInput() = listOf(
            Arguments.of(10L, TimeUnit.SECONDS),
            Arguments.of(20L, TimeUnit.HOURS),
            Arguments.of(5L, TimeUnit.DAYS)
        )
    }
}
