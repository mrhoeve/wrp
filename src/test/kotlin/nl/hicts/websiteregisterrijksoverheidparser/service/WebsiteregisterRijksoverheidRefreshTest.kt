package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.io.IOException
import java.nio.file.Files

class WebsiteregisterRijksoverheidRefreshTest {
    @Test
    fun `failed parsing keeps cached register and retries the document`() {
        val documentUrl = "https://example.test/register.ods"
        val firstDownload = Files.createTempFile("wrp-failed-refresh-", ".ods").toFile()
        val secondDownload = Files.createTempFile("wrp-failed-refresh-", ".ods").toFile()
        val resourceHelperService = mockk<ResourceHelperService> {
            every { determineDocumentURL() } returns documentUrl
        }
        val callbackService = mockk<CallbackService>(relaxed = true)
        val fileProcessingService = mockk<FileProcessingService> {
            every { processFile(any(), documentUrl) } throws IOException("Invalid ODS document")
        }
        val remoteResourceClient = mockk<RemoteResourceClient> {
            every { downloadToTemporaryFile(documentUrl) } returnsMany listOf(firstDownload, secondDownload)
        }
        val cacheManager = ConcurrentMapCacheManager(RegisterCache.DATA, RegisterCache.METADATA)
        cacheManager.getCache(RegisterCache.DATA)?.put(RegisterCache.DATA, "old data")
        cacheManager.getCache(RegisterCache.METADATA)?.put(RegisterCache.METADATA, "old metadata")
        val service = WebsiteregisterRijksoverheidService(
            resourceHelperService,
            callbackService,
            fileProcessingService,
            remoteResourceClient,
            cacheManager,
        )

        service.checkForNewRegister()
        service.checkForNewRegister()

        assertEquals("old data", service.getRegisterData())
        assertEquals("old metadata", service.getMetadata())
        assertFalse(firstDownload.exists())
        assertFalse(secondDownload.exists())
        verify(exactly = 2) { remoteResourceClient.downloadToTemporaryFile(documentUrl) }
        verify(exactly = 0) { callbackService.performCallback() }
    }
}
