package app.simwire.gateway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.simwire.gateway.core.pairing.TokenStore
import app.simwire.gateway.ui.HealthScreen
import app.simwire.gateway.ui.HomeScreen
import app.simwire.gateway.ui.PairScreen
import app.simwire.gateway.ui.SimwireTheme

private enum class Screen { Home, Pair, Health }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimwireTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var paired by remember { mutableStateOf(TokenStore(context).isPaired) }

    when (screen) {
        Screen.Home -> HomeScreen(
            isPaired = paired,
            onPair = { screen = Screen.Pair },
            onHealth = { screen = Screen.Health },
        )
        Screen.Pair -> PairScreen(
            onPaired = {
                paired = TokenStore(context).isPaired
                screen = Screen.Home
            },
            onBack = { screen = Screen.Home },
        )
        Screen.Health -> HealthScreen(
            onBack = { screen = Screen.Home },
            onUnpaired = {
                paired = false
                screen = Screen.Home
            },
        )
    }
}
