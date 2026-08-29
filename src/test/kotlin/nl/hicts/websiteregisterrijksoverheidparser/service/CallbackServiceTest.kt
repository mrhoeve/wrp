package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.IOException

class CallbackServiceTest {
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

    @Test
    fun `performCallback receives exception and handles it correctly`() {
        val service = CallbackService(remoteResourceClient, callbackURL, "")
        every { remoteResourceClient.getText(callbackURL) } throws IOException()

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(callbackURL) }
    }
}
