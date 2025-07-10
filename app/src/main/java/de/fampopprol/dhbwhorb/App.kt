package de.fampopprol.dhbwhorb

import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.LoginScreen
import de.fampopprol.dhbwhorb.ui.screen.MainScreen
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import kotlinx.coroutines.launch

@Composable
fun App() {
    DHBWHorbTheme {
        val context = LocalContext.current
        val credentialManager = remember { CredentialManager(context) }
        var isLoggedIn by remember { mutableStateOf(credentialManager.isLoggedInBlocking()) }
        val dualisService = remember { DualisService() }
        val timetableCacheManager = remember { TimetableCacheManager(context) }
        val gradesCacheManager = remember { GradesCacheManager(context) }
        val scope = rememberCoroutineScope()

        // Auto-login if credentials are stored
        LaunchedEffect(Unit) {
            if (credentialManager.hasStoredCredentialsBlocking() && !isLoggedIn) {
                val username = credentialManager.getUsernameBlocking()
                val password = credentialManager.getPassword()
                if (username != null && password != null) {
                    dualisService.login(username, password) { result ->
                        if (result != null) {
                            isLoggedIn = true
                            Log.d("MainActivity", "Auto-login successful")
                        } else {
                            Log.e("MainActivity", "Auto-login failed")
                            scope.launch {
                                credentialManager.logout() // Clear invalid credentials
                                timetableCacheManager.clearCache() // Clear cached data on logout
                                gradesCacheManager.clearCache() // Clear grades cache on logout
                            }
                        }
                    }
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp) // Remove default content padding
        ) { innerPadding ->
            if (!isLoggedIn) {
                LoginScreen(
                    dualisService = dualisService,
                    credentialManager = credentialManager,
                    onLoginSuccess = { isLoggedIn = true },
                    modifier = Modifier
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.systemBars)
                )
            } else {
                MainScreen(
                    dualisService = dualisService,
                    credentialManager = credentialManager,
                    timetableCacheManager = timetableCacheManager,
                    gradesCacheManager = gradesCacheManager,
                    onLogout = {
                        isLoggedIn = false
                        scope.launch {
                            timetableCacheManager.clearCache() // Clear cached data on logout
                            gradesCacheManager.clearCache() // Clear grades cache on logout
                            Log.d("MainActivity", "Cleared cache during logout")
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}