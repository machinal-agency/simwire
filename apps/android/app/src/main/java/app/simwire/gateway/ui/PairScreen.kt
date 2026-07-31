package app.simwire.gateway.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simwire.gateway.core.GatewayService
import app.simwire.gateway.core.pairing.PairingClient
import app.simwire.gateway.core.pairing.PairingResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private sealed interface PairUi {
    data object Idle : PairUi
    data object Working : PairUi
    data class Done(val clientName: String) : PairUi
    data class Error(val reason: String) : PairUi
}

@Composable
fun PairScreen(onPaired: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val client = remember { PairingClient(context) }
    var state by remember { mutableStateOf<PairUi>(PairUi.Idle) }
    var scanned by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned = it } ?: run { state = PairUi.Idle }
    }

    LaunchedEffect(scanned) {
        val raw = scanned ?: return@LaunchedEffect
        state = PairUi.Working
        val payload = client.parseQr(raw)
        state = if (payload == null) {
            PairUi.Error("That QR is not a simwire pairing code.")
        } else {
            when (val result = client.pair(payload)) {
                is PairingResult.Success -> {
                    GatewayService.start(context)
                    PairUi.Done(result.clientName)
                }
                is PairingResult.Failure -> PairUi.Error(result.reason)
            }
        }
        scanned = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val ui = state) {
            PairUi.Idle -> {
                Text(
                    "Pair with your computer",
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "npx simwire pair",
                    color = Grey70,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Run this in your terminal, then scan the QR it shows.",
                    color = Grey50,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                WhitePill("Open the scanner") {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the QR from your terminal")
                            .setBeepEnabled(false)
                            .setCaptureActivity(PortraitCaptureActivity::class.java),
                    )
                }
            }
            PairUi.Working -> {
                CircularProgressIndicator(color = White)
                Spacer(Modifier.height(18.dp))
                Text("Shaking hands over Wi-Fi…", color = Grey50, fontSize = 14.sp)
            }
            is PairUi.Done -> {
                Text("Paired ✓", color = White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("This phone now answers to ${ui.clientName}.", color = Grey50, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                WhitePill("Done", onPaired)
            }
            is PairUi.Error -> {
                Text("Pairing failed", color = White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(ui.reason, color = Grey50, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                WhitePill("Try again") { state = PairUi.Idle }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("← Back", color = Grey50, fontSize = 14.sp) }
    }
}

@Composable
private fun WhitePill(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
        shape = RoundedCornerShape(999.dp),
    ) { Text(label, fontSize = 15.sp) }
}
