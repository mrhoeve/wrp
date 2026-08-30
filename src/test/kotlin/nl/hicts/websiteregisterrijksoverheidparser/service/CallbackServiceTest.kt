package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.util.stream.Stream

class CallbackServiceTest {
    companion object {
        @JvmStatic
        fun callbackURLs(): Stream<Arguments> = Stream.of(
            Arguments.of("http://localhost", "token=xyz", "http://localhost?token=xyz"),
            Arguments.of("http://localhost?source=wrp", "token=xyz", "http://localhost?source=wrp&token=xyz"),
            Arguments.of("http://localhost?", "token=xyz", "http://localhost?token=xyz"),
            Arguments.of("http://localhost?source=wrp&", "token=xyz", "http://localhost?source=wrp&token=xyz"),
            Arguments.of("http://localhost#result", "token=xyz", "http://localhost?token=xyz#result"),
        )
    }

    private val callbackURL = "http://localhost"
    private val callbackParams = "test"
    private val remoteResourceClient: RemoteResourceClient = mockk()

    @Test
    fun `performCallback without specified callbackURL does nothing`() {
        val service = CallbackService(remoteResourceClient, "", "")

        service.performCallback()

        verify(exactly = 0) { remoteResourceClient.getText(any()) }
    }

    @Test
    fun `performCallback without params succeeds`() {
        val service = CallbackService(remoteResourceClient, callbackURL, "")
        every { remoteResourceClient.getText(callbackURL) } returns "<html></html>"

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(callbackURL) }
    }

    @Test
    fun `performCallback with params succeeds`() {
        val service = CallbackService(remoteResourceClient, callbackURL, callbackParams)
        every { remoteResourceClient.getText("$callbackURL?$callbackParams") } returns "<html></html>"

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText("$callbackURL?$callbackParams") }
    }

    @ParameterizedTest
    @MethodSource("callbackURLs")
    fun `performCallback preserves existing query parameters`(
        configuredURL: String,
        configuredParameter: String,
        expectedURL: String,
    ) {
        val service = CallbackService(remoteResourceClient, configuredURL, configuredParameter)
        every { remoteResourceClient.getText(expectedURL) } returns "response body is ignored"

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(expectedURL) }
    }

    @Test
    fun `performCallback receives exception and handles it correctly`() {
        val service = CallbackService(remoteResourceClient, callbackURL, "")
        every { remoteResourceClient.getText(callbackURL) } throws IOException()

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(callbackURL) }
    }
}
