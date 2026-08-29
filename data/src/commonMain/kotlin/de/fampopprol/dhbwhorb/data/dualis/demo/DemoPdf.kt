/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.demo

/**
 * A one-page PDF, written by hand.
 *
 * The demo account has to hand the viewer real bytes: a document that is listed but answers a tap
 * with "not available" is a dead end, and it leaves the open-and-save path — the part with the
 * platform file dialogs in it — untried until somebody logs into the real Dualis.
 *
 * By hand because the alternative is a PDF library on four platforms for one page of text. The
 * format allows it: a PDF is a handful of numbered objects, a cross-reference table of their byte
 * offsets, and a trailer pointing at the catalogue. Only the offsets need care, and they are
 * counted as the objects are written rather than guessed afterwards.
 *
 * Helvetica, because it is one of the fourteen faces every reader has to provide itself — nothing
 * is embedded, and the file stays under two kilobytes.
 */
internal object DemoPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 64
    private const val TITLE_SIZE = 18
    private const val BODY_SIZE = 11
    private const val LINE_HEIGHT = 18

    /**
     * @param title the heading, also the first line of the page
     * @param lines the body, one entry per line; an empty entry is a blank line
     * @param monospacedBody sets the body in Courier, for a page whose lines are columns — a
     *   proportional font turns a padded table into a ragged one
     */
    fun render(title: String, lines: List<String>, monospacedBody: Boolean = false): ByteArray {
        val content = contentStream(title, lines, monospacedBody)

        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>",
            "<< /Length ${content.length} >>\nstream\n$content\nendstream",
        )

        val out = PdfWriter()
        out.append("%PDF-1.4\n")
        // A comment of high bytes, which is how a file says "treat me as binary" to anything that
        // might otherwise helpfully convert line endings.
        out.appendBytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        val offsets = objects.mapIndexed { index, body ->
            val offset = out.size
            out.append("${index + 1} 0 obj\n$body\nendobj\n")
            offset
        }

        val xrefOffset = out.size
        out.append("xref\n0 ${objects.size + 1}\n")
        // Object 0 is always the head of the free list, and every entry is exactly 20 bytes.
        out.append("0000000000 65535 f \n")
        offsets.forEach { out.append("${it.toString().padStart(10, '0')} 00000 n \n") }

        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun contentStream(title: String, lines: List<String>, monospacedBody: Boolean): String {
        val top = PAGE_HEIGHT - MARGIN
        val bodyFont = if (monospacedBody) "F2" else "F1"
        return buildString {
            append("BT\n/F1 $TITLE_SIZE Tf\n$MARGIN $top Td\n(${escape(title)}) Tj\nET\n")
            append("BT\n/$bodyFont $BODY_SIZE Tf\n$MARGIN ${top - 2 * LINE_HEIGHT} Td\n$LINE_HEIGHT TL\n")
            lines.forEach { append("(${escape(it)}) Tj T*\n") }
            append("ET\n")
        }
    }

    /** `(`, `)` and `\` end or escape a string literal, so they have to be escaped themselves. */
    private fun escape(text: String): String = buildString {
        text.forEach { char ->
            when (char) {
                '(', ')', '\\' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }
}

/**
 * A growing byte buffer that knows how many bytes it holds — which is the whole reason it exists,
 * because the cross-reference table is a list of byte offsets into the file.
 *
 * Text goes in as WinAnsi, the encoding the font declares: for German that is Latin-1, one byte
 * per character. `encodeToByteArray` would write UTF-8, and "Studienbescheinigung für" would
 * arrive in the viewer as "fÃ¼r".
 */
private class PdfWriter {

    private val bytes = mutableListOf<Byte>()

    val size: Int get() = bytes.size

    fun append(text: String) {
        text.forEach { char ->
            val code = char.code
            // Anything outside Latin-1 has no WinAnsi byte; a question mark is a visible loss
            // rather than a corrupt file.
            bytes += if (code <= 0xFF) code.toByte() else '?'.code.toByte()
        }
    }

    fun appendBytes(raw: ByteArray) {
        raw.forEach { bytes += it }
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}
