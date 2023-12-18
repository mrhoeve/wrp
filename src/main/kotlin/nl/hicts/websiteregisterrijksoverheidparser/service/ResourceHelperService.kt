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
        private const val baseDomain = "https://www.communicatierijk.nl"
        const val baseResourceURL =
            "${baseDomain}/vakkennis/rijkswebsites/verplichte-richtlijnen/websiteregister-rijksoverheid"
    }

    private val logger = LoggerFactory.getLogger(ResourceHelperService::class.java)

    @Value("\${resourceurl:$baseResourceURL}")
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
        } catch (t: Throwable) {
            throw UnableToDetermineDomainException("Unable to parse resourceURL '${resourceURL}', could not determine domain.")
        }
    }

    /**
     * Loads the [resourceURL] and searches for a tag containing the text '(ods,'.
     * When found, it retrieves the given href thus resulting in a relative path to the register.
     * This path gets prefixed with the domain, resulting in an absolute path
     */
    fun determineDocumentURL(): String? {
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

}
