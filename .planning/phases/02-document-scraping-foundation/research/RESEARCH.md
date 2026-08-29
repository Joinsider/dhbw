# Research: Document Scraping Foundation

## HTML Structure Analysis

The documents are presented in a table with the class `tb`.

### Table Rows (`tr`)
Each row (excluding the header row) represents a document.

### Table Data (`td`)
- **Column 1 (`td.tbdata`)**: Document Name/Title (e.g., "Studienbescheinigung")
- **Column 2 (`td.tbdata`)**: Date (format: `DD.MM.YY`, e.g., "25.03.26")
- **Column 3 (`td.tbdata`)**: Time (format: `HH:MM`, e.g., "09:40")
- **Column 4 (`td.tbdata`)**: Status (often empty)
- **Column 5 (`td.tbdata`)**: Actions, contains an `<a>` tag with class `img download`.

### Download Link
The `href` attribute of the download link points to `/scripts/filetransfer.exe?...`.

## Data Model Proposal

```kotlin
data class DualisDocument(
    val title: String,
    val date: String, // Or parsed LocalDateTime
    val downloadUrl: String
)
```

## Implementation Strategy

1.  **Parser**: Use `Ksoup` (already used in the project for other parsers) to extract rows from the table.
2.  **Scraper**: Add a new method to the `DualisService` or equivalent to fetch the documents page.
3.  **URL**: The documents page is typically accessed via a specific `PRGNAME=CREATEDOCUMENT` and `menuid`. From the example: `?APPNAME=CampusNet&PRGNAME=CREATEDOCUMENT&ARGUMENTS=sessionno,menuid,mode,...` with `menuid=000339`.

## Verification Plan

- Create unit tests using the content of `.planning/example/documents.html`.
- Verify extraction of all 8 documents in the sample.
- Verify correct parsing of titles and download URLs.
