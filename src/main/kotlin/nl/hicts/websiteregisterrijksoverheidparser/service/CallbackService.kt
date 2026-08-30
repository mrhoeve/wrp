package nl.hicts.websiteregisterrijksoverheidparser.service

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
                remoteResourceClient.getText(createCallbackURL())
                logger.info("Callback executed")
            } catch (exception: Exception) {
                logger.error("Unable to perform callback", exception)
            }
        }
    }

    private fun createCallbackURL(): String {
        if (callbackParameter.isBlank()) return callbackURL

        val fragmentIndex = callbackURL.indexOf('#')
        val baseURL = if (fragmentIndex >= 0) callbackURL.substring(0, fragmentIndex) else callbackURL
        val fragment = if (fragmentIndex >= 0) callbackURL.substring(fragmentIndex) else ""
        val separator = when {
            baseURL.endsWith('?') || baseURL.endsWith('&') -> ""
            baseURL.contains('?') -> "&"
            else -> "?"
        }
        return "$baseURL$separator$callbackParameter$fragment"
    }
}
