package nl.hicts.websiteregisterrijksoverheidparser.service

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import io.mockk.every
import io.mockk.mockk
import nl.hicts.websiteregisterrijksoverheidparser.exception.ExitProcessServiceCalledException
import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import nl.hicts.websiteregisterrijksoverheidparser.model.RegisterMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.KotlinModule

import java.io.IOException
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


@WireMockTest
class WebsiteregisterRijksoverheidServiceTest {
    companion object {
        private const val CACHEMANAGER_TIMEOUT = 500L
        private val CACHEMANAGER_TIMEOUT_TIMEUNIT = TimeUnit.MILLISECONDS
        private const val REGISTER_1_SITE = "websiteregister-rijksoverheid-file-met-1-site.ods"
        private const val REGISTER_2_SITES = "websiteregister-rijksoverheid-file-met-2-sites.ods"
    }

    private val objectMapper = createObjectMapperWithDeserializationOfZonedDateTime()
    private val cacheManager: CacheManager = createCacheManagerForTesting()

    private val resourceHelperService = ResourceHelperService()
    private val callbackService = CallbackService()
    private val fileProcessingService = FileProcessingService(objectMapper, OdsRegisterParser())
    private val exitProcessServiceMock: ExitProcessService = mockk()
    private val exitProcessServiceCalledExceptionMessage = "thrown from test"

    private lateinit var service: WebsiteregisterRijksoverheidService
    private lateinit var defaultDomain: String
    private lateinit var wiremockHost: String
    private lateinit var defaultResourceURL: String

    @BeforeEach
    fun setup(wmRuntimeInfo: WireMockRuntimeInfo) {
        // Setup de default domains and resourceURL
        // as defaultResourceURL we'll be using the baseResourceURL
        val defaultURI = URI(ResourceHelperService.BASE_RESOURCE_URL)
        defaultResourceURL = if (defaultURI.query != null) {
            defaultURI.path.plus("?").plus(defaultURI.query)
        } else {
            defaultURI.path
        }

        // We're using wiremock for testing, so use localhost and get the HTTP port from wiremock
        val localhost = "localhost"
        defaultDomain = "$localhost:${wmRuntimeInfo.httpPort}"
        wiremockHost = "$localhost"

        // Make sure that we won't call exitProcess
        every { exitProcessServiceMock.terminateApplicationWithError() } throws ExitProcessServiceCalledException(
            exitProcessServiceCalledExceptionMessage
        )

        // Setup service and set de cachemanager via reflection
        service = WebsiteregisterRijksoverheidService(
            resourceHelperService,
            callbackService,
            fileProcessingService,
            exitProcessServiceMock,
        )
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager)
        ReflectionTestUtils.setField(fileProcessingService, "cacheManager", cacheManager)

