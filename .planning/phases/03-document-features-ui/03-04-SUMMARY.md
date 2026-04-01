---
phase: 03-document-features-ui
plan: 04
type: summary
wave: 4
---

<summary>
This plan successfully implemented the foundation for document downloading and platform-native file viewing, satisfying requirement DOC-UI-03.

**Data Layer:**
- Added `getRawBytes(url: String, cookie: String?): Result<ByteArray>` to `DualisApiClient` for downloading raw file data
- Added `downloadDocument(url: String): Result<ByteArray>` to `DualisDocumentService` with automatic session management and re-authentication support
- Download method properly handles session cookies and error cases

**Platform File Viewing:**
- Created `expect fun openFile(byteArray: ByteArray, fileName: String)` in commonMain as the cross-platform interface
- **Android implementation**: Uses `FileProvider` to create secure URIs for files cached in app directory, launches system viewer with `Intent.ACTION_VIEW`, retrieves Context from `NotificationDispatcher.getContext()`
- **iOS implementation**: Saves files to NSTemporaryDirectory and uses `UIDocumentInteractionController` for native preview
- **Desktop implementation**: Creates temp files and opens with `java.awt.Desktop` for system-default application handling
- All implementations include proper error handling and Napier logging

**Android Configuration:**
- Added `FileProvider` to `AndroidManifest.xml` with authority `de.fampopprol.dhbwhorb.fileprovider`
- Created `res/xml/file_paths.xml` with cache directory path configuration for secure file sharing
- Properly configured permissions and flags for external file viewing

The implementation is complete and verified. The app now has full capability to download documents from Dualis and open them using platform-native viewers on Android, iOS, and Desktop.

**Note:** FileViewer implementations were initially created with errors (missing ContextProvider, incorrect signatures) but have been corrected to use `NotificationDispatcher.getContext()` for Android and proper expect/actual function signatures across all platforms.
</summary>
