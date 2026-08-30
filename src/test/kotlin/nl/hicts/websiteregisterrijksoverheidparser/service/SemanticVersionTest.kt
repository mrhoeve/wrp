package nl.hicts.websiteregisterrijksoverheidparser.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SemanticVersionTest {
    @Test
    fun `parses release snapshot and prefixed versions`() {
        val expected = SemanticVersion(2, 10, 3)

        assertEquals(expected, SemanticVersion.parse("2.10.3"))
        assertEquals(expected, SemanticVersion.parse("2.10.3-SNAPSHOT"))
        assertEquals(expected, SemanticVersion.parse("v2.10.3"))
    }

    @Test
    fun `compares each semantic version component numerically`() {
        assertTrue(SemanticVersion.parse("2.0.0") > SemanticVersion.parse("1.99.99"))
        assertTrue(SemanticVersion.parse("2.10.0") > SemanticVersion.parse("2.9.99"))
        assertTrue(SemanticVersion.parse("2.10.4") > SemanticVersion.parse("2.10.3"))
    }

    @Test
    fun `rejects versions outside major minor patch format`() {
        assertThrows<IllegalArgumentException> { SemanticVersion.parse("1.8") }
        assertThrows<IllegalArgumentException> { SemanticVersion.parse("2.0.0-RC1") }
        assertThrows<IllegalArgumentException> { SemanticVersion.parse("invalid") }
    }

    @Test
    fun `compatible parser treats legacy release as patch zero`() {
        assertEquals(SemanticVersion(1, 8, 0), SemanticVersion.parseCompatible("1.8"))
        assertEquals(SemanticVersion(2, 0, 0), SemanticVersion.parseCompatible("2.0.0"))
        assertThrows<IllegalArgumentException> { SemanticVersion.parseCompatible("invalid") }
    }
}
