package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.nio.file.Files

class WebsiteregisterLifecycleTest {
    @Test
    fun `shutdown deletes the active temporary register file`() {
        val documentURL = "https://example.test/register.ods"
        val downloadedFile = Files.createTempFile("wrp-active-register-", ".ods").toFile()
        val service = WebsiteregisterRijksoverheidService(
            mockk { every { determineDocumentURL() } returns documentURL },
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk { every { downloadToTemporaryFile(documentURL) } returns downloadedFile },
            ConcurrentMapCacheManager(RegisterCache.DATA, RegisterCache.METADATA),
        )

        service.checkForNewRegister()
        assertTrue(downloadedFile.exists())

        service.cleanupTemporaryFile()
        service.cleanupTemporaryFile()

        assertFalse(downloadedFile.exists())
    }
}
