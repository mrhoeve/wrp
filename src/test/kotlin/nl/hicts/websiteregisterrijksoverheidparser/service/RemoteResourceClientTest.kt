package nl.hicts.websiteregisterrijksoverheidparser.service

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient

@WireMockTest
class RemoteResourceClientTest {
    private val client = RemoteResourceClient(RestClient.builder())

    @Test
    fun `getText performs a GET and returns the response body`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/register").willReturn(ok("register page")))

        assertEquals("register page", client.getText("${wireMock.httpBaseUrl}/register"))
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
    fun `getText follows HTTP redirects`(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/redirect").willReturn(temporaryRedirect("/target")))
        stubFor(get("/target").willReturn(ok("redirected response")))

        val response = client.getText("${wireMock.httpBaseUrl}/redirect")

        assertEquals("redirected response", response)
    }
}
