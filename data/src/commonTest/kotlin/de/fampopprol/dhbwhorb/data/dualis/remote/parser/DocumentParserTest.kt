package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentParserTest {

    private val sampleHtml = """
        <table class="tb">
            <tr>
                <td class="tbhead">Name</td>
                <td class="tbhead">Datum</td>
                <td class="tbhead">Zeit</td>
                <td class="tbhead">Status</td>
                <td class="tbhead">&nbsp;</td>
            </tr>

            <tr>
                <td class="tbdata">Studienbescheinigung</td>
                <td class="tbdata">25.03.26</td>
                <td class="tbdata">09:40</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?W1OHJ6JL4uCZ-CnByf22mCI9PBVeuHETcOH38j0Ge~aryj87oDaFczyKMa~XZQqaY3u~Wi6ilST847Cmhwz~9l0M7OfeiRnCtiZqio-MeRWpp-l8J96dtDDuhmTv34KV-qYMOIbAFqlJqIy0FThKHv50pJE~5~-1pVbyG7yQOXV79Vlte6JvZ7HjwUlHnCz9XRuojUWmvBcK5aGsIdpODTPan-j2W0RM2YUA61jrSwb2D~dT8unIO-cl5o1Czpr9N~dGQA__">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Zahlungsinformation Semesterbeiträge</td>
                <td class="tbdata">19.02.26</td>
                <td class="tbdata">14:47</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?LGdWK8A1ycO2ciGdULL2DaU9AGjiST4e4XGtPsVnVNE8faAX~-K5T8w-IQaupeCJSDVDbaVSU9mwvkAIrwCq5z5OaQ8pXqVHVKlC5TIonRI~RsGKPuSnVG-YPx6PaJDiCETKCygBNRyf4WMJNWzaoDkqaWIT02UiXJDQ0nHOTddRspKCeCm9g50HVvK1yF2zUXWNYuf6HN~QLUhdqF6Mygr92lws2gQHCd6ep4vTzpJOCksfARmLIyOqsWJz5Wm2WhYNY~zKFXZ9BkMS0NbDEMIQ~cGdNYVaJp85TF7BTCTUbohn">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Semesternotenbescheid - Download</td>
                <td class="tbdata">11.02.26</td>
                <td class="tbdata">15:52</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?tnzi3pqPyAd5k2D8rgf1C6qab7b758X1wWhZNAyJ3~StKRXgLyvjuz5nbYrXr5u~xs8XqZEQ0iguNrTl0rV06IdPrkUtg0oZGRcBjGbzRqgYgzaOlwsHPVFgUycF~v51cr8JIyy3OQ3TsDWGODW2IXc~~YOdSdQp6vaA6SabbXatahA~woySSXpGNbvEq~4zo~~y9VCTmuJn-cBIbXIPNodfe3kn6SahXTsnIxUij81XLvLxmBl7gm53kw__">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Studienbescheinigung</td>
                <td class="tbdata">07.11.25</td>
                <td class="tbdata">13:26</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?lGpCJucryF4v2d8QRMp6OGuxNwRsbamI5t9vkUZvhARd1fljCnmGCXKYpp43pXN5cK7gaHXcmBKterdoH3r5DuovF44vt8-zVH2rpYPplv70mQfQz0NS2B9kbAgaCWUIcZUuk-MzpsE9n-lEc6mDgcp4qw6TGBnW1kG6hUsVkK6vVRR4vJMKu~ttdsvVmRieNlMu6~4xphtHomqV4SVYW77d9ebPLxdtTsCvbosjge7Sky2~5aO5oSDIN3akQBOM0x5czg__">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Zahlungsinformation Semesterbeiträge</td>
                <td class="tbdata">20.08.25</td>
                <td class="tbdata">15:40</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?FAKKrigdNVlpeOL0mYqg8znp7qL0YPSW6CM-ezK5EvyKnxSwafguOkNHI4v2qNEdy88rzpMLN1MgRxFO-XY0LYNz9HSWtky9GCAc1fmRhLdtjQR~O7eGQuSYC3rPgjGURdptRdyXX3gdD7xISVS9ZzIyQFnmE26yHyu~8aIoZssuhHt6YnKQAFWeJpbBLBZPTvMgOZUKteRB5Xiz6BO7SPtI9I1fS29iYFDgvGkf9VbipxC9yj7jLREU5VBcpW257jcN4O4eo~yctqNvTecaQQ1nGqwYptH6hhXYsyPsO2cbNn5i">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Semesternotenbescheid - Download</td>
                <td class="tbdata">29.07.25</td>
                <td class="tbdata">13:18</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?8-1S3MsLljQcPJwCjkDYNHpK8fs2690zaNx8aoHSmGrazPPV6TShHpPLm~j-Q30uzSps~hNNSiKMZTQ3kxelqRnRFVUSrgVDJ7EBR2ZwOeQAkOBG1zcA3jfHFLTvBYpNsAMslrJOD~w9xpomzDK9xUKmLILrp2DtypUFytXTlf4burTwRudRsVj~ZrgepxFsRUwJ4Qw9X3DnvAUTfdMOo1HvmrVxG1MEgttGAm9aeM66cuhAFiZv9RMUDQ__">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Studienbescheinigung</td>
                <td class="tbdata">04.04.25</td>
                <td class="tbdata">05:37</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?vop9BWaBBWc1xyuawLXwxJQppH13GpAZwnWQDzPU7q3pgW7vpInQBgHHHJhzmeDX~1yGq3eSlW5FP5AFy75f4l5fOFF1CP59OeFoS29n35ky8C3MMfYNk~PRLhHGAHZ8GrZwDXd08gCZ07w6vQy-U1Uads659TkDbK24b1ADTsJgp13iJv5botLBRP8vsTLzkeGGeDO9rwy~IHyIFoF5Q2ffpp8uLE4pKNRpJ91xji9~3ye~8gFY56Y2S4RbRhT~zZJ6Qw__">Download</a>
                </td>
            </tr>
            <tr>
                <td class="tbdata">Zahlungsinformation Semesterbeiträge</td>
                <td class="tbdata">02.09.24</td>
                <td class="tbdata">11:18</td>
                <td class="tbdata"></td>
                <td class="tbdata">
                    <a class="img download" href="/scripts/filetransfer.exe?oZ8YyOFKz2~Za-GKFdqUha5KWAi4JeIlUqPKk7-h2qmXv1ZBy27Xp~wJ5uCR-pe2KhUCOlER8ug9tM-VeWfBXLITRV02sfe5ftD8dmd-M3azBuBOEhS8f~VqZgOB5Og~KibUEANOVolFxuakxi1L~birQLy8CgPJvQ~SX4BJO6UaHmpMZEiwG9fwVTTcZud6FN0zLpAAJyMqgjlgXju9aRZExok5Mft79mVxuz4YXskuXH2nAdxXkj-nAtu2EH1iRJTNdTB9wWKg8jEjt7TTW~U1MRbHdyhBI4pLI12stYsJ8xKA">Download</a>
                </td>
            </tr>
        </table>
    """.trimIndent()

    @Test
    fun testParseDocuments() {
        val parser = DocumentParser()
        val documents = parser.parseDocuments(sampleHtml)

        assertEquals(8, documents.size, "Should parse 8 documents")

        val firstDoc = documents[0]
        assertEquals("Studienbescheinigung", firstDoc.title)
        assertEquals("25.03.26", firstDoc.date)
        assertEquals("09:40", firstDoc.time)
        assertTrue(firstDoc.downloadUrl.contains("/scripts/filetransfer.exe?W1OHJ6JL4uCZ-CnByf22mCI9PBVeuHETcOH38j0Ge"), "Download URL should be correct")

        val lastDoc = documents[7]
        assertEquals("Zahlungsinformation Semesterbeiträge", lastDoc.title)
        assertEquals("02.09.24", lastDoc.date)
        assertEquals("11:18", lastDoc.time)
    }

    @Test
    fun aRowUsingRealThTagsIsAlsoSkippedAsAHeader() {
        val html = """
            <table>
                <tr><th>Name</th><th>Datum</th><th>Zeit</th><th>Status</th><th></th></tr>
                <tr>
                    <td>Studienbescheinigung</td>
                    <td>25.03.26</td>
                    <td>09:40</td>
                    <td></td>
                    <td><a href="/download/1">Download</a></td>
                </tr>
            </table>
        """.trimIndent()

        val documents = DocumentParser().parseDocuments(html)

        assertEquals(1, documents.size, "The <th> header row must not be read as a document")
    }

    @Test
    fun aRowWithFewerThanFiveCellsIsSkipped() {
        val html = """
            <table>
                <tr><td>Only</td><td>Two cells</td></tr>
            </table>
        """.trimIndent()

        assertTrue(DocumentParser().parseDocuments(html).isEmpty())
    }

    @Test
    fun aRowWithNoDownloadLinkIsSkipped() {
        val html = """
            <table>
                <tr>
                    <td>Studienbescheinigung</td>
                    <td>25.03.26</td>
                    <td>09:40</td>
                    <td></td>
                    <td>no link here</td>
                </tr>
            </table>
        """.trimIndent()

        assertTrue(DocumentParser().parseDocuments(html).isEmpty(), "A row without a download href is not a document")
    }

    @Test
    fun duplicateRowsAreCollapsedToOne() {
        val row = """
            <tr>
                <td>Studienbescheinigung</td>
                <td>25.03.26</td>
                <td>09:40</td>
                <td></td>
                <td><a href="/download/1">Download</a></td>
            </tr>
        """.trimIndent()
        val html = "<table>$row$row</table>"

        val documents = DocumentParser().parseDocuments(html)

        assertEquals(1, documents.size, "The same row twice must not become two documents")
    }
}
