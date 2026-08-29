package nl.hicts.websiteregisterrijksoverheidparser.service

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CallbackService(
    private val remoteResourceClient: RemoteResourceClient,
    @param:Value("\${callbackurl:}") private val callbackURL: String,
    @param:Value("\${callbackparameter:}") private val callbackParameter: String,
) {
    private val logger = LoggerFactory.getLogger(CallbackService::class.java)

    /**
     * Performs the callback if one is specified
     */
    fun performCallback() {
        if (callbackURL.isNotBlank()) {
            try {
                val completeCallbackURL = if (callbackParameter.isNotBlank()) {
                    "$callbackURL?$callbackParameter"
                } else {
                    callbackURL
                }
                val callbackResponse = Jsoup.parse(remoteResourceClient.getText(completeCallbackURL))
                logger.info("Callback to $callbackURL executed, response document:\n$callbackResponse")
            } catch (t: Throwable) {
                logger.error("Unable to perform callback to $callbackURL", t)
            }
        }
    }

}
