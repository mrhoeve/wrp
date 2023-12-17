package nl.hicts.websiteregisterrijksoverheidparser.service

import com.fasterxml.jackson.databind.ObjectMapper
import nl.hicts.websiteregisterrijksoverheidparser.model.RegisterMetadata
import org.jsoup.Jsoup
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.odftoolkit.odfdom.doc.table.OdfTable
import org.odftoolkit.odfdom.doc.table.OdfTableRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.event.EventListener
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import org.springframework.util.StreamUtils
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlin.system.exitProcess


@Service
@CacheConfig(cacheNames = ["data", "metadata"])
open class WebsiteregisterRijksoverheidService(val objectMapper: ObjectMapper) {
    companion object {
        private const val baseDomain = "https://www.communicatierijk.nl"
        const val baseResourceURL =
            "${baseDomain}/vakkennis/rijkswebsites/verplichte-richtlijnen/websiteregister-rijksoverheid"
    }

    @Autowired
    private lateinit var cacheManager: CacheManager
    private val logger = LoggerFactory.getLogger(WebsiteregisterRijksoverheidService::class.java)

    /**
     * Used semi-static variables
     * these are only changed when a (new) register is discovered
     */
    @Value("\${resourceurl:$baseResourceURL}")
    private lateinit var resourceURL: String
    @Value("\${callbackurl:}")
    private var callbackURL: String? = null
    @Value("\${callbackparameter:}")
    private var callbackparameter: String? = null

    private lateinit var domain: String
    private var tempFile: File? = null
    private var documentURL: String? = null
    private var registerMetadata: RegisterMetadata? = null

    /**
     * Variables that are used when the data is loaded
     * Once the data is places in de cache, these values are reset to null
     */
    private var spreadsheet: OdfTable? = null
    private var numberOfRows: Int = 0
    private var columnHeaders = mutableListOf<String>()
    private var data = mutableListOf<MutableMap<String, String>>()

    /**
     * Serves [registerMetadata] as json from the cache
     * When the cache doesn't contain the metadata-key, all data is reloaded into the cache
     */
    @Cacheable(cacheNames = ["metadata"])
    open fun getMetadata(): String {
        if ((cacheManager.getCache("metadata") as CaffeineCache).nativeCache.asMap()?.values?.firstOrNull() == null) {
            processFile()
        }
        return (cacheManager.getCache("metadata") as CaffeineCache).nativeCache.asMap()?.values?.first() as String
    }

    /**
     * Serves [data] as json from the cache
     * When the cache doesn't contain the data-key, all data is reloaded into the cache
     */
    @Cacheable(cacheNames = ["data"])
    open fun getRegisterData(): String {
        if ((cacheManager.getCache("data") as CaffeineCache).nativeCache.asMap()?.values?.firstOrNull() == null) {
            processFile()
        }
        val result = (cacheManager.getCache("data") as CaffeineCache).nativeCache.asMap()?.values?.first() as String
        return result
    }

    /**
     * EventListener to trigger loading of data when the application is started
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeServiceAtStartup() {
        determineDomain()
        checkForNewRegister()
    }

    /**
     * Sets the base domain URL to use
     * This is needed because the tag-scanning for the ODS-file returns a relative path
     */
    private fun determineDomain() {
        try {
            val url = URI.create(resourceURL).toURL()
            if(url.port != -1) {
                domain = url.protocol.plus("://").plus(url.host).plus(":").plus(url.port)
            } else {
                domain = url.protocol.plus("://").plus(url.host)
            }
        } catch (t: Throwable) {
            logger.error("Unable to parse resourceURL '${resourceURL}', could not determine domain. Exiting application")
            exitProcess(1)
        }
    }

    /**
     * When the application starts, this method loads the current register.
     * When the application is running, this method is scheduled to run every whole hour.
     * It can also be triggered to run by calling the /checkfornew endpoint
     * It performs a check if there's a new version of the register on the [resourceURL].
     * If it's true, is starts loading the new register.
     * When [callbackURL] is not null or blank, a callback is executed after loading the new register
     */
    @Scheduled(cron = "@hourly")
    fun checkForNewRegister() {
        logger.info("Checking for new register")
        val retrievedDocumentURL = determineDocumentURL()
        retrievedDocumentURL?.let { retrieved ->
            if (retrieved == documentURL) {
                logger.info("No new register found -- keeping current one")
                return
            }
            logger.info("Register found at URL $retrieved")
            try {
                tempFile?.let { file ->
                    val deleted = file.delete()
                    if (!deleted) logger.warn("Failure to delete file ${file.toPath()}")
                }
                tempFile = null
                documentURL = retrieved
                downloadFileToTemp(retrieved)
                clearCachedDataAndInvalidateCache()
                processFile()
                performCallback()
            } catch (t: Throwable) {
                logger.error("Unexpected error occurred.", t)
            }
        }
    }

    /**
     * Loads the [resourceURL] and searches for a tag containing the text '(ods,'.
     * When found, it retrieves the given href thus resulting in a relative path to the register.
     * This path gets prefixed with the domain, resulting in a absolute path
     */
    private fun determineDocumentURL(): String? {
        var linkToDocument: String? = null
        try {
            val doc = Jsoup.connect(resourceURL).get()
            linkToDocument =
                doc.select("a").firstOrNull { it.text().contains("(ods,", true) }?.attributes()?.get("href")
        } catch (t: Throwable) {
            logger.error("Unable to connect to $resourceURL", t)
        }
        linkToDocument?.run {
            return domain.plus(linkToDocument)
        } ?: run {
            logger.error("Could not determine link to the registerdocument")
            return null
        }
    }

