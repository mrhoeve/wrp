package nl.hicts.websiteregisterrijksoverheidparser.model

data class VersionInfo(
    val version: String,
    val latestVersion: String?,
    val updateAvailable: Boolean?,
    val checkedAt: String?,
)
