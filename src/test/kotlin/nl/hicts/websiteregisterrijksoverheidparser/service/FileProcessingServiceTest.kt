package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class FileProcessingServiceTest {
    private val parser = mockk<OdsRegisterParser>()
    private val service = FileProcessingService(mockk(), parser)

    /**
     * Only test the unhappy flow
     * The happy flow gets testen in [WebsiteregisterRijksoverheidServiceTest]
     */
    @Test
    fun `Loading of document fails`() {
        every { parser.parse(any()) } throws Exception()

        service.processFile(mockk(), "mockk")

        io.mockk.verify(exactly = 1) { parser.parse(any()) }
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }
}
