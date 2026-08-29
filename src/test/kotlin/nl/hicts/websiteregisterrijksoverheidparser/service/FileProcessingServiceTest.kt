package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

class FileProcessingServiceTest {
    private val parser = mockk<OdsRegisterParser>()
    private val service = FileProcessingService(mockk(), parser, mockk<CacheManager>())

    /**
     * Only test the unhappy flow
     * The happy flow gets testen in [WebsiteregisterRijksoverheidServiceTest]
     */
    @Test
    fun `Loading of document fails`() {
        every { parser.parse(any()) } throws Exception()

        assertThrows<Exception> {
            service.processFile(mockk(), "mockk")
        }

        io.mockk.verify(exactly = 1) { parser.parse(any()) }
    }

    @Test
    fun `clearing register data clears both caches`() {
        val dataCache = mockk<Cache>(relaxed = true)
        val metadataCache = mockk<Cache>(relaxed = true)
        val cacheManager = mockk<CacheManager> {
            every { getCache(RegisterCache.DATA) } returns dataCache
            every { getCache(RegisterCache.METADATA) } returns metadataCache
        }
        val service = FileProcessingService(mockk(), parser, cacheManager)

        service.clearCachedDataAndInvalidateCache()

        verify(exactly = 1) { dataCache.clear() }
        verify(exactly = 1) { metadataCache.clear() }
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }
}
