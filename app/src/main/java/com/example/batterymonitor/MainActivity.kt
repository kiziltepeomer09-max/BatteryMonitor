package com.example.batterymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // İzin reddedilse bile kullanıcıya bilgi verilebilir.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryMonitorScreen(
                        onStart = { startService() },
                        onStop = { stopService() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Pil optimizasyonu izni isteği
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Cihaz desteklemiyorsa yoksay
        }
    }

    private fun startService() {
        val intent = Intent(this, BatteryMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopService() {
        val intent = Intent(this, BatteryMonitorService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
    }
}

@Composable
fun BatteryMonitorScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val batteryData by BatteryDataRepository.currentFlow.collectAsState()
    val isTracking by BatteryDataRepository.isTracking.collectAsState()
    
    val latestValue = batteryData.lastOrNull() ?: 0f
    // Şarj oluyor (Pozitif) -> Yeşil, Deşarj (Negatif) -> Kırmızı
    val currentColor = if (latestValue >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isTracking) "Anlık Akım:" else "Takip Bekleniyor",
            fontSize = 20.sp,
            color = Color.Gray
        )
        
        Text(
            text = "${String.format("%.2f", latestValue)} mA",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = currentColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (batteryData.isNotEmpty()) {
            val chartEntries = batteryData.mapIndexed { index, value ->
                FloatEntry(x = index.toFloat(), y = value)
            }
            
            Chart(
                chart = lineChart(
                    lines = listOf(
                        lineSpec(lineColor = currentColor)
                    )
                ),
                model = entryModelOf(chartEntries),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        } else {
            Box(modifier = Modifier.height(250.dp), contentAlignment = Alignment.Center) {
                Text("Grafik verisi bekleniyor...", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (isTracking) onStop() else onStart() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color.Red else Color.Blue
            ),
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
        ) {
            Text(
                text = if (isTracking) "Takibi Durdur" else "Takibi Başlat",
                fontSize = 18.sp
            )
        }
    }
}
