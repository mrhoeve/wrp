package nl.hicts.websiteregisterrijksoverheidparser.controller

import nl.hicts.websiteregisterrijksoverheidparser.model.VersionInfo
import nl.hicts.websiteregisterrijksoverheidparser.service.VersionInformationProvider
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VersionController(private val versionInformationProvider: VersionInformationProvider) {
    @GetMapping("/version", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVersion(): VersionInfo = versionInformationProvider.getVersionInfo()
}