    /**
     * Loads the [resourceURL] and searches for a tag containing the text '(ods,'.
     * When found, it retrieves the given href thus resulting in a relative path to the register.
     * This path gets prefixed with the domain, resulting in a absolute path
     */
    private fun performCallback() {
        if(callbackURL?.isBlank() == false) {
            try {
                val completeCallbackURL = if(callbackparameter?.isBlank() == false) {
                    "$callbackURL?$callbackparameter"
                } else {
                    "$callbackURL"
                }
                val callbackResponse = Jsoup.connect(completeCallbackURL).get()
                logger.info("Callback to $callbackURL executed, response document:\n$callbackResponse")
            } catch (t: Throwable) {
                logger.error("Unable to perform callback to $callbackURL", t)
            }
        }
    }

    /**
     * Downloads the given file at [givenDocumentURL] to a temporary file.
     */
    private fun downloadFileToTemp(givenDocumentURL: String) {
        val restTemplate = RestTemplate()
        tempFile = restTemplate.execute(URI(givenDocumentURL), HttpMethod.GET, null) { clientHttpResponse ->
            val ret: File = File.createTempFile("document", ".ods")
            tempFile = ret
            StreamUtils.copy(clientHttpResponse.getBody(), FileOutputStream(ret))
            ret
        }
    }

    /**
     * Clears all cached data and deletes the registerMetadata.
     */
    @CacheEvict(value = ["data", "metadata"], allEntries = true)
    open fun clearCachedDataAndInvalidateCache() {
        clearCachedData()
        registerMetadata = null
    }

    /**
     * Reads the [tempFile] and stores the data in the cache
     */
    private fun processFile() {
        try {
            val stopWatch = StopWatch()
            stopWatch.start()
            val document = OdfSpreadsheetDocument.loadDocument(tempFile)
            spreadsheet = document.spreadsheetTables[0]

            determineNumberOfRows()
            retrieveColumnHeaders()
            processRegisterData()

            if (registerMetadata == null) {
                createRegisterMetadata()
            }

            cacheData()
            clearCachedData()

            stopWatch.stop()
            logger.info("Found ${registerMetadata?.registersFound} registerdata, parsed in ${stopWatch.totalTimeSeconds.roundToInt()} seconds")

        } catch (t: Throwable) {
            logger.error("Unexpected error occurred.", t)
            spreadsheet = null
        }
    }

    /**
     * determines the number of rows to process, stored in cell A1 within the .ODS-file
     */
    private fun determineNumberOfRows() {
        numberOfRows = getStringFromCell(0, 0).toInt()
    }

    /**
     * Gets all the columnheaders and stores it in [columnHeaders]
     */
    private fun retrieveColumnHeaders() {
        val headerRow = 1
        var currentColumn = 0
        var currentHeader = getStringFromCell(currentColumn, headerRow)
        columnHeaders.clear()
        do {
            if (currentHeader.isNotBlank()) {
                columnHeaders.add(currentColumn, currentHeader)
                currentColumn++
                currentHeader = getStringFromCell(currentColumn, headerRow)
            }
        } while (currentHeader.isNotBlank())
    }

    /**
     * Processes each row and combines it with the correct columnhead from [columnHeaders] to form a key-value pair and stores this in [data]
     */
    private fun processRegisterData() {
        var dataInCurrentRow: OdfTableRow
        for (currentRow in 2 until 2 + numberOfRows) {
            val currentDataRow = mutableMapOf<String, String>()
            dataInCurrentRow = spreadsheet?.getRowByIndex(currentRow) ?: run { throw IllegalArgumentException() }
            for (currentColumn in 0 until columnHeaders.size) {
                currentDataRow[columnHeaders[currentColumn]] =
                    getStringFromCellInTableRow(dataInCurrentRow, currentColumn)
            }
            data.add(currentDataRow)
        }
    }

    /**
     * creates the RegisterMetadata object, storing general information
     */
    private fun createRegisterMetadata() {
        registerMetadata = RegisterMetadata(
            checkNotNull(documentURL),
            ZonedDateTime.now(ZoneId.of("UTC")),
            data.size,
            columnHeaders
        )
    }

    /**
     * Stores the RegisterMetadata and data in their respective cache
     */
    private fun cacheData() {
        cacheManager.getCache("metadata")?.put("metadata", objectMapper.writeValueAsString(registerMetadata))
        cacheManager.getCache("data")?.put("data", objectMapper.writeValueAsString(data))

    }

    /**
     * Clears the dataobjects
     */
    private fun clearCachedData() {
        spreadsheet = null
        numberOfRows = 0
        data.clear()
        columnHeaders.clear()
    }

    /**
     * Helper method to retrieve a string from a specific cell from [spreadsheet]
     */
    private fun getStringFromCell(colIndex: Int, rowIndex: Int): String {
        return spreadsheet?.getCellByPosition(colIndex, rowIndex)?.stringValue ?: run { "" }
    }

    /**
     * Helper method to retrieve a string from a specific column within the given [row]
     */
    private fun getStringFromCellInTableRow(row: OdfTableRow, colIndex: Int): String {
        return row.getCellByIndex(colIndex)?.stringValue ?: kotlin.run { "" }
    }
}
