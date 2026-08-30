package nl.hicts.websiteregisterrijksoverheidparser.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.hicts.websiteregisterrijksoverheidparser.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.info.BuildProperties
import java.io.IOException
import java.util.Properties

class VersionServiceTest {
    private val remoteResourceClient: RemoteResourceClient = mockk()

    @Test
    fun `version information is unknown before the first successful check`() {
        val service = createService("2.0.0")

        val version = service.getVersionInfo()

        assertEquals("2.0.0", version.version)
        assertNull(version.latestVersion)
        assertNull(version.updateAvailable)
        assertNull(version.checkedAt)
    }

    @Test
    fun `newer published release is reported as available`() {
        every { remoteResourceClient.getText(any(), any()) } returns """{"tag_name":"v2.1.0"}"""
        val service = createService("2.0.0")

        service.checkForUpdate()
        val version = service.getVersionInfo()

        assertEquals("2.1.0", version.latestVersion)
        assertTrue(version.updateAvailable == true)
        assertNotNull(version.checkedAt)
    }

    @Test
    fun `equal or older published release is not reported as available`() {
        every { remoteResourceClient.getText(any(), any()) } returns """{"tag_name":"2.0.0"}"""
        val service = createService("2.1.0-SNAPSHOT")

        service.checkForUpdate()

        assertFalse(service.getVersionInfo().updateAvailable == true)
    }

    @Test
    fun `legacy release version remains comparable during migration`() {
        every { remoteResourceClient.getText(any(), any()) } returns """{"tag_name":"1.8"}"""
        val service = createService("1.8")

        service.checkForUpdate()

        assertEquals("1.8", service.getVersionInfo().latestVersion)
        assertFalse(service.getVersionInfo().updateAvailable == true)
    }

    @Test
    fun `failed check preserves the last successful result`() {
        every { remoteResourceClient.getText(any(), any()) } returns """{"tag_name":"2.1.0"}"""
        val service = createService("2.0.0")
        service.checkForUpdate()
        val successfulResult = service.getVersionInfo()
        every { remoteResourceClient.getText(any(), any()) } throws IOException("unavailable")

        service.checkForUpdate()

        assertEquals(successfulResult, service.getVersionInfo())
    }

    @Test
    fun `disabled update check performs no request`() {
        val service = createService("2.0.0", enabled = false)

        service.checkForUpdate()

        verify(exactly = 0) { remoteResourceClient.getText(any(), any()) }
        assertNull(service.getVersionInfo().updateAvailable)
    }

    @Test
    fun `invalid release response leaves update status unknown`() {
        every { remoteResourceClient.getText(any(), any()) } returns """{"tag_name":"not-semver"}"""
        val service = createService("2.0.0")

        service.checkForUpdate()

        assertNull(service.getVersionInfo().updateAvailable)
    }

    private fun createService(version: String, enabled: Boolean = true): VersionService {
        val properties = Properties().apply { setProperty("version", version) }
        return VersionService(
            remoteResourceClient = remoteResourceClient,
            objectMapper = objectMapper(),
            buildProperties = BuildProperties(properties),
            updateCheckEnabled = enabled,
            updateCheckURL = "https://api.example/releases/latest",
        )
    }
}
