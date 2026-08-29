package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.io.IOException

class CallbackServiceTest {
    private val callbackURL = "http://localhost"
    private val callbackParams = "test"
    private val remoteResourceClient: RemoteResourceClient = mockk()
    private val service = CallbackService(remoteResourceClient)

    @Test
    fun `performCallback without specified callbackURL does nothing`() {
        service.performCallback()

        verify(exactly = 0) { remoteResourceClient.getText(any()) }
    }

    @Test
    fun `performCallback without params succeeds`() {
        setupCallbackURL()
        every { remoteResourceClient.getText(callbackURL) } returns "<html></html>"

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(callbackURL) }
    }

    @Test
    fun `performCallback with params succeeds`() {
        setupCallbackURL()
        setupCallbackParams()
        every { remoteResourceClient.getText("$callbackURL?$callbackParams") } returns "<html></html>"

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText("$callbackURL?$callbackParams") }
    }

    @Test
    fun `performCallback receives exception and handles it correctly`() {
        setupCallbackURL()
        every { remoteResourceClient.getText(callbackURL) } throws IOException()

        service.performCallback()

        verify(exactly = 1) { remoteResourceClient.getText(callbackURL) }
    }

    private fun setupCallbackURL() {
        ReflectionTestUtils.setField(service, "callbackURL", callbackURL)
    }

    private fun setupCallbackParams() {
        ReflectionTestUtils.setField(service, "callbackparameter", callbackParams)
    }
}
