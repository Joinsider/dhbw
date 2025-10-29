package de.joinside.dhbw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.aakira.napier.Napier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Test logging to verify Napier is working
        Napier.d("MainActivity onCreate() called", tag = "MainActivity")
        Napier.i("App is starting...", tag = "MainActivity")

        setContent {
            Napier.d("Setting content with App()", tag = "MainActivity")
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        Napier.d("MainActivity onResume() called", tag = "MainActivity")
    }

    override fun onPause() {
        super.onPause()
        Napier.d("MainActivity onPause() called", tag = "MainActivity")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}