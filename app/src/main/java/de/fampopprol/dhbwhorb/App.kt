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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.dualis.network.DualisService
import de.fampopprol.dhbwhorb.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.LoginScreen
import de.fampopprol.dhbwhorb.ui.screen.TimetableScreen
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme

@Composable
fun App() {
    DHBWHorbTheme {
        val context = LocalContext.current
        val credentialManager = remember { CredentialManager(context) }
        var isLoggedIn by remember { mutableStateOf(credentialManager.isLoggedIn()) }
        val dualisService = remember { DualisService() }
        val timetableCacheManager = remember { TimetableCacheManager(context) }

        // Auto-login if credentials are stored
        LaunchedEffect(Unit) {
            if (credentialManager.hasStoredCredentials() && !isLoggedIn) {
                val username = credentialManager.getUsername()
                val password = credentialManager.getPassword()
                if (username != null && password != null) {
                    dualisService.login(username, password) { result ->
                        if (result != null) {
                            isLoggedIn = true
                            Log.d("MainActivity", "Auto-login successful")
                        } else {
                            Log.e("MainActivity", "Auto-login failed")
                            credentialManager.logout() // Clear invalid credentials
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
                TimetableScreen(
                    dualisService = dualisService,
                    credentialManager = credentialManager,
                    timetableCacheManager = timetableCacheManager,
                    onLogout = { isLoggedIn = false },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}