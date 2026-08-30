package nl.hicts.websiteregisterrijksoverheidparser.service

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.util.unit.DataSize
import java.io.File
import java.time.Duration

@WireMockTest
class RemoteResourceClientTest {
    private val client = RemoteResourceClient(RestClient.builder())

    @Test
    fun `getText performs a GET and returns the response body`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/register").willReturn(ok("register page")))

        assertEquals("register page", client.getText("${wireMock.httpBaseUrl}/register"))
    }

    @Test
    fun `getText sends configured request headers`(wireMock: WireMockRuntimeInfo) {
        stubFor(
            get("/release")
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(ok("release")),
        )

        val response = client.getText(
            "${wireMock.httpBaseUrl}/release",
            mapOf("Accept" to "application/json"),
        )

        assertEquals("release", response)
    }

    @Test
    fun `downloadToTemporaryFile streams the response to disk`(wireMock: WireMockRuntimeInfo) {
        val content = "ODS content".toByteArray()
        stubFor(get("/register.ods").willReturn(ok().withBody(content)))

        val downloadedFile = client.downloadToTemporaryFile("${wireMock.httpBaseUrl}/register.ods")

        try {
            assertArrayEquals(content, downloadedFile.readBytes())
        } finally {
            assertTrue(downloadedFile.delete())
        }
    }

    @Test
    fun `downloadToTemporaryFile rejects unsuccessful responses`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/register.ods").willReturn(serverError()))

        assertThrows<IllegalStateException> {
            client.downloadToTemporaryFile("${wireMock.httpBaseUrl}/register.ods")
        }
    }

    @Test
    fun `downloadToTemporaryFile rejects responses above the configured maximum`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/large-register.ods").willReturn(ok().withBody("too much data")))
        val sizeLimitedClient = RemoteResourceClient(
            RestClient.builder(),
            maxDownloadSize = DataSize.ofBytes(5),
        )

        assertThrows<IllegalStateException> {
            sizeLimitedClient.downloadToTemporaryFile("${wireMock.httpBaseUrl}/large-register.ods")
        }
    }

    @Test
    fun `failed download schedules cleanup when immediate deletion fails`() {
        val temporaryFile = CleanupTrackingFile(deleteResult = false)

        client.cleanupFailedDownload(temporaryFile)

        assertEquals(1, temporaryFile.deleteAttempts)
        assertTrue(temporaryFile.deferredDeletion)
    }

    @Test
    fun `failed download needs no deferred cleanup after successful deletion`() {
        val temporaryFile = CleanupTrackingFile(deleteResult = true)

        client.cleanupFailedDownload(temporaryFile)

        assertEquals(1, temporaryFile.deleteAttempts)
        assertFalse(temporaryFile.deferredDeletion)
    }

    @Test
    fun `getText follows HTTP redirects`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/redirect").willReturn(temporaryRedirect("/target")))
        stubFor(get("/target").willReturn(ok("redirected response")))

        val response = client.getText("${wireMock.httpBaseUrl}/redirect")

        assertEquals("redirected response", response)
    }

    @Test
    fun `getText stops waiting after the configured read timeout`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/slow").willReturn(ok("too late").withFixedDelay(500)))
        val timeoutClient = RemoteResourceClient(
            RestClient.builder(),
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = Duration.ofMillis(100),
        )

        assertThrows<ResourceAccessException> {
            timeoutClient.getText("${wireMock.httpBaseUrl}/slow")
        }
    }

    @Test
    fun `timeouts must be greater than zero`() {
        assertThrows<IllegalArgumentException> {
            RemoteResourceClient(
                RestClient.builder(),
                connectTimeout = Duration.ZERO,
                readTimeout = Duration.ofSeconds(1),
            )
        }

        assertThrows<IllegalArgumentException> {
            RemoteResourceClient(
                RestClient.builder(),
                connectTimeout = Duration.ofSeconds(1),
                readTimeout = Duration.ofSeconds(-1),
            )
        }
        assertThrows<IllegalArgumentException> {
            RemoteResourceClient(
                RestClient.builder(),
                maxDownloadSize = DataSize.ofBytes(0),
            )
        }
    }

    private class CleanupTrackingFile(private val deleteResult: Boolean) : File("temporary-download.ods") {
        var deleteAttempts = 0
            private set
        var deferredDeletion = false
            private set

        override fun delete(): Boolean {
            deleteAttempts++
            return deleteResult
        }

        override fun deleteOnExit() {
            deferredDeletion = true
        }
    }
}
