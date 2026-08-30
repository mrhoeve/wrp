package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebsiteregisterCacheConcurrencyTest {
    @Test
    fun `concurrent cache misses reload the register only once`() {
        val documentURL = "https://example.test/register.ods"
        val downloadedFile = Files.createTempFile("wrp-concurrent-cache-", ".ods").toFile()
        val cacheManager = ConcurrentMapCacheManager(RegisterCache.DATA, RegisterCache.METADATA)
        val reloadStarted = CountDownLatch(1)
        val secondRequestStarted = CountDownLatch(1)
        val allowReloadToFinish = CountDownLatch(1)
        var processingCount = 0
        val fileProcessingService = mockk<FileProcessingService> {
            every { processFile(downloadedFile, documentURL) } answers {
                processingCount++
                if (processingCount > 1) {
                    reloadStarted.countDown()
                    check(allowReloadToFinish.await(5, TimeUnit.SECONDS))
                }
                cacheManager.getCache(RegisterCache.DATA)?.put(RegisterCache.DATA, "register data")
                cacheManager.getCache(RegisterCache.METADATA)?.put(RegisterCache.METADATA, "metadata")
            }
        }
        val service = WebsiteregisterRijksoverheidService(
            mockk { every { determineDocumentURL() } returns documentURL },
            mockk(relaxed = true),
            fileProcessingService,
            mockk { every { downloadToTemporaryFile(documentURL) } returns downloadedFile },
            cacheManager,
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            service.checkForNewRegister()
            cacheManager.getCache(RegisterCache.DATA)?.clear()

            val firstRequest = executor.submit<String> { service.getRegisterData() }
            check(reloadStarted.await(5, TimeUnit.SECONDS))
            val secondRequest = executor.submit<String> {
                secondRequestStarted.countDown()
                service.getRegisterData()
            }
            check(secondRequestStarted.await(5, TimeUnit.SECONDS))
            allowReloadToFinish.countDown()

            assertEquals("register data", firstRequest.get(5, TimeUnit.SECONDS))
            assertEquals("register data", secondRequest.get(5, TimeUnit.SECONDS))
            verify(exactly = 2) { fileProcessingService.processFile(downloadedFile, documentURL) }
        } finally {
            allowReloadToFinish.countDown()
            executor.shutdownNow()
            downloadedFile.delete()
        }
    }
}
