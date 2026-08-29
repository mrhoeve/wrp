package nl.hicts.websiteregisterrijksoverheidparser.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OdsRegisterParserTest {
    private val parser = OdsRegisterParser()

    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `parses all columns present in the source`() {
        val file = createOds(
            groups = listOf("", "", ""),
            headers = listOf("URL", "Nieuwe kolom", "Nog een kolom"),
            rows = listOf(
                listOf("https://example.test", "waarde", "andere waarde"),
                listOf("https://missing.test", "alleen deze waarde")
            )
        )

        val result = parser.parse(file)

        assertEquals(listOf("URL", "Nieuwe kolom", "Nog een kolom"), result.columnHeaders)
        assertEquals("andere waarde", result.records[0]["Nog een kolom"])
        assertEquals("", result.records[1]["Nog een kolom"])
    }

    @Test
    fun `uses source sections to disambiguate duplicate headers`() {
        val file = createOds(
            groups = listOf("", "Websitetest Internet.nl", "", "E-mailtest Internet.nl", ""),
            headers = listOf("URL", "Totaal", "IPv6", "Totaal", "IPv6"),
            rows = listOf(listOf("https://example.test", "ja", "nee", "nee", "ja"))
        )

        val result = parser.parse(file)

        assertEquals(
            listOf("URL", "Websitetest Totaal", "Websitetest IPv6", "E-mailtest Totaal", "E-mailtest IPv6"),
            result.columnHeaders
        )
        assertEquals("ja", result.records.single()["Websitetest Totaal"])
        assertEquals("nee", result.records.single()["E-mailtest Totaal"])
    }

    @Test
    fun `keeps unknown sections and makes remaining collisions unique`() {
        val file = createOds(
            groups = listOf("", "Nieuwe sectie", "", "Andere sectie"),
            headers = listOf("URL", "Status", "Status", "Status"),
            rows = listOf(listOf("https://example.test", "een", "twee", "drie"))
        )

        val result = parser.parse(file)

        assertEquals(
            listOf("URL", "Nieuwe sectie Status", "Nieuwe sectie Status (2)", "Andere sectie Status"),
            result.columnHeaders
        )
        assertEquals(4, result.records.single().keys.size)
    }

    @Test
    fun `assigns a deterministic name to an empty header inside the used range`() {
        val file = createOds(
            groups = listOf("", "", ""),
            headers = listOf("URL", "", "Status"),
            rows = listOf(listOf("https://example.test", "naamloos", "ja"))
        )

        val result = parser.parse(file)

        assertEquals(listOf("URL", "Kolom 2", "Status"), result.columnHeaders)
        assertEquals("naamloos", result.records.single()["Kolom 2"])
    }

    @Test
    fun `parses the latest real register without losing columns`() {
        val resource = checkNotNull(javaClass.getResource("/__files/websiteregister-rijksoverheid-2026-08-28.ods"))

        val result = parser.parse(File(resource.toURI()))

        assertEquals(1659, result.records.size)
        assertEquals(28, result.columnHeaders.size)
        assertEquals(28, result.columnHeaders.toSet().size)
        assertTrue(result.columnHeaders.contains("Websitetest Testdatum"))
        assertTrue(result.columnHeaders.contains("E-mailtest Testdatum"))
        assertFalse(result.columnHeaders.contains("Testdatum"))
        assertTrue(result.records.all { it.keys.toList() == result.columnHeaders })
    }

    private fun createOds(
        groups: List<String>,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        require(groups.size == headers.size)
        val groupRow = groups.toMutableList().apply { this[0] = rows.size.toString() }
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append(
                """<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" """ +
                    """xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" """ +
                    """xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">"""
            )
            append("<office:body><office:spreadsheet><table:table table:name=\"Sheet1\">")
            appendOdsRow(groupRow)
            appendOdsRow(headers)
            rows.forEach { appendOdsRow(it) }
            append("</table:table></office:spreadsheet></office:body></office:document-content>")
        }

        val file = tempDirectory.resolve("register-${System.nanoTime()}.ods").toFile()
        ZipOutputStream(file.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("content.xml"))
            archive.write(xml.toByteArray(Charsets.UTF_8))
            archive.closeEntry()
        }
        return file
    }

    private fun StringBuilder.appendOdsRow(values: List<String>) {
        append("<table:table-row>")
        values.forEach { value ->
            append("<table:table-cell office:value-type=\"string\"><text:p>")
            append(escapeXml(value))
            append("</text:p></table:table-cell>")
        }
        append("</table:table-row>")
    }

    private fun escapeXml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
