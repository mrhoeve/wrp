package nl.hicts.websiteregisterrijksoverheidparser.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.event.EventListener
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StreamUtils
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileOutputStream
import java.net.URI


@Service
@CacheConfig(cacheNames = ["data", "metadata"])
class WebsiteregisterRijksoverheidService(
    val resourceHelperService: ResourceHelperService,
    val callbackService: CallbackService,
    val fileProcessingService: FileProcessingService,
    val exitProcessService: ExitProcessService
) {

    @Autowired
    private lateinit var cacheManager: CacheManager
    private val logger = LoggerFactory.getLogger(WebsiteregisterRijksoverheidService::class.java)

    /**
     * Used semi-static variables
     * these are only changed when a (new) register is discovered
     */
    private var tempFile: File? = null
    private var documentURL: String? = null

    /**
     * Serves [FileProcessingService.registerMetadata] as json from the cache
     * When the cache doesn't contain the metadata-key, all data is reloaded into the cache
     */
    @Cacheable(cacheNames = ["metadata"])
    fun getMetadata(): String {
        if ((cacheManager.getCache("metadata") as CaffeineCache).nativeCache.asMap()?.values?.firstOrNull() == null) {
            processFile()
        }
        return (cacheManager.getCache("metadata") as CaffeineCache).nativeCache.asMap()?.values?.first() as String
    }

    /**
     * Serves [FileProcessingService.data] as json from the cache
     * When the cache doesn't contain the data-key, all data is reloaded into the cache
     */
    @Cacheable(cacheNames = ["data"])
    fun getRegisterData(): String {
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
            resourceHelperService.determineDomain()
        } catch (t: Throwable) {
            logger.error("${t.message?.plus(" ")}Exiting application")
            exitProcessService.terminateApplicationWithError()
        }
    }

    /**
     * When the application starts, this method loads the current register.
     * When the application is running, this method is scheduled to run every whole hour.
     * It can also be triggered to run by calling the /checkfornew endpoint
     * It performs a check if there's a new version of the register on the [ResourceHelperService.resourceURL].
     * If it's true, is starts loading the new register.
     * When [CallbackService.callbackURL] is not null or blank, a callback is executed after loading the new register
     */
    @Scheduled(cron = "@hourly")
    fun checkForNewRegister() {
        logger.info("Checking for new register")
        val retrievedDocumentURL = resourceHelperService.determineDocumentURL()
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
                fileProcessingService.clearCachedDataAndInvalidateCache()
                processFile()
                callbackService.performCallback()
            } catch (t: Throwable) {
                logger.error("Unexpected error occurred.", t)
            }
        }
    }

    private fun processFile() {
        fileProcessingService.processFile(checkNotNull(tempFile), checkNotNull(documentURL))
    }

    /**
     * Downloads the given file at [givenDocumentURL] to a temporary file.
     */
    private fun downloadFileToTemp(givenDocumentURL: String) {
        val restTemplate = RestTemplate()
        tempFile = restTemplate.execute(URI(givenDocumentURL), HttpMethod.GET, null) { clientHttpResponse ->
            val ret: File = File.createTempFile("document", ".ods")
            tempFile = ret
            StreamUtils.copy(clientHttpResponse.body, FileOutputStream(ret))
            ret
        }
    }
}
