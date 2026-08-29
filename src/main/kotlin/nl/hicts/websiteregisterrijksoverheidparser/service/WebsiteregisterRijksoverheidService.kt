package nl.hicts.websiteregisterrijksoverheidparser.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File


@Service
class WebsiteregisterRijksoverheidService(
    val resourceHelperService: ResourceHelperService,
    val callbackService: CallbackService,
    val fileProcessingService: FileProcessingService,
    val exitProcessService: ExitProcessService,
    private val remoteResourceClient: RemoteResourceClient,
    private val cacheManager: CacheManager,
) {
    private val logger = LoggerFactory.getLogger(WebsiteregisterRijksoverheidService::class.java)

    /**
     * Used semi-static variables
     * these are only changed when a (new) register is discovered
     */
    private var tempFile: File? = null
    private var documentURL: String? = null

    /**
     * Serves [FileProcessingService.registerMetadata] as JSON from the cache
     * When the cache doesn't contain the metadata-key, all data is reloaded into the cache
     */
    fun getMetadata(): String = getCachedValue(RegisterCache.METADATA)

    /**
     * Serves the parsed register data as JSON from the cache
     * When the cache doesn't contain the data-key, all data is reloaded into the cache
     */
    fun getRegisterData(): String = getCachedValue(RegisterCache.DATA)

    private fun getCachedValue(cacheName: String): String {
        cacheManager.getCache(cacheName)?.get(cacheName, String::class.java)?.let { return it }
        processFile()
        return checkNotNull(cacheManager.getCache(cacheName)?.get(cacheName, String::class.java)) {
            "Cache '$cacheName' was not populated after processing the register"
        }
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
    @Synchronized
    fun checkForNewRegister() {
        logger.info("Checking for new register")
        val retrievedDocumentURL = resourceHelperService.determineDocumentURL()
        retrievedDocumentURL?.let { retrieved ->
            if (retrieved == documentURL) {
                logger.info("No new register found -- keeping current one")
                return
            }
            logger.info("Register found at URL $retrieved")
            var downloadedFile: File? = null
            try {
                downloadedFile = remoteResourceClient.downloadToTemporaryFile(retrieved)
                fileProcessingService.processFile(downloadedFile, retrieved)

                val previousFile = tempFile
                tempFile = downloadedFile
                documentURL = retrieved
                downloadedFile = null
                deleteTemporaryFile(previousFile)
                callbackService.performCallback()
            } catch (exception: Exception) {
                deleteTemporaryFile(downloadedFile)
                logger.error("Unable to load register from $retrieved; keeping the current register.", exception)
            }
        }
    }

    private fun processFile() {
        fileProcessingService.processFile(checkNotNull(tempFile), checkNotNull(documentURL))
    }

    private fun deleteTemporaryFile(file: File?) {
        file?.let {
            val deleted = it.delete()
            if (!deleted) logger.warn("Failure to delete file ${it.toPath()}")
        }
    }
}