        createWiremockStubbing()
    }

    @Test
    fun `domain could not be determined`() {
        val resourceHelperServiceMock = mockk<ResourceHelperService>()
        service = WebsiteregisterRijksoverheidService(
            resourceHelperServiceMock,
            callbackService,
            fileProcessingService,
            exitProcessServiceMock
        )
        every { resourceHelperServiceMock.determineDomain() } throws UnableToDetermineDomainException()

        val thrownException = assertThrows<ExitProcessServiceCalledException> {
            service.initializeServiceAtStartup()
        }

        io.mockk.verify { exitProcessServiceMock.terminateApplicationWithError() }
        assertEquals(exitProcessServiceCalledExceptionMessage, thrownException.message)
    }

    @Test
    fun `Full test of service including cachemanager`() {
        setResourceURL()
        setCallbackURL()

        // Let's start
        // First, determine if startup works
        service.initializeServiceAtStartup()

        val firstCallResultMetadata = objectMapper.readValue(service.getMetadata(), RegisterMetadata::class.java)
        val firstCallResultData = service.getRegisterData()

        assertEquals("http://$defaultDomain${binaryLinkWithOneSite()}", firstCallResultMetadata.documentURL)
        assertNotNull(firstCallResultMetadata.discoveryDateTime)
        assertEquals(1, firstCallResultMetadata.registersFound)
        assertEquals(expectedHeaders(), firstCallResultMetadata.columnHeaders)
        JSONAssert.assertEquals(dataFromOneSite(), firstCallResultData, true)

        // Sleep until caches clear and get the metadata.
        // It should be completely the same as when initializing
        TimeUnit.SECONDS.sleep(1)
        val resultMetadataAfterClearingCache =
            objectMapper.readValue(service.getMetadata(), RegisterMetadata::class.java)
        assertEquals(firstCallResultMetadata, resultMetadataAfterClearingCache)

        // Sleep until caches clear and get the data.
        // This should also be the same as when initializing
        TimeUnit.SECONDS.sleep(1)
        val resultDataAfterClearingCache = service.getRegisterData()
        assertEquals(firstCallResultData, resultDataAfterClearingCache)

        // Check for a new register
        // Wiremock is configured to give a different resourceURL page so this should result in something
        service.checkForNewRegister()

        // Get the data and compare it with what's expected
        val secondCallResultMetadata = objectMapper.readValue(service.getMetadata(), RegisterMetadata::class.java)
        val secondCallResultData = service.getRegisterData()

        assertEquals("http://$defaultDomain${binaryLinkWithTwoSites()}", secondCallResultMetadata.documentURL)
        assertNotNull(secondCallResultMetadata.discoveryDateTime)
        assertEquals(2, secondCallResultMetadata.registersFound)
        assertEquals(expectedHeaders(), secondCallResultMetadata.columnHeaders)
        JSONAssert.assertEquals(dataFromTwoSites(), secondCallResultData, true)

        // Verify wiremock interaction
        // First, the resourceURL should be called for twice
        verify(2, getRequestedFor(urlEqualTo(defaultResourceURL)))
        // The callback should also be called twice (after initializing and after finding the new register)
        verify(2, getRequestedFor(urlEqualTo("/callback")))
        // Both files should have been downloaded one time
        verify(1, getRequestedFor(urlEqualTo(binaryLinkWithOneSite())))
        verify(1, getRequestedFor(urlEqualTo(binaryLinkWithTwoSites())))
    }

    @Test
    fun `Second check for new register receives the same file`() {
        setResourceURL()
        setCallbackURL()

        // Let's start
        // First, determine if startup works
        service.initializeServiceAtStartup()

        // Check for a new register
        // Wiremock is configured to give a different resourceURL page so this should result in something
        service.checkForNewRegister()

        val firstCallResultMetadata = objectMapper.readValue(service.getMetadata(), RegisterMetadata::class.java)
        val firstCallResultData = service.getRegisterData()

        // Check again for a new register
        // Wiremock is configured to give the same page as like nothing has changed
        service.checkForNewRegister()

        val secondCallResultMetadata =
            objectMapper.readValue(service.getMetadata(), RegisterMetadata::class.java)
        val secondCallResultData = service.getRegisterData()
        assertEquals(firstCallResultMetadata, secondCallResultMetadata)
        assertEquals(firstCallResultData, secondCallResultData)

        // Verify wiremock interaction
        // First, the resourceURL should be called for twice
        verify(3, getRequestedFor(urlEqualTo(defaultResourceURL)))
    }

    @Test
    fun `Test callbackparameter`() {
        setResourceURL()
        setCallbackURL()
        setCallbackparameter()

        // Let's start
        // First, determine if startup works
        service.initializeServiceAtStartup()

        // The callback should also be called twice (after initializing and after finding the new register)
        verify(1, getRequestedFor(urlEqualTo("/callback?token=xyz")))
    }


    private fun validHTMLContaingTag(binaryLinkToFile: String): String {
        return """
            <html>
                <body>
                    <a href="$binaryLinkToFile">Websiteregister Rijksoverheid (ODS, 6 KB)</a>
                </body>
            </html>
        """.trimIndent()
    }

    private fun binaryLinkWithOneSite(): String =
        createBinaryLink(REGISTER_1_SITE)

    private fun dataFromOneSite(): String = """
        [{"URL":"http://www.rijksoverheid.nl","Organisatietype":"Rijksoverheid","Organisatie":"AZ","Suborganisatie":"DPC","Afdeling":"Online Advies","Bezoeken/mnd":"23.245.794","Voldoet":"ja","Websitetest Totaal":"ja","Websitetest IPv6":"ja","Websitetest DNSSEC":"ja","HTTPS":"ja","CSP":"waarschuwing","RefPol.":"ja","X-Cont.":"ja","X-Frame.":"ja","Websitetest Testdatum":"21-06-2022","E-mailtest Totaal":"ja","E-mailtest IPv6":"ja","E-mailtest DNSSEC":"ja","STARTTLS en DANE":"","DMARC":"ja","DKIM":"","SPF":"ja","E-mailtest Testdatum":"14-07-2022","Platformgebruik":"Platform Rijksoverheid Online (AZ)"}]
    """.trimIndent()

    private fun binaryLinkWithTwoSites(): String =
        createBinaryLink(REGISTER_2_SITES)

    private fun dataFromTwoSites(): String = """
        [{"URL":"http://www.rijksoverheid.nl","Organisatietype":"Rijksoverheid","Organisatie":"AZ","Suborganisatie":"DPC","Afdeling":"Online Advies","Bezoeken/mnd":"23.245.794","Voldoet":"ja","Websitetest Totaal":"ja","Websitetest IPv6":"ja","Websitetest DNSSEC":"ja","HTTPS":"ja","CSP":"waarschuwing","RefPol.":"ja","X-Cont.":"ja","X-Frame.":"ja","Websitetest Testdatum":"21-06-2022","E-mailtest Totaal":"ja","E-mailtest IPv6":"ja","E-mailtest DNSSEC":"ja","STARTTLS en DANE":"","DMARC":"ja","DKIM":"","SPF":"ja","E-mailtest Testdatum":"14-07-2022","Platformgebruik":"Platform Rijksoverheid Online (AZ)"},{"URL":"http://www.nederlandwereldwijd.nl","Organisatietype":"Rijksoverheid","Organisatie":"BUZA","Suborganisatie":"","Afdeling":"","Bezoeken/mnd":"6.520.737","Voldoet":"ja","Websitetest Totaal":"ja","Websitetest IPv6":"ja","Websitetest DNSSEC":"ja","HTTPS":"ja","CSP":"ja","RefPol.":"ja","X-Cont.":"ja","X-Frame.":"ja","Websitetest Testdatum":"21-06-2022","E-mailtest Totaal":"nee","E-mailtest IPv6":"ja","E-mailtest DNSSEC":"ja","STARTTLS en DANE":"","DMARC":"ja","DKIM":"nee","SPF":"nee","E-mailtest Testdatum":"14-07-2022","Platformgebruik":"Platform Rijksoverheid Online (AZ)"}]
    """.trimIndent()

    private fun createBinaryLink(linkToFile: String): String =
        "/binaries/communicatierijk/documenten/publicaties/websiteregister/$linkToFile"

    private fun expectedHeaders(): List<String> = listOf(
        "URL",
        "Organisatietype",
        "Organisatie",
        "Suborganisatie",
        "Afdeling",
        "Bezoeken/mnd",
        "Voldoet",
        "Websitetest Totaal",
        "Websitetest IPv6",
        "Websitetest DNSSEC",
        "HTTPS",
        "CSP",
        "RefPol.",
        "X-Cont.",
        "X-Frame.",
        "Websitetest Testdatum",
        "E-mailtest Totaal",
        "E-mailtest IPv6",
        "E-mailtest DNSSEC",
        "STARTTLS en DANE",
        "DMARC",
        "DKIM",
        "SPF",
        "E-mailtest Testdatum",
        "Platformgebruik"
    )

    private fun setResourceURL(resourceURL: String? = null) {
        ReflectionTestUtils.setField(
            resourceHelperService,
            "resourceURL",
            resourceURL ?: "http://$defaultDomain$defaultResourceURL"
        )
    }

    private fun setCallbackURL() {
        ReflectionTestUtils.setField(
            callbackService,
            "callbackURL",
            "http://$defaultDomain/callback"
        )
    }

    private fun setCallbackparameter() {
        ReflectionTestUtils.setField(
            callbackService,
            "callbackparameter",
            "token=xyz"
        )
    }

    private fun createWiremockStubbing() {
        // Stubbing for defaultResourceURL
        // First time an HTML page will be returned containing the link to the file with 1 registerdata
        stubFor(
            get(defaultResourceURL).inScenario("ResourceURL")
                .whenScenarioStateIs(STARTED)
                .willReturn(okTextXml(validHTMLContaingTag(binaryLinkWithOneSite())))
                .willSetStateTo("Serve second resource")
        )

        // The second time it will contain the link to the file with 2 registerdata
        stubFor(
            get(defaultResourceURL).inScenario("ResourceURL")
                .whenScenarioStateIs("Serve second resource")
                .willReturn(okTextXml(validHTMLContaingTag(binaryLinkWithTwoSites())))
        )

        // Give an 200 response on the callback
        stubFor(
            get("/callback")
                .withHost(equalTo(wiremockHost))
                .willReturn(ok())
        )

        // Give an 200 response on the callback with token
        stubFor(
            get("/callback?token=xyz")
                .withHost(equalTo(wiremockHost))
                .willReturn(ok())
        )

        // Serve the first file when requested
        stubFor(
            get(binaryLinkWithOneSite())
                .withHost(equalTo(wiremockHost))
                .willReturn(aResponse().withBodyFile("$REGISTER_1_SITE"))
        )

        // Serve the second file when requested
        stubFor(
            get(binaryLinkWithTwoSites())
                .withHost(equalTo(wiremockHost))
                .willReturn(aResponse().withBodyFile("$REGISTER_2_SITES"))
        )
    }

    // Create an specialized objectMapper including deserialization of ZonedDateTime (used in testing only)
    private fun createObjectMapperWithDeserializationOfZonedDateTime(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(SimpleModule().addDeserializer(ZonedDateTime::class.java, ZonedDateTimeDeserializer()))
            .disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
            .build()
    }

    // Create a specialized cachemanager for testing
    private fun createCacheManagerForTesting(): CacheManager {
        val cacheManager = CaffeineCacheManager("data", "metadata")
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .initialCapacity(2500)
            .maximumSize(3000)
            .expireAfterAccess(CACHEMANAGER_TIMEOUT, CACHEMANAGER_TIMEOUT_TIMEUNIT)
            .weakKeys()
    }

    // Class used for deserialization of ZonedDateTime
    class ZonedDateTimeDeserializer : ValueDeserializer<ZonedDateTime?>() {
        @Throws(IOException::class)
        override fun deserialize(
            jsonParser: JsonParser,
            deserializationContext: DeserializationContext?
        ): ZonedDateTime {
            val localDate: LocalDateTime = LocalDateTime.parse(
                jsonParser.text,
                DateTimeFormatter.ISO_DATE_TIME
            )
            return localDate.atZone(ZoneOffset.UTC)
        }
    }
}
