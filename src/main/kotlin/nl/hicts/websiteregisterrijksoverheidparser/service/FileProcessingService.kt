package nl.hicts.websiteregisterrijksoverheidparser.service

import com.fasterxml.jackson.databind.ObjectMapper
import nl.hicts.websiteregisterrijksoverheidparser.model.RegisterMetadata
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.odftoolkit.odfdom.doc.table.OdfTable
import org.odftoolkit.odfdom.doc.table.OdfTableRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Service
@CacheConfig(cacheNames = ["data", "metadata"])
class FileProcessingService(val objectMapper: ObjectMapper) {
    @Autowired
    private lateinit var cacheManager: CacheManager
    private val logger = LoggerFactory.getLogger(WebsiteregisterRijksoverheidService::class.java)

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
     * Reads the [tempFile] and stores the data in the cache
     */
    fun processFile(tempFile: File, documentURL: String) {
        try {
            val stopWatch = StopWatch()
            stopWatch.start()
            val document = OdfSpreadsheetDocument.loadDocument(tempFile)
            spreadsheet = document.spreadsheetTables[0]

            determineNumberOfRows()
            retrieveColumnHeaders()
            processRegisterData()

            if (registerMetadata == null) {
                createRegisterMetadata(documentURL)
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
    private fun createRegisterMetadata(documentURL: String) {
        registerMetadata = RegisterMetadata(
            documentURL,
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

    /**
     * Clears all cached data and deletes the registerMetadata.
     */
    @CacheEvict(value = ["data", "metadata"], allEntries = true)
    fun clearCachedDataAndInvalidateCache() {
        clearCachedData()
        registerMetadata = null
    }

}
