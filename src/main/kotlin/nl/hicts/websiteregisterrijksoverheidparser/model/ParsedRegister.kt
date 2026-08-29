package nl.hicts.websiteregisterrijksoverheidparser.model

data class ParsedRegister(
    val columnHeaders: List<String>,
    val records: List<Map<String, String>>
)
