# URGENT: Session ID Still Empty - Build Not Updated

## Current Status

Looking at your latest logs from 18:18:53:

```
AuthenticationService: Session ID: null...
SessionManager: Storing auth data - SessionID: ..., AuthToken: 3406738961...
SessionManager: Returning cached auth data: AuthData(sessionId=, authToken=340673896152827, userFullName=null)
```

**The session ID is STILL empty!** This means one of two things:

1. **The app wasn't rebuilt** with my latest changes
2. **The cached data is being used** instead of the new login

---

## Critical Issue: Old Build Running

The log line `AuthenticationService: Session ID: null...` should NOT exist anymore in the latest code!

In the updated code, it should show:
```
✓ Using authToken as session ID: 3406738961...
Session ID length: 15 characters
Created AuthData - sessionId: 3406738961..., authToken: 3406738961...
```

**You're running an old build!**

---

## Action Required: Force Rebuild

### Option 1: Clean Build (RECOMMENDED)
```bash
# In terminal at project root:
cd /Users/johannes/StudioProjects/dhbw-next

# Clean everything
./gradlew clean

# Rebuild
./gradlew :composeApp:assembleDebug

# Install
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Option 2: Android Studio
1. **Build → Clean Project**
2. **Build → Rebuild Project**
3. **Run → Run 'app'** (not just the green play button - use the dropdown)

### Option 3: Nuclear Option
1. Uninstall app from device/emulator completely
2. **Build → Clean Project**
3. **File → Invalidate Caches / Restart**
4. Rebuild and run

---

## Why TimetablePage Shows No Logs

The TimetablePage has NO ViewModel connected because it requires database initialization which needs Android Context.

Current App.kt:
```kotlin
AppScreen.TIMETABLE -> {
    TimetablePage(
        viewModel = null,  // ← NO VIEWMODEL!
        ...
    )
}
```

Without a ViewModel:
- ❌ No data fetching happens
- ❌ No API calls are made  
- ❌ No lectures are loaded
- ❌ Empty state shown

---

## Quick Test: Verify Session ID Fix

After rebuilding, you should see these NEW logs:

```
AuthenticationService: No cookie session ID found (this is normal for Dualis)
AuthenticationService: AuthToken value: 3406738961...
AuthenticationService: ✓ Using authToken as session ID: 3406738961...
AuthenticationService: Session ID length: 15 characters
AuthenticationService: Created AuthData - sessionId: 3406738961..., authToken: 3406738961...
SessionManager: Storing auth data - SessionID: 3406738961..., AuthToken: 3406738961...
```

**Then when you check cached data:**
```
SessionManager: Returning cached auth data: AuthData(sessionId=340673896152827, authToken=340673896152827, userFullName=null)
```

Notice: **sessionId is NO LONGER empty!**

---

## Steps to See Lectures

### Step 1: Rebuild App
Clean build and reinstall as described above.

### Step 2: Login Again  
- Uninstall app or clear data
- Login with credentials
- **Verify in logs that sessionId is populated!**

### Step 3: Initialize Services Properly

You need to create the services with database in **MainActivity** or **Application** class:

```kotlin
// In MainActivity.kt or similar:
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var timetableViewModel: TimetableViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database with Android context
        database = createRoomDatabase(
            getDatabaseBuilder(applicationContext)
        )
        
        // Create shared HttpClient
        val sharedHttpClient = HttpClient {
            expectSuccess = false
            install(HttpCookies)
        }
        
        // Initialize services
        val sessionManager = SessionManager(...)
        val authService = AuthenticationService(sessionManager, sharedHttpClient)
        val apiClient = DualisApiClient(sharedHttpClient)
        val dualisLectureService = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            authenticationService = authService,
            timetableParser = TimetableParser(),
            htmlParser = HtmlParser(),
            lectureEventDao = database.lectureDao(),
            lecturerDao = database.lecturerDao()
        )
        
        val lectureService = LectureService(database, dualisLectureService)
        timetableViewModel = TimetableViewModel(lectureService)
        
        setContent {
            App(
                timetableViewModel = timetableViewModel,  // Pass it!
                ...
            )
        }
    }
}
```

Then update App.kt to accept and use it:
```kotlin
fun App(
    timetableViewModel: TimetableViewModel? = null,  // Add parameter
    ...
) {
    // ...existing code...
    
    AppScreen.TIMETABLE -> {
        TimetablePage(
            viewModel = timetableViewModel,  // Use it!
            ...
        )
    }
}
```

---

## Expected Behavior After Fixes

### 1. Login
```
✓ Using authToken as session ID: 3406738961...
✓ Created AuthData - sessionId: 3406738961...
```

### 2. Navigate to Timetable
```
TimetableViewModel: Loading lectures for week offset: 0
LectureService: Getting lectures for week 0
DualisLectureService: Fetching weekly lectures for date: 2025-11-06
DualisApiClient: Executing GET with sessionId: 3406738961...
```

### 3. See Lectures!
```
TimetableParser: Parsed 8 lectures from weekly view
TimetableViewModel: Successfully loaded 8 lectures
```

---

## Immediate Next Steps

1. **Clean and rebuild the app** - You're running old code!
2. **Check logs after login** - Session ID should be populated
3. **If session ID is still empty** - Share the FULL log output
4. **If session ID works** - Then we tackle the ViewModel initialization

---

## Summary

| Issue | Status | Fix |
|-------|--------|-----|
| Empty session ID | ❌ NOT FIXED YET | Need clean rebuild |
| Old code running | ❌ CONFIRMED | Force clean build |
| No ViewModel | ❌ NOT INITIALIZED | Need platform-specific init |
| No lectures shown | ❌ EXPECTED | ViewModel not connected |

**Next: Clean build, verify session ID, then initialize ViewModel!**

