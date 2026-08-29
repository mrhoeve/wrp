package nl.hicts.websiteregisterrijksoverheidparser.service

import tools.jackson.databind.ObjectMapper
import nl.hicts.websiteregisterrijksoverheidparser.model.RegisterMetadata
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
class FileProcessingService(
    private val objectMapper: ObjectMapper,
    private val odsRegisterParser: OdsRegisterParser
) {
    @Autowired
    private lateinit var cacheManager: CacheManager
    private val logger = LoggerFactory.getLogger(FileProcessingService::class.java)

    private var registerMetadata: RegisterMetadata? = null

    /**
     * Reads the [tempFile] and stores the data in the cache
     */
    fun processFile(tempFile: File, documentURL: String) {
        try {
            val stopWatch = StopWatch()
            stopWatch.start()
            val parsedRegister = odsRegisterParser.parse(tempFile)

            if (registerMetadata == null) {
                createRegisterMetadata(documentURL, parsedRegister.records.size, parsedRegister.columnHeaders)
            }

            cacheData(parsedRegister.records)

            stopWatch.stop()
            logger.info("Found ${registerMetadata?.registersFound} registerdata, parsed in ${stopWatch.totalTimeSeconds.roundToInt()} seconds")

        } catch (t: Throwable) {
            logger.error("Unexpected error occurred.", t)
        }
    }

    /**
     * creates the RegisterMetadata object, storing general information
     */
    private fun createRegisterMetadata(documentURL: String, registersFound: Int, columnHeaders: List<String>) {
        registerMetadata = RegisterMetadata(
            documentURL,
            ZonedDateTime.now(ZoneId.of("UTC")),
            registersFound,
            columnHeaders
        )
    }

    /**
     * Stores the RegisterMetadata and data in their respective cache
     */
    private fun cacheData(data: List<Map<String, String>>) {
        cacheManager.getCache("metadata")?.put("metadata", objectMapper.writeValueAsString(registerMetadata))
        cacheManager.getCache("data")?.put("data", objectMapper.writeValueAsString(data))
    }

    /**
     * Clears all cached data and deletes the registerMetadata.
     */
    @CacheEvict(value = ["data", "metadata"], allEntries = true)
    fun clearCachedDataAndInvalidateCache() {
        registerMetadata = null
    }

}
