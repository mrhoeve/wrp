package nl.hicts.websiteregisterrijksoverheidparser

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "callbackparameter=token=context",
        "cacheduration=5",
        "cachetimeunit=MINUTES",
        "httpconnecttimeout=2s",
        "httpreadtimeout=5s",
        "httpmaxdownloadsize=10MB",
        "odsmaxuncompressedsize=10MB",
        "odsmaxrows=10000",
    ],
)
class ApplicationContextIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `application starts with configured properties and serves the REST contract`() {
        val client = RestClient.create("http://localhost:$port")

        val registerData = client.get().uri("/registerdata").retrieve().body(String::class.java)
        val metadata = client.get().uri("/metadata").retrieve().body(String::class.java)
        val health = client.get().uri("/health").retrieve().body(String::class.java)
        val actuatorHealth = client.get().uri("/actuator/health").retrieve().body(String::class.java)
        val checkForNew = client.get().uri("/checkfornew").retrieve().body(String::class.java)

        JSONAssert.assertEquals(EXPECTED_REGISTER_DATA, registerData, true)
        JSONAssert.assertEquals(
            """{"documentURL":"${wireMockServer.baseUrl()}/register.ods","registersFound":1}""",
            metadata,
            false,
        )
        assertEquals("UP", health)
        JSONAssert.assertEquals("""{"status":"UP"}""", actuatorHealth, false)
        assertEquals("OK", checkForNew)
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/source")))
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/register.ods")))
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/callback?token=context")))
    }

    companion object {
        private const val EXPECTED_REGISTER_DATA =
            """[{"URL":"http://www.rijksoverheid.nl","Organisatietype":"Rijksoverheid","Organisatie":"AZ","Suborganisatie":"DPC","Afdeling":"Online Advies","Bezoeken/mnd":"23.245.794","Voldoet":"ja","Websitetest Totaal":"ja","Websitetest IPv6":"ja","Websitetest DNSSEC":"ja","HTTPS":"ja","CSP":"waarschuwing","RefPol.":"ja","X-Cont.":"ja","X-Frame.":"ja","Websitetest Testdatum":"21-06-2022","E-mailtest Totaal":"ja","E-mailtest IPv6":"ja","E-mailtest DNSSEC":"ja","STARTTLS en DANE":"","DMARC":"ja","DKIM":"","SPF":"ja","E-mailtest Testdatum":"14-07-2022","Platformgebruik":"Platform Rijksoverheid Online (AZ)"}]"""

        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort()).apply {
            start()
            stubFor(
                get(urlEqualTo("/source")).willReturn(
                    ok("""<a href="/register.ods">Websiteregister</a>"""),
                ),
            )
            val register = checkNotNull(
                Thread.currentThread().contextClassLoader
                    .getResourceAsStream("__files/websiteregister-rijksoverheid-file-met-1-site.ods"),
            ).use { it.readAllBytes() }
            stubFor(get(urlEqualTo("/register.ods")).willReturn(aResponse().withBody(register)))
            stubFor(get(urlEqualTo("/callback?token=context")).willReturn(okJson("{}")))
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("resourceurl") { "${wireMockServer.baseUrl()}/source" }
            registry.add("callbackurl") { "${wireMockServer.baseUrl()}/callback" }
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMockServer.stop()
        }
    }
}
