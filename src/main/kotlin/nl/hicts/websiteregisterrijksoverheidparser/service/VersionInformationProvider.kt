package nl.hicts.websiteregisterrijksoverheidparser.service

import nl.hicts.websiteregisterrijksoverheidparser.model.VersionInfo

fun interface VersionInformationProvider {
    fun getVersionInfo(): VersionInfo
}
