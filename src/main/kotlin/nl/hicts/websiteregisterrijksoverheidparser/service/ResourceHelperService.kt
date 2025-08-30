package nl.hicts.websiteregisterrijksoverheidparser.service

import nl.hicts.websiteregisterrijksoverheidparser.exception.UnableToDetermineDomainException
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class ResourceHelperService {
    companion object {
        private const val BASE_DOMAIN = "https://www.communicatierijk.nl"
        const val BASE_RESOURCE_URL =
            "${BASE_DOMAIN}/documenten/2016/05/26/websiteregister"
    }

    private val logger = LoggerFactory.getLogger(ResourceHelperService::class.java)

    @Value("\${resourceurl:$BASE_RESOURCE_URL}")
    private lateinit var resourceURL: String

    private lateinit var domain: String

    /**
     * Determines the base domain URL to use
     */
    @Throws(UnableToDetermineDomainException::class)
    fun determineDomain() {
        try {
            val url = URI.create(resourceURL).toURL()
            domain = if (url.port != -1) {
                url.protocol.plus("://").plus(url.host).plus(":").plus(url.port)
            } else {
                url.protocol.plus("://").plus(url.host)
            }
        } catch (_: Throwable) {
            throw UnableToDetermineDomainException("Unable to parse resourceURL '${resourceURL}', could not determine domain.")
        }
    }

    /**
     * Loads the [resourceURL] and searches for a tag containing '.ods' in the href attribute.
     * When found, it retrieves the given href thus resulting in a relative path to the register.
     * This path gets prefixed with the domain, resulting in an absolute path.
     */
    fun determineDocumentURL(): String? {
        var linkToDocument: String? = null
        try {
            val doc = Jsoup.connect(resourceURL).get()
            linkToDocument =
                doc.select("a").firstOrNull { it.attributes()["href"].contains(".ods", true) }?.attributes()?.get("href")
        } catch (t: Throwable) {
            logger.error("Unable to connect to $resourceURL", t)
        }
        if (linkToDocument == null) {
            logger.error("Could not determine link to the registerdocument")
            return null
        }
        if (linkToDocument.startsWith("http", true)) {
            return linkToDocument
        }
        return domain.plus(linkToDocument)
    }

}
