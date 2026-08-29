package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException

class ResourceHelperServiceTest {
    private val remoteResourceClient: RemoteResourceClient = mockk()

    @Test
    fun `domain could not be determined`() {
        val service = ResourceHelperService(remoteResourceClient, "not a valid URL")

        val caughtException = assertThrows<UnableToDetermineDomainException> {
            service.determineDomain()
        }

        assertTrue(!caughtException.message.isNullOrEmpty(), "Exception contains a message")
    }

    @Test
    fun `domain without port could be determined`() {
        val expectedResult = "https://eendomein.local"
        val resourceURL = "$expectedResult/eensubdomein"
        val service = ResourceHelperService(remoteResourceClient, resourceURL)
        every { remoteResourceClient.getText(resourceURL) } returns htmlWithRegisterLink("/register.ods")

        service.determineDomain()

        assertEquals("$expectedResult/register.ods", service.determineDocumentURL())
    }

    @Test
    fun `domain with port could be determined`() {
        val expectedResult = "https://eendomein.local:443"
        val resourceURL = "$expectedResult/eensubdomein"
        val service = ResourceHelperService(remoteResourceClient, resourceURL)
        every { remoteResourceClient.getText(resourceURL) } returns htmlWithRegisterLink("/register.ods")

        service.determineDomain()

        assertEquals("$expectedResult/register.ods", service.determineDocumentURL())
    }

    @Test
    fun `determineDocumentURL returns null when retrieving the source page fails`() {
        val resourceURL = "https://eendomein.local:443/eensubdomein"
        val service = ResourceHelperService(remoteResourceClient, resourceURL)
        every { remoteResourceClient.getText(resourceURL) } throws IOException()

        val result = service.determineDocumentURL()

        assertEquals(null, result)
    }

    @ParameterizedTest
    @MethodSource("linkResolutionInput")
    fun `document links are resolved against the resource URL`(link: String, expectedURL: String) {
        val resourceURL = "https://source.example/path/register-page"
        val service = ResourceHelperService(remoteResourceClient, resourceURL)
        every { remoteResourceClient.getText(resourceURL) } returns htmlWithRegisterLink(link)

        service.determineDomain()

        assertEquals(expectedURL, service.determineDocumentURL())
    }

    private fun htmlWithRegisterLink(link: String) = """<a href="$link">Register</a>"""

    companion object {
        @JvmStatic
        fun linkResolutionInput() = listOf(
            Arguments.of(
                "files/register.ods",
                "https://source.example/path/files/register.ods",
            ),
            Arguments.of(
                "//cdn.example/register.ods",
                "https://cdn.example/register.ods",
            ),
            Arguments.of(
                "https://downloads.example/register.ODS?version=2",
                "https://downloads.example/register.ODS?version=2",
            ),
        )
    }
}
