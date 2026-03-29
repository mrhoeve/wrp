package nl.hicts.websiteregisterrijksoverheidparser.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

data class RegisterMetadata(
    val documentURL: String,
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @param:JsonProperty("discoveryDateTimeUTC")
    val discoveryDateTime: ZonedDateTime,
    val registersFound: Int,
    val columnHeaders: List<String>
)
