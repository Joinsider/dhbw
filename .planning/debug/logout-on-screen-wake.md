---
status: verifying
trigger: "User is still being logged out when turning the screen on (Pixel 9 Pro Android 16, and also tablets). The recent fix (removing repeatOnLifecycle) didn't resolve it."
created: 2026-04-10T21:30:00Z
updated: 2026-04-10T21:36:00Z
symptoms_prefilled: true
---

## Current Focus
hypothesis: CONFIRMED - onDestroy() unconditionally calls logout() which clears session on ANY activity recreation (not just final destroy)
test: Verified MainActivity.onDestroy() at line 341 calls logout() with no guards for configuration changes
expecting: Activity recreation on screen wake → onDestroy() called → logout() executed → all credentials cleared
next_action: Fix by removing logout() from onDestroy() OR using isFinishing() check to only logout on final destroy

## Symptoms
expected: User remains logged in when screen is turned off and back on
actual: App logs user out when screen is turned back on after being off
errors: "Session cleared" / user logged out with no explicit logout action
reproduction: "Turn screen off, turn screen back on - user is logged out"
started: After recent fixes; issue persists on Pixel 9 Pro Android 16 and tablets

## Eliminated
- repeatOnLifecycle as the cause (recent fix removed this, issue persists)

## Evidence
- timestamp: 2026-04-10T21:30:30Z
  checked: MainActivity.kt onDestroy() method (line 337-345)
  found: "authenticationService?.logout() // Clear session cache if needed" on line 341
  implication: onDestroy() unconditionally calls logout() which clears ALL credentials, sessions, auth tokens, demo mode flag
  
- timestamp: 2026-04-10T21:30:45Z
  checked: requestedOrientation = SCREEN_ORIENTATION_PORTRAIT on line 127 in onCreate()
  found: Code sets requestedOrientation in onCreate() which can trigger configuration changes
  implication: On screen wake, if requestedOrientation value differs from current, Android destroys activity and recreates it
  
- timestamp: 2026-04-10T21:31:00Z
  checked: Session/auth clearing code across codebase
  found: No screen lock/unlock listeners. App is relying on Android lifecycle. The only session clearing is via logout() which happens in:
         1. onDestroy() in MainActivity.kt (line 341) - UNCONDITIONAL
         2. handleLogout in App.kt (line 169) - User-initiated logout
  implication: Activity being destroyed = automatic logout via onDestroy()
  
- timestamp: 2026-04-10T21:31:15Z
  checked: Why activity would be destroyed on screen wake
  found: requestedOrientation in onCreate() can trigger configuration changes. If:
         - Device is rotated during screen off
         - OR Android recreates activity for any reason on wake
         - THEN onCreate() sets requestedOrientation AGAIN
         - THEN onDestroy() is called before onCreate() completes
         - THEN logout() is called clearing all data
  implication: CRITICAL BUG: onDestroy() should NOT logout() unconditionally. It should only logout on app termination, not on configuration changes

## Resolution
root_cause: "MainActivity.onDestroy() unconditionally calls authenticationService?.logout() which clears ALL session/credential data. When activity is recreated (on configuration changes, screen wake, fold state change, etc.), onDestroy() is called even though the app is not terminating. This causes the user to be logged out on ANY activity recreation, including screen wake."
fix: "Removed the logout() call from MainActivity.onDestroy() (line 341). Replaced with comprehensive comment explaining why logout should NOT be called here. Cleanup of HttpClient continues via HttpClientManager lifecycle observer. Logout now only happens on explicit user action via SettingsPage logout button. Session data persisted in SecureStorage survives activity recreation."
verification: "[awaiting human verification - test screen wake on Pixel 9 Pro and tablets]"
files_changed:
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
