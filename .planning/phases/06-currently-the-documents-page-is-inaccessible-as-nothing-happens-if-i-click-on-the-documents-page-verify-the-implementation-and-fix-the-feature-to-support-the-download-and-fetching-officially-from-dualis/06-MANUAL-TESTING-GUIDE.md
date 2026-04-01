# Manual Testing Guide for Documents Feature

## Overview
This guide provides comprehensive testing procedures for verifying the Documents feature implementation with real Dualis access. It covers all platforms (Android, iOS, Desktop) and all major functionality areas.

## Prerequisites
- Real DHBW Dualis account credentials
- One or more of: Android emulator, iOS simulator, or Desktop build
- Access to documents in real Dualis account
- Napier debug logging enabled (for troubleshooting)

---

## Test Case 1: Navigation to Documents Page

**Objective:** Verify users can navigate to Documents page from other pages.

**Steps:**
1. Launch app and login with real Dualis credentials
2. From Timetable page, click DOCUMENTS in bottom navigation
3. Verify Documents page loads without crashing
4. Verify loading indicator appears briefly
5. Verify documents list is displayed

**Expected Results:**
- No crash or error messages
- Documents page displays with correct layout
- Loading state transitions smoothly to document list
- Bottom navigation shows DOCUMENTS as active item

**Platforms:** Android, iOS, Desktop

---

## Test Case 2: Document List Display

**Objective:** Verify documents are correctly fetched from Dualis and displayed.

**Steps:**
1. Navigate to Documents page (from Test Case 1)
2. Wait for documents to load completely
3. Verify document count matches Dualis portal (3+ documents expected)
4. For each document, verify:
   - Title is displayed and readable
   - Date is displayed in correct format (DD.MM.YY)
   - Time is displayed in correct format (HH:MM)
   - Document card is visually distinct and tappable
5. Check logs for any "DocumentParser" errors (use Napier debug logs)
6. Scroll through document list if more than 5 documents

**Expected Results:**
- 3+ documents displayed
- All fields populated correctly
- No parsing errors in logs
- Documents fully visible and properly formatted
- Scrolling works smoothly for large document lists

**Platforms:** Android, iOS, Desktop

---

## Test Case 3: Search Functionality

**Objective:** Verify document search filters documents by title.

**Steps:**
1. From Documents page, locate search field
2. Type search text: "Studienbescheinigung"
3. Verify document list filters to show only matching documents
4. Verify document count decreases appropriately
5. Clear search field
6. Verify all documents reappear in list
7. Repeat with different search terms (partial matches should work)

**Expected Results:**
- Search filters documents in real-time
- Only documents with matching title appear
- Clearing search restores full document list
- Partial search terms work (e.g., "Studie" matches "Studienbescheinigung")
- Search is case-insensitive

**Platforms:** Android, iOS, Desktop

---

## Test Case 4: Download Document

**Objective:** Verify users can download documents and choose between opening and saving.

**Steps:**
1. From Documents page, click three-dot menu button on any document
2. Verify dropdown menu appears with two options: "Open" and "Save to Files"
3. Choose "Open" option
4. Verify loading indicator appears briefly
5. Verify PDF opens in appropriate viewer
   - Android: PDF viewer or browser
   - iOS: Default PDF handler
   - Desktop: Default file handler
6. Repeat steps 1-4 with "Save to Files" option
7. Verify document is saved to appropriate location:
   - Android: Downloads folder or Files app
   - iOS: Files app
   - Desktop: Downloads folder

**Expected Results:**
- Dropdown menu appears with clear options
- Loading indicator shows during download
- PDF opens correctly in platform-specific viewer
- Document downloads successfully
- No HTTP errors (401, 403) in logs
- Both menu options work correctly

**Platforms:** Android, iOS, Desktop

**Known Behavior:**
- Download may take 2-10 seconds depending on file size and network
- First attempt may trigger session verification
- If session expired, automatic re-authentication occurs (transparent to user)

---

## Test Case 5: Session Expiration Handling

**Objective:** Verify app handles session expiration gracefully and re-authenticates automatically.

**Steps:**
1. Login and load documents successfully
2. Manually expire session by:
   - Option A: Logout and login again in browser
   - Option B: Clear cookies in app settings (if available)
   - Option C: Wait for session timeout (typically 1+ hour)
3. From Documents page, try to download a document
4. Verify app automatically re-authenticates (transparent process)
5. Verify download proceeds after re-authentication
6. Check logs for re-authentication messages

**Expected Results:**
- Re-authentication occurs automatically
- No manual login prompt required (unless credentials expired)
- Download succeeds after re-auth
- Logs show: "Re-authentication successful"
- User experience remains smooth throughout

**Platforms:** Android, iOS, Desktop

---

## Test Case 6: Error Handling

**Objective:** Verify appropriate error messages when operations fail.

**Subtests:**

**6a. Network Unavailable:**
1. On Documents page, disable network connection
2. Try to load documents (pull to refresh if available)
3. Verify error message appears: "Failed to load documents"
4. Re-enable network
5. Try to load again
6. Verify documents load successfully

**6b. Download Fails:**
1. Try to download document while network is disabled
2. Verify error message appears: "Failed to download document: [error details]"
3. Re-enable network and retry
4. Verify download succeeds

**6c. Invalid Session:**
1. If session becomes invalid during download
2. Verify automatic re-authentication attempt occurs
3. Verify error message only if re-auth fails
4. Expected message: "Failed to save document: [error details]"

**Expected Results:**
- All error messages are user-friendly
- Specific error details provided in logs
- Error messages don't crash the app
- User can retry operation after fixing issue
- Error state clears when retry succeeds

