# Authentication & Timetable Integration Fix

## Problem Identified

Your app showed "no lectures this week" even though Dualis had lectures. The root cause was **cookies not being shared** between the AuthenticationService and DualisApiClient.

### Symptoms
```
SessionID: ..., AuthToken: 7561460270..., FullName: null
Created and cached AuthData: AuthData(sessionId=, authToken=756146027069778, userFullName=null)
```

The session ID was **empty** because each service was creating its own `HttpClient` instance with separate cookie storage.

---

## Issues Fixed

### 1. ✅ Cookie Sharing Between Services
**Problem:** AuthenticationService and DualisApiClient used separate HttpClient instances
**Solution:** Modified both services to accept an HttpClient parameter and share one instance

**Before:**
```kotlin
class DualisApiClient(
    private val client: HttpClient = HttpClient {
        expectSuccess = false
        install(HttpCookies)
    }
)

class AuthenticationService(
    val sessionManager: SessionManager,
    private val client: HttpClient = HttpClient {
        expectSuccess = false
        install(HttpCookies)
    }
)
```

**After:**
```kotlin
class DualisApiClient(
    private val client: HttpClient  // ← Required parameter
)

class AuthenticationService(
    val sessionManager: SessionManager,
    private val client: HttpClient  // ← Required parameter
)
```

### 2. ✅ Date Formatting Issue
**Problem:** Used `date.month.toString()` which returns "NOVEMBER" instead of "11"
**Solution:** Changed to `date.monthNumber` for numeric month

**Before:**
```kotlin
val dateString = "${date.day.toString().padStart(2, '0')}.${date.month.toString().padStart(2, '0')}.${date.year}"
// Would produce: "06.NOVEMBER.2025" ❌
```

**After:**
```kotlin
val dateString = "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
// Produces: "06.11.2025" ✅
```

### 3. ✅ Proper Service Initialization
**Problem:** Services weren't properly initialized with dependencies
**Solution:** Updated App.kt to properly create and inject all services

**Changes in App.kt:**
```kotlin
// Create shared HttpClient for all Dualis services
val sharedHttpClient = remember {
    HttpClient {
        expectSuccess = false
        install(HttpCookies)
    }
}

// Initialize services with shared HttpClient
val authenticationService = AuthenticationService(
    sessionManager = sessionManager,
    client = sharedHttpClient  // ← Shared!
)

val dualisApiClient = DualisApiClient(
    client = sharedHttpClient  // ← Same instance!
)

val dualisLectureService = DualisLectureService(
    apiClient = dualisApiClient,
    sessionManager = sessionManager,
    authenticationService = authenticationService,
    timetableParser = timetableParser,
    htmlParser = htmlParser,
    lectureEventDao = database.lectureDao(),
    lecturerDao = database.lecturerDao()
)

val lectureService = LectureService(
    database = database,
    dualisLectureService = dualisLectureService
)

val timetableViewModel = TimetableViewModel(
    lectureService = lectureService
)
```

---

## Why This Matters

### Cookie-based Authentication Flow:
1. User logs in → AuthenticationService receives session cookie
2. Cookie is stored in HttpClient's cookie jar
3. DualisApiClient makes requests → **needs same cookie jar**
4. If using different HttpClient → **no cookies** → authentication fails

### The Fix:
- Both services now share the **same HttpClient instance**
- Cookies from login are **automatically sent** with lecture requests
- Session is maintained across all API calls

---

## Files Modified

### 1. `/composeApp/src/commonMain/kotlin/de/joinside/dhbw/data/dualis/remote/DualisApiClient.kt`
- Changed constructor to require HttpClient parameter
- Added factory method `createDefault()` for backward compatibility
- Added documentation about cookie sharing

### 2. `/composeApp/src/commonMain/kotlin/de/joinside/dhbw/data/dualis/remote/services/AuthenticationService.kt`
- Changed constructor parameter order (HttpClient now required)
- Added factory method `createSharedHttpClient()`
- Added documentation about shared client

### 3. `/composeApp/src/commonMain/kotlin/de/joinside/dhbw/data/dualis/remote/services/DualisLectureService.kt`
- Fixed date formatting: `date.month.toString()` → `date.monthNumber`
- Now properly uses dayOfMonth and monthNumber

