package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.odftoolkit.odfdom.doc.table.OdfTable
import org.springframework.test.util.ReflectionTestUtils
import java.io.File

class FileProcessingServiceTest {
    private val service = FileProcessingService(mockk())

    /**
     * Only test the unhappy flow
     * The happy flow gets testen in [WebsiteregisterRijksoverheidServiceTest]
     */
    @Test
    fun `Loading of document fails`() {
        // Place some mock in the spreadsheet variable
        // After handeling the exception, this variable must be null
        ReflectionTestUtils.setField(service, "spreadsheet", mockk<OdfTable>())
        val initial = ReflectionTestUtils.getField(service, "spreadsheet")

        // throw exception when called
        mockkStatic(OdfSpreadsheetDocument::class)
        every { OdfSpreadsheetDocument.loadDocument(any(File::class)) } throws Exception()

        service.processFile(mockk(), "mockk")

        val result = ReflectionTestUtils.getField(service, "spreadsheet")

        assertTrue(initial !== null)
        assertTrue(result === null)
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkAll()
    }
}
