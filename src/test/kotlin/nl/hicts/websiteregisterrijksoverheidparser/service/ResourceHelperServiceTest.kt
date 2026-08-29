package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.io.IOException

class ResourceHelperServiceTest {
    private val remoteResourceClient: RemoteResourceClient = mockk()
    private val service = ResourceHelperService(remoteResourceClient)

    @Test
    fun `domain could not be determined`() {
        setResourceURL("not a valid URL")

        val caughtException = assertThrows<UnableToDetermineDomainException> {
            service.determineDomain()
        }

        assertTrue(!caughtException.message.isNullOrEmpty(), "Exception contains a message")
    }

    @Test
    fun `domain without port could be determined`() {
        val expectedResult = "https://eendomein.local"
        setResourceURL("$expectedResult/eensubdomein")

        service.determineDomain()

        assertEquals(expectedResult, ReflectionTestUtils.getField(service, "domain"))
    }

    @Test
    fun `domain with port could be determined`() {
        val expectedResult = "https://eendomein.local:443"
        setResourceURL("$expectedResult/eensubdomein")

        service.determineDomain()

        assertEquals(expectedResult, ReflectionTestUtils.getField(service, "domain"))
    }

    @Test
    fun `determineDocumentURL returns null when retrieving the source page fails`() {
        val resourceURL = "https://eendomein.local:443/eensubdomein"
        setResourceURL(resourceURL)
        every { remoteResourceClient.getText(resourceURL) } throws IOException()

        val result = service.determineDocumentURL()

        assertEquals(null, result)
    }

    private fun setResourceURL(resourceURL: String) {
        ReflectionTestUtils.setField(service, "resourceURL", resourceURL)
    }
}
