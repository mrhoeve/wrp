package nl.hicts.websiteregisterrijksoverheidparser.service

import com.fasterxml.jackson.annotation.JsonProperty
import nl.hicts.websiteregisterrijksoverheidparser.model.VersionInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
class VersionService(
    private val remoteResourceClient: RemoteResourceClient,
    private val objectMapper: ObjectMapper,
    buildProperties: BuildProperties,
    @param:Value("\${versioncheckenabled:true}") private val updateCheckEnabled: Boolean = true,
    @param:Value("\${versioncheckurl:$DEFAULT_RELEASE_URL}") private val updateCheckURL: String = DEFAULT_RELEASE_URL,
) : VersionInformationProvider {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val currentVersion = requireNotNull(buildProperties.version) {
        "Build version is missing from META-INF/build-info.properties"
    }
    private val versionInfo = AtomicReference(
        VersionInfo(
            version = currentVersion,
            latestVersion = null,
            updateAvailable = null,
            checkedAt = null,
        ),
    )

    override fun getVersionInfo(): VersionInfo = versionInfo.get()

    @Scheduled(
        initialDelayString = "\${versioncheckinitialdelay:PT30S}",
        fixedDelayString = "\${versioncheckinterval:PT6H}",
    )
    fun checkForUpdate() {
        if (!updateCheckEnabled) return

        try {
            val response = remoteResourceClient.getText(updateCheckURL, GITHUB_HEADERS)
            val release = objectMapper.readValue(response, GitHubRelease::class.java)
            val latestVersion = release.tagName.removePrefix("v")
            val updateAvailable =
                SemanticVersion.parseCompatible(latestVersion) > SemanticVersion.parseCompatible(currentVersion)
            versionInfo.set(
                VersionInfo(
                    version = currentVersion,
                    latestVersion = latestVersion,
                    updateAvailable = updateAvailable,
                    checkedAt = Instant.now().toString(),
                ),
            )
        } catch (exception: Exception) {
            logger.warn("Unable to check whether a newer WRP release is available", exception)
        }
    }

    private data class GitHubRelease(
        @param:JsonProperty("tag_name") val tagName: String,
    )

    companion object {
        private const val DEFAULT_RELEASE_URL = "https://api.github.com/repos/mrhoeve/wrp/releases/latest"
        private val GITHUB_HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2026-03-10",
            "User-Agent" to "mrhoeve/wrp",
        )
    }
}
