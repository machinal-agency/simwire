package app.simwire.gateway.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private data class Check(val label: String, val ok: Boolean, val fix: (() -> Unit)?)

@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    val checks = remember(refresh) { buildChecks(context, permissionLauncher::launch) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Text("Health", color = White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything the gateway needs to stay alive.",
            color = Grey50,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(26.dp))

        HorizontalDivider(color = Hairline)
        checks.forEach { check ->
            CheckRow(check) { refresh++ }
            HorizontalDivider(color = Hairline)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("← Back", color = Grey50, fontSize = 14.sp) }
    }
}

@Composable
private fun CheckRow(check: Check, onAfterFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = check.fix != null) {
                check.fix?.invoke()
                onAfterFix()
            }
            .padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (check.ok) "✓" else "✗",
                color = if (check.ok) White else Grey50,
                fontSize = 15.sp,
            )
            Spacer(Modifier.width(16.dp))
            Text(check.label, fontSize = 15.sp, color = if (check.ok) White else Grey70)
        }
        if (!check.ok && check.fix != null) {
            Text(
                "Fix",
                color = White,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

private fun buildChecks(
    context: Context,
    requestPermissions: (Array<String>) -> Unit,
): List<Check> {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    val smsPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE,
    )
    val power = context.getSystemService(PowerManager::class.java)
    val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true

    return listOf(
        Check(
            label = "SMS permissions",
            ok = smsPermissions.all(::granted),
            fix = { requestPermissions(smsPermissions) },
        ),
        Check(
            label = "Notifications allowed",
            ok = if (android.os.Build.VERSION.SDK_INT >= 33) {
                granted(Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            fix = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
        ),
        Check(
            label = "Battery optimization off",
            ok = unrestricted,
            fix = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
    )
}
