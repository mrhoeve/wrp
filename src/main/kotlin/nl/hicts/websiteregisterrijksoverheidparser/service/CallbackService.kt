package nl.hicts.websiteregisterrijksoverheidparser.service

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CallbackService {
    private val logger = LoggerFactory.getLogger(WebsiteregisterRijksoverheidService::class.java)

    @Value("\${callbackurl:}")
    private var callbackURL: String? = null
    @Value("\${callbackparameter:}")
    private var callbackparameter: String? = null

    /**
     * Performs the callback if one is specified
     */
    fun performCallback() {
        if(!callbackURL.isNullOrBlank()) {
            try {
                val completeCallbackURL = if(!callbackparameter.isNullOrBlank()) {
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

}
