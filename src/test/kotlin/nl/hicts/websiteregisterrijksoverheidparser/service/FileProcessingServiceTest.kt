package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.CacheManager
import java.io.File

class FileProcessingServiceTest {
    private val parser = mockk<OdsRegisterParser>()
    private val service = FileProcessingService(mockk(), parser, mockk<CacheManager>())

    /**
     * Only test the unhappy flow
     * The happy flow is tested in [WebsiteregisterRijksoverheidServiceTest].
     */
    @Test
    fun `Loading of document fails`() {
        every { parser.parse(any()) } throws Exception()

        assertThrows<Exception> {
            service.processFile(File("ignored.ods"), "mockk")
        }

        io.mockk.verify(exactly = 1) { parser.parse(any()) }
    }

}
