package com.GregorKobel.iot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.GregorKobel.iot.ui.theme.IoTTheme
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database: DatabaseReference = Firebase.database.reference
//        Firebase.database.setPersistenceEnabled(false)
        enableEdgeToEdge()

        setContent {
            IoTTheme {
                val context = applicationContext
                ResiInputScreen(context, database)
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
            delay(2000)

            try {
                val snapshot = database.child("resi").get().await()
                val tempList = mutableListOf<ResiStatus>()

                for (child in snapshot.children) {
                    val number = child.child("number").getValue(String::class.java) ?: continue
                    val scanned = child.child("scanned").getValue(Boolean::class.java) ?: false
                    val alreadyNotified = child.child("alreadyNotified").getValue(Boolean::class.java) ?: false

                    val photoUrls = child.child("photoUrl").children.mapNotNull {
                        it.getValue(String::class.java)
                    }

                    Log.d("ResiDebug", "number=$number, scanned=$scanned, alreadyNotified=$alreadyNotified")

                    if (scanned && !alreadyNotified) {
                        showNotification(
                            context,
                            "Resi Terverifikasi",
                            "Nomor resi $number berhasil dipindai."
                        )

                        Log.d("ResiDebug", "✅ Notifikasi ditampilkan untuk resi: $number")
                        child.ref.child("alreadyNotified").setValue(true)

                        statusMessage = "📦 Nomor resi $number berhasil dipindai."
                        isResiFound = true
                    }

                    tempList.add(ResiStatus(number, scanned, photoUrls))
                }

                resiStatusList = tempList

            } catch (e: Exception) {
                statusMessage = "❌ Gagal memuat data: ${e.message}"
                Log.e("ResiDebug", "Gagal memuat data", e)
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
            submitResi(
                database,
                resi,
                onSuccess = {
                    statusMessage = "✅ Nomor resi berhasil disimpan."
                },
                onError = { e ->
                    statusMessage = "❌ Gagal menyimpan resi: ${e.message}"
                },
                onEmpty = {
                    statusMessage = "⚠️ Nomor resi kosong."
                }
            )
        },
        statusMessage = statusMessage,
        isResiFound = isResiFound,
        resiStatusList = resiStatusList,
        context = context
    )
}



@Composable
fun ResiInputUI(
    resi: String,
    onResiChange: (String) -> Unit,
    onSubmit: () -> Unit,
    statusMessage: String,
    isResiFound: Boolean,
    resiStatusList: List<ResiStatus>,
    context: Context
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

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

        Text("📋 Daftar Resi:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(resiStatusList) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = item.resi)
                        Text(
                            text = if (item.isScanned) "✅ Sudah discan" else "⏳ Belum discan",
                            color = if (item.isScanned) Color.Green else Color.Gray
                        )
                    }

                    if (item.photoUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                selectedPhotos = item.photoUrls
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Lihat Foto")
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Tutup")
                    }
                },
                title = { Text("Foto Resi") },
                text = {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(selectedPhotos) { url ->
                            Log.d("PhotoURLDebug", "Menampilkan foto dari URL: $url") // DEBUG LOG DI SINI

                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCachePolicy(CachePolicy.DISABLED)  // Nonaktifkan cache memory
                                    .diskCachePolicy(CachePolicy.DISABLED)    // Nonaktifkan cache disk
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}




data class ResiStatus(
    val resi: String,
    val isScanned: Boolean,
    val photoUrls: List<String> = emptyList()
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






