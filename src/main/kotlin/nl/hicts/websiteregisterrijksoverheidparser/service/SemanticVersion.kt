package nl.hicts.websiteregisterrijksoverheidparser.service

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        private val VERSION_PATTERN = Regex("^(?:v)?(\\d+)\\.(\\d+)\\.(\\d+)(?:-SNAPSHOT)?$")
        private val LEGACY_VERSION_PATTERN = Regex("^(?:v)?(\\d+)\\.(\\d+)$")

        fun parse(value: String): SemanticVersion {
            val match = requireNotNull(VERSION_PATTERN.matchEntire(value.trim())) {
                "Version '$value' does not use major.minor.patch format"
            }
            return fromMatch(match)
        }

        fun parseCompatible(value: String): SemanticVersion {
            val normalized = value.trim()
            VERSION_PATTERN.matchEntire(normalized)?.let { return fromMatch(it) }
            val legacyMatch = requireNotNull(LEGACY_VERSION_PATTERN.matchEntire(normalized)) {
                "Version '$value' does not use a supported version format"
            }
            return SemanticVersion(
                major = legacyMatch.groupValues[1].toInt(),
                minor = legacyMatch.groupValues[2].toInt(),
                patch = 0,
            )
        }

        private fun fromMatch(match: MatchResult) = SemanticVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
        )
    }
}
