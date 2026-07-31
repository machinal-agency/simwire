package app.simwire.gateway.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.simwire.gateway.R
import app.simwire.gateway.core.GatewayBus
import app.simwire.gateway.core.JournalKind

@Composable
fun HomeScreen(
    isPaired: Boolean,
    onPair: () -> Unit,
    onHealth: () -> Unit,
) {
    val status by GatewayBus.status.collectAsStateWithLifecycle()
    val journal by GatewayBus.journal.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.wordmark),
                contentDescription = "simwire",
                modifier = Modifier.width(96.dp),
            )
            TextButton(onClick = onHealth) {
                Text("Health", color = Grey50, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(30.dp))

        Overline("GATEWAY")
        Spacer(Modifier.height(4.dp))
        StatusRow(
            on = isPaired,
            label = if (isPaired) "Paired" else "Not paired",
        )
        HorizontalDivider(color = Hairline)
        StatusRow(
            on = status.running,
            label = if (status.running) "Running on ${status.lanAddress ?: "?"}:4650" else "Stopped",
        )
        HorizontalDivider(color = Hairline)
        StatusRow(
            on = status.clientName != null,
            label = status.clientName?.let { "Client: $it" } ?: "No client connected",
        )

        if (!isPaired) {
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onPair,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
                shape = RoundedCornerShape(999.dp),
            ) { Text("Pair with your computer", fontSize = 15.sp) }
        }

        Spacer(Modifier.height(34.dp))
        Overline("JOURNAL")
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Hairline)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (journal.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Send an SMS from your code and it shows up here.",
                        color = Grey50,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(journal.asReversed()) { entry ->
                Row(modifier = Modifier.padding(vertical = 9.dp)) {
                    Text(
                        entry.time,
                        color = Grey30,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        entry.text,
                        color = when (entry.kind) {
                            JournalKind.IN -> White
                            JournalKind.OUT -> White
                            JournalKind.SYS -> Grey50
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp,
                    )
                }
                HorizontalDivider(color = Hairline)
            }
        }
    }
}

@Composable
fun Overline(text: String) {
    Text(text, color = Grey50, fontSize = 11.sp, letterSpacing = 2.sp)
}

@Composable
private fun StatusRow(on: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .then(
                    if (on) Modifier.background(White, CircleShape)
                    else Modifier.border(1.dp, Grey30, CircleShape),
                ),
        )
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (on) White else Grey50, fontSize = 15.sp)
    }
}