**Platforms:** Android, iOS, Desktop

---

## Test Case 7: Multi-Platform Verification

**Objective:** Verify feature works consistently across all supported platforms.

**Steps:**
1. Repeat Test Cases 1-6 on Android (emulator or physical device)
2. Repeat Test Cases 1-6 on iOS (simulator)
3. Repeat Test Cases 1-6 on Desktop (macOS/Linux/Windows)
4. Document any platform-specific differences
5. Verify UI layout adapts correctly to each platform
6. Check that navigation feels natural on each platform

**Expected Results:**
- Functionality identical across all platforms
- UI adapts appropriately (mobile vs desktop layout)
- No platform-specific crashes
- Performance acceptable on all platforms

**Known Platform Differences:**
- PDF opening behavior varies by platform:
  - Android: Intent to PDF viewer app
  - iOS: Default PDF handler in Files app or browser
  - Desktop: System default file handler
- File save locations vary by platform
- UI spacing may vary due to platform conventions

---

## Logging Reference

### How to Enable Debug Logging
- Most logging is automatic through Napier
- For detailed output, ensure Napier is configured at app startup
- On Android: Check Logcat with filter "DocumentParser" or "DualisDocumentService"
- On iOS: Check Xcode console
- On Desktop: Check console output

### Key Log Tags
```
"DocumentParser" - Document HTML parsing issues
"DualisDocumentService" - Document fetching and downloading
"DocumentsViewModel" - UI state and user interactions
"AuthenticationService" - Authentication and session issues
```

### Example Log Outputs
```
// Successful document fetch
D: Parsed 5 documents (DocumentParser)
D: Fetching documents with URL: https://dualis.dhbw.de/scripts/... (DualisDocumentService)

// Successful download
D: Downloading document from: https://dualis.dhbw.de/scripts/filetransfer.exe... (DualisDocumentService)
D: Downloaded document successfully: Studienbescheinigung, size: 245623 bytes (DocumentsViewModel)

// Session expiration and re-auth
W: Invalid document page received. Title: 'Login' (DualisDocumentService)
D: Attempting re-authentication (DualisDocumentService)
D: Re-authentication successful (DualisDocumentService)
```

---

## Known Limitations

### Demo Mode
- When not logged in or in demo mode, document downloads return error: "Document download not available in demo mode"
- Demo mode provides 3 hardcoded test documents for UI verification
- Real documents only available with valid Dualis credentials

### Session Management
- Session timeout typically 1+ hours (set by Dualis)
- Automatic re-authentication requires stored credentials
- If credentials are not stored, manual login required after session expires

### Platform-Specific Behavior
- Document opening is platform-specific and depends on installed apps
- PDF viewers vary by device
- Save locations vary by platform and device configuration

### HTML Parsing
- Parser is regex-based and depends on Dualis HTML structure
- If Dualis changes HTML structure significantly, parser may need updates
- Very large documents (>50MB) may cause memory issues on some devices

---

## Troubleshooting

### Issue: Documents page shows "Failed to load documents"

**Diagnosis:**
1. Check network connection - must be connected to internet
2. Check Napier logs for specific error message
3. Verify Dualis credentials are correct

**Solutions:**
- Ensure network is connected
- Logout and login again with correct credentials
- Check if Dualis service is available (may be down for maintenance)
- Try again after 5 minutes

### Issue: Download button doesn't respond

**Diagnosis:**
1. Check if document is still loading
2. Check logs for UI state issues

**Solutions:**
- Wait for document list to fully load
- Try a different document
- Logout and login again
- Restart the app

### Issue: Downloaded file won't open

**Diagnosis:**
1. Verify file downloaded (check Files app or Downloads folder)
2. Check if PDF viewer is installed
3. Verify file is not corrupted

**Solutions:**
- Install a PDF viewer app (if not present)
- Try opening with a different app
- Try downloading again
- Check that file size matches document size in Dualis

### Issue: Session expiration not handled automatically

**Diagnosis:**
1. Check if credentials are stored in app
2. Check logs for re-authentication messages

**Solutions:**
- Ensure "Remember credentials" is checked during login
- Manually logout and login again
- Clear app cache and login again

---

## Testing Checklist

Use this checklist to ensure comprehensive testing:

- [ ] Test Case 1: Navigation works from all pages
- [ ] Test Case 2: Documents display correctly with all fields
- [ ] Test Case 3: Search filters documents correctly
- [ ] Test Case 4a: "Open" option downloads and opens document
- [ ] Test Case 4b: "Save to Files" option downloads and saves document
- [ ] Test Case 5: Session expiration handled automatically
- [ ] Test Case 6a: Network error displays appropriate message
- [ ] Test Case 6b: Download error displays appropriate message
- [ ] Test Case 7: Tested on Android, iOS, and Desktop
- [ ] No crashes observed during testing
- [ ] No 401/403 errors in logs (except expected in demo mode)
- [ ] Performance acceptable (documents load within 5 seconds)
- [ ] UI layout correct on all screen sizes
- [ ] Accessibility verified (can navigate with accessibility features)

---

## Additional Notes

### When to Use This Guide
- Before releasing app to production
- After making changes to document fetching or parsing logic
- When testing with new Dualis server version
- When testing on new device types

### Reporting Issues
If issues are found during testing:
1. Document the exact steps to reproduce
2. Capture relevant log excerpts
3. Note which platform and device type
4. Check if issue is reproducible
5. Report with severity level and expected behavior

### Future Enhancements
Possible future improvements (not in scope for this phase):
- Caching downloaded documents for offline access
- Document categorization/filtering by type
- Document preview without full download
- Batch download capability
- Document synchronization with device storage
