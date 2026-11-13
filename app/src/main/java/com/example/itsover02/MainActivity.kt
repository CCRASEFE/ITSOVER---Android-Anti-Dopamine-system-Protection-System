package com.example.itsover02

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.itsover02.telegram.ControlState
import com.example.itsover02.telegram.TelegramDataStore
import com.example.itsover02.telegram.TelegramPreferences
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceAdminReceiver.enforceHardening(this)

        setContent {
            MaterialTheme {
                val isVpnRunning by viewModel.isRunning.collectAsState()
                MinimalistDashboard(
                    isVpnRunning = isVpnRunning,
                    onStartVpn = { requestVpnPermission() }
                )
            }
        }
    }

    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalDnsBlockerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun MinimalistDashboard(
    isVpnRunning: Boolean,
    onStartVpn: () -> Unit
) {
    val context = LocalContext.current
    val dataStore = remember { TelegramDataStore(context) }
    var prefs by remember { mutableStateOf<TelegramPreferences>(dataStore.getCurrentPreferences()) }

    LaunchedEffect(Unit) {
        val adminComponent = DeviceAdminReceiver.getComponentName(context)
        while (true) {
            val current = dataStore.getCurrentPreferences()
            // Si el tiempo llegó a 00:00, desbloquear en el acto y poner en verde
            if (current.controlState == ControlState.COOLDOWN && current.cooldownExpiresWall > 0L) {
                if (System.currentTimeMillis() >= current.cooldownExpiresWall) {
                    com.example.itsover02.telegram.PackageSuspender.setSuspended(
                        context,
                        adminComponent,
                        current.telegramPackage,
                        false
                    )
                    dataStore.updateState(ControlState.AVAILABLE)
                }
            }
            prefs = dataStore.getCurrentPreferences()
            delay(500L)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF161F36), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isVpnRunning) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isVpnRunning) "ESCUDO ACTIVO" else "ESCUDO PAUSADO",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "TELEGRAM CONTROL",
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (prefs.controlState) {
                ControlState.AVAILABLE -> {
                    Text(
                        text = "DISPONIBLE",
                        color = Color(0xFF10B981),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tienes 20 segundos al abrir la app",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }

                ControlState.ACTIVE_SESSION -> {
                    Text(
                        text = "EN USO",
                        color = Color(0xFFF59E0B),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Límite: 20 segundos",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp
                    )
                }

                ControlState.COOLDOWN -> {
                    val remainingMs = (prefs.cooldownExpiresWall - System.currentTimeMillis()).coerceAtLeast(0L)
                    val totalSec = remainingMs / 1000
                    val minutes = totalSec / 60
                    val seconds = totalSec % 60

                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color(0xFFEF4444),
                        fontSize = 56.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tiempo restante para volver a usar",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (!isVpnRunning) {
                Button(
                    onClick = onStartVpn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ACTIVAR VPN")
                }
            }
        }
    }
}