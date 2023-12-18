package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.io.IOException

class ResourceHelperServiceTest {
    private val service = ResourceHelperService()

    @Test
    fun `domain could not be determined`() {
        val invalidResourceURL = "not a valid URL"

        setResourceURL(invalidResourceURL)

        val catchedException = assertThrows<UnableToDetermineDomainException> {
            service.determineDomain()
        }
        assertTrue(!catchedException.message.isNullOrEmpty(), "Exception contains a message")
    }

    @Test
    fun `domain without port could be determined`() {
        val expectedResult = "https://eendomein.local"
        val resourceURL = "$expectedResult/eensubdomein"

        setResourceURL(resourceURL)

        service.determineDomain()

        val result = ReflectionTestUtils.getField(service, "domain") as String

        assertEquals(expectedResult, result)
    }

    @Test
    fun `domain with port could be determined`() {
        val expectedResult = "https://eendomein.local:443"
        val resourceURL = "$expectedResult/eensubdomein"

        setResourceURL(resourceURL)

        service.determineDomain()

        val result = ReflectionTestUtils.getField(service, "domain") as String

        assertEquals(expectedResult, result)
    }

    /**
     * Only test the unhappy path. The happy path is tested in [WebsiteregisterRijksoverheidServiceTest]
     */
    @Test
    fun `determineDocumentURL fails`() {
        setResourceURL("https://eendomein.local:443/eensubdomein")

        val connectionMock = mockk<org.jsoup.Connection>()
        mockkStatic(Jsoup::class)
        every { Jsoup.connect(any()) } returns connectionMock
        every { connectionMock.get() } throws IOException()

        val result = service.determineDocumentURL()

        assertEquals(null, result)
    }

    private fun setResourceURL(resourceURL: String) {
        ReflectionTestUtils.setField(
            service,
            "resourceURL",
            resourceURL
        )
    }

    @AfterEach
    fun `remove all static mockks`() {
        unmockkStatic(Jsoup::class)
    }
}
