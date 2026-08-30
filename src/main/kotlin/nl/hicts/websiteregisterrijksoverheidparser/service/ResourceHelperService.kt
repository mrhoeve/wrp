package nl.hicts.websiteregisterrijksoverheidparser.service

import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class ResourceHelperService(
    private val remoteResourceClient: RemoteResourceClient,
    @param:Value("\${resourceurl:$BASE_RESOURCE_URL}") private val resourceURL: String,
) {
    companion object {
        private const val BASE_DOMAIN = "https://www.communicatierijk.nl"
        const val BASE_RESOURCE_URL =
            "${BASE_DOMAIN}/documenten/2016/05/26/websiteregister"
    }

    private val logger = LoggerFactory.getLogger(ResourceHelperService::class.java)

    private lateinit var resourceURI: URI

    /**
     * Validates and stores the configured resource URI.
     */
    @Throws(UnableToDetermineDomainException::class)
    fun determineDomain() {
        try {
            val configuredURI = URI.create(resourceURL)
            require(configuredURI.scheme.equals("http", ignoreCase = true) ||
                configuredURI.scheme.equals("https", ignoreCase = true))
            require(!configuredURI.host.isNullOrBlank())
            resourceURI = configuredURI
        } catch (exception: Exception) {
            throw UnableToDetermineDomainException(
                "resourceurl must be an absolute HTTP(S) URL: '$resourceURL'",
                exception,
            )
        }
    }

    /**
     * Loads the [resourceURL] and searches for a tag containing '.ods' in the href attribute.
     * Relative, root-relative, protocol-relative and absolute links are resolved against [resourceURL].
     */
    fun determineDocumentURL(): String? {
        var linkToDocument: String? = null
        try {
            val doc = Jsoup.parse(remoteResourceClient.getText(resourceURL), resourceURL)
            linkToDocument =
                doc.select("a").firstOrNull { it.attributes()["href"].contains(".ods", true) }?.attributes()?.get("href")
        } catch (exception: Exception) {
            logger.error("Unable to connect to $resourceURL", exception)
        }
        if (linkToDocument == null) {
            logger.error("Could not determine link to the registerdocument")
            return null
        }
        return try {
            resourceURI.resolve(linkToDocument).toString()
        } catch (exception: Exception) {
            logger.error("Unable to resolve registerdocument link '$linkToDocument'", exception)
            null
        }
    }

}