### 4. `/composeApp/src/commonMain/kotlin/de/joinside/dhbw/App.kt`
- Created shared HttpClient instance
- Initialized all services with proper dependency injection
- Connected TimetableViewModel to TimetablePage

---

## Testing Checklist

### ✅ Authentication
- [ ] Login with real credentials
- [ ] Check session ID is not empty in logs
- [ ] Verify cookies are present

### ✅ Lecture Fetching
- [ ] Navigate to Timetable page
- [ ] Check for "Loading lectures..." indicator
- [ ] Verify lectures appear (if available in Dualis)
- [ ] Check logs for successful API calls

### ✅ Week Navigation
- [ ] Click "Previous Week" button
- [ ] Click "Next Week" button
- [ ] Verify different lectures load

### ✅ Error Handling
- [ ] Try with invalid credentials (should show error)
- [ ] Try when Dualis is down (should show error message)
- [ ] Try with no network (should show error)

---

## Expected Log Output (Success)

```
AuthenticationService: Login completed successfully
SessionManager: Storing auth data - SessionID: XYZ123..., AuthToken: ABC456..., FullName: John Doe
SessionManager: Created and cached AuthData: AuthData(sessionId=XYZ123..., authToken=ABC456..., userFullName=John Doe)

TimetableViewModel: Loading lectures for week offset: 0
LectureService: Getting lectures for week 0
DualisLectureService: Fetching weekly lectures for date: 2025-11-06
DualisApiClient: Executing GET request to: https://dualis.dhbw.de/scripts/mgrqispi.dll
DualisApiClient: Request successful, response length: 15234 characters
TimetableParser: Parsing lectures from weekly view HTML
TimetableParser: Parsed 8 lectures from weekly view

TimetableViewModel: Successfully loaded 8 lectures for week 0
```

---

## Common Issues & Solutions

### Issue: Still showing empty session ID
**Solution:** 
1. Logout completely: `authenticationService.logout()`
2. Clear app data
3. Login again
4. Check logs for "Storing auth data"

### Issue: "No lectures this week" but Dualis has lectures
**Possible causes:**
1. Date formatting wrong (fixed in this update)
2. Wrong week being requested
3. HTML parser not recognizing lecture format
4. Check logs for parsing errors

### Issue: "Access denied" errors
**Possible causes:**
1. Session expired (should auto re-authenticate)
2. Wrong credentials stored
3. Dualis server issues

---

## Architecture Diagram

```
┌─────────────────┐
│   App.kt        │ Creates shared HttpClient
└────────┬────────┘
         │
         ├─────────────────────────────────┐
         │                                 │
         ▼                                 ▼
┌─────────────────────┐         ┌──────────────────────┐
│ Authentication      │         │  DualisApiClient     │
│ Service             │◄────────┤                      │
│ (has HttpClient)    │         │  (has same           │
└──────────┬──────────┘         │   HttpClient)        │
           │                    └──────────┬───────────┘
           │ Cookies stored                │
           │ in shared jar                 │ Cookies sent
           │                               │ automatically
           ▼                               ▼
    ┌──────────────┐              ┌─────────────────┐
    │ Session ID   │              │ API Requests    │
    │ + Cookies    │◄─────────────┤ with Session    │
    └──────────────┘              └─────────────────┘
```

---

## Next Steps

1. **Test the Fix:**
   - Build and run the app
   - Login with real Dualis credentials
   - Navigate to Timetable page
   - Verify lectures appear

2. **Monitor Logs:**
   - Watch for "SessionID: ..." in logs
   - Should NOT be empty
   - Should see lecture parsing logs

3. **Report Results:**
   - If still showing empty: Check network inspector for cookies
   - If lectures don't appear: Check HTML parsing logs
   - If other issues: Share full log output

---

## Summary

The core issue was **architectural**: services weren't sharing their HTTP client, so cookies from authentication were lost when making API requests. This is now fixed by:

1. ✅ Sharing one HttpClient across all services
2. ✅ Properly injecting dependencies
3. ✅ Fixing date formatting
4. ✅ Connecting all the pieces in App.kt

The app should now:
- ✅ Maintain session after login
- ✅ Fetch lectures from Dualis
- ✅ Display them in the timetable
- ✅ Handle week navigation
- ✅ Show proper loading/error states

**The fix is complete and ready for testing!** 🎉

