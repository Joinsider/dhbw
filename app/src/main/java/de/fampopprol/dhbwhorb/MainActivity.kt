package de.fampopprol.dhbwhorb

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.fampopprol.dhbwhorb.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.dualis.network.DualisService
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DHBWHorbTheme {
                var isLoggedIn by remember { mutableStateOf(false) }
                val dualisService = remember { DualisService() }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!isLoggedIn) {
                        LoginScreen(
                            dualisService = dualisService,
                            onLoginSuccess = { isLoggedIn = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        TimetableScreen(
                            dualisService = dualisService,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(dualisService: DualisService, onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") }
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") }
        )
        Button(onClick = {
            dualisService.login(username, password) { redirectUrl ->
                if (redirectUrl != null) {
                    onLoginSuccess()
                }
                Log.d("LoginScreen", "Redirect URL: $redirectUrl")
            }
        }) {
            Text("Login")
        }
    }
}

@Composable
fun TimetableScreen(dualisService: DualisService, modifier: Modifier = Modifier) {
    var timetable by remember { mutableStateOf<List<TimetableDay>?>(null) }

    // Fetch timetable for current month
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1 // Month is 0-indexed

    dualisService.getMonthlySchedule(year, month) { fetchedTimetable ->
        timetable = fetchedTimetable
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (timetable != null) {
            timetable?.forEach { day ->
                Text(text = day.date)
                day.events.forEach { event ->
                    Text(text = "  ${event.title} - ${event.time} - ${event.location} - ${event.lecturer}")
                }
            }
        } else {
            Text(text = "Loading timetable...")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    DHBWHorbTheme {
        LoginScreen(dualisService = DualisService(), onLoginSuccess = {})
    }
}

@Preview(showBackground = true)
@Composable
fun TimetableScreenPreview() {
    DHBWHorbTheme {
        TimetableScreen(dualisService = DualisService())
    }
}