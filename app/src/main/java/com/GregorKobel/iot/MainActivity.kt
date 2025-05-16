package com.GregorKobel.iot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.GregorKobel.iot.ui.theme.IoTTheme
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App
        val database : DatabaseReference = Firebase.database.reference
        enableEdgeToEdge()

        // Set the content for the app
        setContent {
            IoTTheme {
                val context = applicationContext
                ResiInputScreen(context,database)
            }
        }
    }
}

@Composable
fun ResiInputScreen(context: Context, database: DatabaseReference) {
    var resi by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isResiFound by remember { mutableStateOf(false) }
    var resiStatusList by remember { mutableStateOf<List<ResiStatus>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)

            try {
                val resiSnapshot = database.child("resi").get().await()
                val scannedSnapshot = database.child("scanned_codes").get().await()

                val resiList = mutableListOf<ResiStatus>()
                val scannedList = scannedSnapshot.children.mapNotNull {
                    it.child("data").getValue(String::class.java)
                }

                for (child in resiSnapshot.children) {
                    val key = child.key ?: continue
                    val number = child.child("number").getValue(String::class.java) ?: continue
                    val scanned = child.child("scanned").getValue(Boolean::class.java) ?: false

                    // Jika belum ditandai scanned, dan ada di scanned_codes → tandai
                    if (!scanned && scannedList.contains(number)) {
                        database.child("resi").child(key).child("scanned").setValue(true).await()

                        showNotification(
                            context.applicationContext,
                            "Resi ditemukan",
                            "Nomor resi $number berhasil dipindai."
                        )

                        isResiFound = true
                        statusMessage = "Nomor resi $number berhasil dipindai."

                        try {
                            database.child("solenoid-lock").child("unlock").setValue(true).await()
                            Log.d("Firebase", "🔓 Solenoid di-unlock selama 20 detik")
                        } catch (e: Exception) {
                            Log.e("FirebaseError", "Gagal mengubah nilai unlock: ${e.message}")
                        }

                        delay(20_000)

                        database.child("solenoid-lock").child("unlock").setValue(false).await()
                        Log.d("Firebase", "🔒 Solenoid di-lock kembali")

                        isResiFound = false
                        statusMessage = ""
                    }

                    resiList.add(ResiStatus(number, scanned))
                }

                resiStatusList = resiList

            } catch (e: Exception) {
                Log.e("Firebase", "❌ Gagal mengecek resi: ${e.message}")
            }
        }
    }

    ResiInputUI(
        resi = resi,
        onResiChange = {
            resi = it
            isResiFound = false
            statusMessage = ""
        },
        onSubmit = {
            submitResi(database, resi,
                onSuccess = {
                    statusMessage = "✅ Nomor resi berhasil disimpan."
                },
                onError = { e ->
                    statusMessage = "❌ Gagal menyimpan resi: ${e.message}"
                },
                onEmpty = {
                    statusMessage = "⚠️ Nomor resi kosong, silakan masukkan nomor resi."
                }
            )
        },
        statusMessage = statusMessage,
        isResiFound = isResiFound,
        resiStatusList = resiStatusList
    )
}

@Composable
fun ResiInputUI(
    resi: String,
    onResiChange: (String) -> Unit,
    onSubmit: () -> Unit,
    statusMessage: String,
    isResiFound: Boolean,
    resiStatusList: List<ResiStatus>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = resi,
            onValueChange = onResiChange,
            label = { Text("Masukkan nomor resi") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onSubmit) {
            Text("Input Resi")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = statusMessage,
            color = if (isResiFound) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("📦 Daftar Resi:", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(resiStatusList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = item.resi)
                    Text(
                        text = if (item.isScanned) "✅ Sudah discan" else "⏳ Belum discan",
                        color = if (item.isScanned) Color.Green else Color.Gray
                    )
                }
            }
        }
    }
}

data class ResiStatus(
    val resi: String,
    val isScanned: Boolean
)

fun submitResi(
    database: DatabaseReference,
    resi: String,
    onSuccess: () -> Unit,
    onError: (Exception) -> Unit,
    onEmpty: () -> Unit
) {
    if (resi.isNotEmpty()) {
        val resiRef = database.child("resi").push()
        val resiMap = mapOf(
            "number" to resi,
            "scanned" to false
        )
        resiRef.setValue(resiMap)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    } else {
        onEmpty()
    }
}

fun showNotification(context: Context, title: String, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "resi_channel"

    val channel = NotificationChannel(
        channelId,
        "Resi Notification",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(1, notification)
}






