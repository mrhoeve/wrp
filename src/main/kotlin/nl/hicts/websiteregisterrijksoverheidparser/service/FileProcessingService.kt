package nl.hicts.websiteregisterrijksoverheidparser.service

import tools.jackson.databind.ObjectMapper
import nl.hicts.websiteregisterrijksoverheidparser.model.RegisterMetadata
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Service
class FileProcessingService(
    private val objectMapper: ObjectMapper,
    private val odsRegisterParser: OdsRegisterParser,
    private val cacheManager: CacheManager,
) {
    private val logger = LoggerFactory.getLogger(FileProcessingService::class.java)

    private var registerMetadata: RegisterMetadata? = null

    /**
     * Reads the [tempFile] and stores the data in the cache
     */
    @Synchronized
    fun processFile(tempFile: File, documentURL: String) {
        val stopWatch = StopWatch()
        stopWatch.start()
        val parsedRegister = odsRegisterParser.parse(tempFile)
        val metadata = registerMetadata
            ?.takeIf { it.documentURL == documentURL }
            ?: RegisterMetadata(
                documentURL,
                ZonedDateTime.now(ZoneOffset.UTC),
                parsedRegister.records.size,
                parsedRegister.columnHeaders,
            )

        cacheData(metadata, parsedRegister.records)
        registerMetadata = metadata

        stopWatch.stop()
        logger.info(
            "Found ${metadata.registersFound} registerdata, " +
                "parsed in ${stopWatch.totalTimeSeconds.roundToInt()} seconds",
        )
    }

    /**
     * Stores the RegisterMetadata and data in their respective cache
     */
    private fun cacheData(metadata: RegisterMetadata, data: List<Map<String, String>>) {
        val serializedMetadata = objectMapper.writeValueAsString(metadata)
        val serializedData = objectMapper.writeValueAsString(data)

        cacheManager.getCache(RegisterCache.METADATA)
            ?.put(RegisterCache.METADATA, serializedMetadata)
        cacheManager.getCache(RegisterCache.DATA)
            ?.put(RegisterCache.DATA, serializedData)
    }

}
