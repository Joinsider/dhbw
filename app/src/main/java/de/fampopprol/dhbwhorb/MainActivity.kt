package de.fampopprol.dhbwhorb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import de.fampopprol.dhbwhorb.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.dualis.network.DualisService
import de.fampopprol.dhbwhorb.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.*
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    DHBWHorbTheme {
        LoginScreen(
            dualisService = DualisService(),
            credentialManager = CredentialManager(LocalContext.current),
            onLoginSuccess = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimetableScreenPreview() {
    DHBWHorbTheme {
        TimetableScreen(
            dualisService = DualisService(),
            credentialManager = CredentialManager(LocalContext.current),
            timetableCacheManager = TimetableCacheManager(LocalContext.current),
            onLogout = {}
        )
    }
}