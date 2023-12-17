package nl.hicts.websiteregisterrijksoverheidparser.controller

import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.service.WebsiteregisterRijksoverheidService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ControllerTest {
    private val websiteregisterRijksoverheidServiceMock: WebsiteregisterRijksoverheidService = mockk()
    private val controller = Controller(websiteregisterRijksoverheidServiceMock)

    @Test
    fun `getRegisterData returns expected data`() {
        val data = "[{data: true}]"
        every { websiteregisterRijksoverheidServiceMock.getRegisterData() } returns data

        val result = controller.getRegisterData()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(data, result.body)
    }

    @Test
    fun `getMetadata returns expected data`() {
        val data = "[{data: true}]"
        every { websiteregisterRijksoverheidServiceMock.getMetadata() } returns data

        val result = controller.getMetadata()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(data, result.body)
    }

    @Test
    fun `checkForNewRegister returns expected data`() {
        val expectedValue = "OK"
        every { websiteregisterRijksoverheidServiceMock.checkForNewRegister() } returns Unit

        val result = controller.checkForNewRegister()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(expectedValue, result.body)
    }

}
