package nl.hicts.websiteregisterrijksoverheidparser.service

import nl.hicts.websiteregisterrijksoverheidparser.model.ParsedRegister
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

@Component
class OdsRegisterParser(
    @param:Value("\${odsmaxuncompressedsize:256MB}") private val maxUncompressedSize: DataSize = DEFAULT_MAX_UNCOMPRESSED_SIZE,
    @param:Value("\${odsmaxrows:100000}") private val maxRows: Int = DEFAULT_MAX_ROWS,
) {
    companion object {
        private const val CONTENT_XML = "content.xml"
        private const val TABLE_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        private const val TEXT_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        private const val OFFICE_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        private const val MAX_COLUMNS = 16_384
        private const val MAX_ODS_ROWS = 1_048_576
        private const val MAX_TEXT_SPACE_REPETITION = 1_000_000
        private const val DEFAULT_MAX_ROWS = 100_000
        private val DEFAULT_MAX_UNCOMPRESSED_SIZE = DataSize.ofMegabytes(256)

        private val GROUP_ALIASES = mapOf(
            "Websitetest Internet.nl" to "Websitetest",
            "E-mailtest Internet.nl" to "E-mailtest"
        )
    }

    init {
        require(maxUncompressedSize.toBytes() > 0) { "odsmaxuncompressedsize must be greater than zero" }
        require(maxRows in 1..MAX_ODS_ROWS) { "odsmaxrows must be between 1 and $MAX_ODS_ROWS" }
    }

    fun parse(file: File): ParsedRegister {
        ZipFile(file).use { archive ->
            val content = archive.getEntry(CONTENT_XML)
                ?: throw IllegalArgumentException("The ODS document does not contain $CONTENT_XML")
            require(content.size in 1..maxUncompressedSize.toBytes()) {
                "$CONTENT_XML exceeds the configured maximum of $maxUncompressedSize"
            }
            archive.getInputStream(content).use { input ->
                return parseContent(input)
            }
        }
    }

    private fun parseContent(input: InputStream): ParsedRegister {
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        val reader = factory.createXMLStreamReader(input)
        try {
            moveToFirstTable(reader)
            val rows = readRequiredRows(reader)
            require(rows.size >= 2) { "The ODS document does not contain group and header rows" }

            val numberOfRows = declaredRowCount(rows[0])
            require(rows.size == numberOfRows + 2) {
                "The ODS document contains ${rows.size - 2} register rows, expected $numberOfRows"
            }

            val rawHeaders = readRawHeaders(rows[1])
            val effectiveHeaders = createEffectiveHeaders(rows[0], rawHeaders)
            val records = rows.drop(2).map { row -> createRecord(effectiveHeaders, row) }
            return ParsedRegister(effectiveHeaders, records)
        } finally {
            reader.close()
        }
    }

    private fun moveToFirstTable(reader: XMLStreamReader) {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                reader.namespaceURI == TABLE_NAMESPACE && reader.localName == "table"
            ) {
                return
            }
        }
        throw IllegalArgumentException("The ODS document does not contain a spreadsheet table")
    }

    private fun readRequiredRows(reader: XMLStreamReader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var requiredRows: Int? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI == TABLE_NAMESPACE && reader.localName == "table-row") {
                        val repeated = repeatedCount(reader, "number-rows-repeated", maxRows + 2)
                        val row = readRow(reader)
                        repeat(repeated.coerceAtMost((requiredRows ?: 2) - rows.size).coerceAtLeast(0)) {
                            rows.add(row)
                            if (rows.size == 1) {
                                val count = declaredRowCount(row)
                                requiredRows = count + 2
                            }
                        }
                        if (requiredRows != null && rows.size >= checkNotNull(requiredRows)) return rows
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.namespaceURI == TABLE_NAMESPACE && reader.localName == "table") return rows
                }
            }
        }
        return rows
    }

    private fun readRow(reader: XMLStreamReader): List<String> {
        val values = mutableListOf<String>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI == TABLE_NAMESPACE &&
                        reader.localName in setOf("table-cell", "covered-table-cell")
                    ) {
                        val repeated = repeatedCount(reader, "number-columns-repeated", MAX_COLUMNS)
                        val value = readCell(reader)
                        repeat(repeated.coerceAtMost(MAX_COLUMNS - values.size).coerceAtLeast(0)) {
                            values.add(value)
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.namespaceURI == TABLE_NAMESPACE && reader.localName == "table-row") {
                        return values.dropLastWhile { it.isBlank() }
                    }
                }
            }
        }
        return values.dropLastWhile { it.isBlank() }
    }

    private fun readCell(reader: XMLStreamReader): String {
        val fallbackValue = listOf("string-value", "value", "boolean-value", "date-value", "time-value")
            .firstNotNullOfOrNull { name -> reader.getAttributeValue(OFFICE_NAMESPACE, name) }
            .orEmpty()
        val paragraphs = mutableListOf<String>()
        var paragraph: StringBuilder? = null
        var depth = 1

        while (reader.hasNext() && depth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    if (reader.namespaceURI == TEXT_NAMESPACE) {
                        when (reader.localName) {
                            "p" -> paragraph = StringBuilder()
                            "s" -> {
                                val count = repeatedCount(reader, "c", MAX_TEXT_SPACE_REPETITION, TEXT_NAMESPACE)
                                repeat(count) { paragraph?.append(' ') }
                            }
                            "tab" -> paragraph?.append('\t')
                            "line-break" -> paragraph?.append('\n')
                        }
                    }
                }

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> paragraph?.append(reader.text)

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.namespaceURI == TEXT_NAMESPACE && reader.localName == "p") {
                        paragraphs.add(paragraph?.toString().orEmpty())
                        paragraph = null
                    }
                    depth--
                }
            }
        }

        return if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n") else fallbackValue
    }

    private fun repeatedCount(
        reader: XMLStreamReader,
        attribute: String,
        maximum: Int,
        namespace: String = TABLE_NAMESPACE,
    ): Int {
        val configuredCount = reader.getAttributeValue(namespace, attribute) ?: return 1
        val count = configuredCount.toIntOrNull()
            ?: throw IllegalArgumentException("Attribute '$attribute' does not contain a valid repetition count")
        require(count in 1..maximum) { "Attribute '$attribute' exceeds the maximum repetition count of $maximum" }
        return count
    }

    private fun declaredRowCount(row: List<String>): Int {
        val count = row.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Cell A1 does not contain a valid number of register rows")
        require(count <= maxRows) { "The ODS document declares $count rows; configured maximum is $maxRows" }
        return count
    }

    private fun readRawHeaders(headerRow: List<String>): List<String> {
        val lastPopulatedColumn = headerRow.indexOfLast { it.isNotBlank() }
        require(lastPopulatedColumn >= 0) { "The ODS document does not contain column headers" }
        return headerRow.take(lastPopulatedColumn + 1).mapIndexed { index, header ->
            header.ifBlank { "Kolom ${index + 1}" }
        }
    }

    private fun createEffectiveHeaders(groupRow: List<String>, rawHeaders: List<String>): List<String> {
        val duplicateHeaders = rawHeaders.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val groups = readColumnGroups(groupRow, rawHeaders.size)
        val usedNames = mutableMapOf<String, Int>()

        return rawHeaders.mapIndexed { index, rawHeader ->
            val baseName = if (rawHeader in duplicateHeaders && groups[index].isNotBlank()) {
                "${groups[index]} $rawHeader"
            } else {
                rawHeader
            }
            makeUnique(baseName, usedNames)
        }
    }

    private fun readColumnGroups(groupRow: List<String>, columnCount: Int): List<String> {
        var currentGroup = ""
        return (0 until columnCount).map { column ->
            val value = groupRow.getOrElse(column) { "" }
            if (value.isNotBlank() && value.toLongOrNull() == null) {
                currentGroup = GROUP_ALIASES[value] ?: value
            }
            currentGroup
        }
    }

    private fun makeUnique(baseName: String, usedNames: MutableMap<String, Int>): String {
        val occurrence = (usedNames[baseName] ?: 0) + 1
        usedNames[baseName] = occurrence
        return if (occurrence == 1) baseName else "$baseName ($occurrence)"
    }

    private fun createRecord(headers: List<String>, row: List<String>): Map<String, String> {
        return buildMap {
            headers.forEachIndexed { index, header -> put(header, row.getOrElse(index) { "" }) }
        }
    }
}
