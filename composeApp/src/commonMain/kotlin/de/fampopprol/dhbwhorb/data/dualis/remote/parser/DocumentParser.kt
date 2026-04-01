package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import io.github.aakira.napier.Napier

class DocumentParser {
    companion object {
        private const val TAG = "DocumentParser"
    }

    private val rowPattern = """<tr\b[^>]*>([\s\S]*?)</tr>""".toRegex(RegexOption.IGNORE_CASE)
    private val tdPattern = """<td\b[^>]*>([\s\S]*?)</td>""".toRegex(RegexOption.IGNORE_CASE)
    private val scriptPattern = """<script[\s\S]*?</script>""".toRegex(RegexOption.IGNORE_CASE)
    private val htmlTagPattern = """<[^>]+>""".toRegex()
    private val hrefPattern = """href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Parses documents from the HTML table.
     * @param htmlContent The HTML content
     * @return List of parsed DualisDocument objects
     */
    fun parseDocuments(htmlContent: String): List<DualisDocument> {
        val documents = mutableListOf<DualisDocument>()
        try {
            for (rowMatch in rowPattern.findAll(htmlContent)) {
                val rowHtml = rowMatch.groupValues[1]

                // Skip header rows
                if (rowHtml.contains("<th", ignoreCase = true) || rowHtml.contains("class=\"tbhead\"", ignoreCase = true)) {
                    continue
                }

                val cells = tdPattern.findAll(rowHtml).toList()

                if (cells.size < 5) {
                    continue
                }

                val title = normalizeCell(cells[0].groupValues[1])
                val date = normalizeCell(cells[1].groupValues[1])
                val time = normalizeCell(cells[2].groupValues[1])

                val downloadCellHtml = cells[4].groupValues[1]
                val downloadUrlMatch = hrefPattern.find(downloadCellHtml)
                val downloadUrl = downloadUrlMatch?.groupValues[1] ?: ""

                if (title.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    documents.add(
                        DualisDocument(
                            title = title,
                            date = date,
                            time = time,
                            downloadUrl = downloadUrl
                        )
                    )
                }
            }

            // Deduplicate by (title, date, time) combination to remove duplicates from page parsing
            val uniqueDocuments = documents.distinctBy { "${it.title}|${it.date}|${it.time}" }
            if (documents.size != uniqueDocuments.size) {
                Napier.d("Parsed ${documents.size} documents (${uniqueDocuments.size} unique after deduplication)", tag = TAG)
            } else {
                Napier.d("Parsed ${documents.size} documents", tag = TAG)
            }
        } catch (e: Exception) {
            Napier.e("Error parsing documents: ${e.message}", e, tag = TAG)
        }
        return documents.distinctBy { "${it.title}|${it.date}|${it.time}" }
    }

    private fun normalizeCell(text: String): String {
        return scriptPattern
            .replace(text, "")
            .replace(htmlTagPattern, "")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
