package com.example.documentscannerportable

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.documentscannerportable.ui.theme.DocumentScannerPortableTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline


class MainActivity : ComponentActivity() {

    @Composable
    fun SplashScreen(onTimeout: () -> Unit) {
        LaunchedEffect(Unit) {
            delay(2000)
            onTimeout()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_2),
                contentDescription = "App Splash Logo",
                modifier = Modifier.size(150.dp)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocumentScannerPortableTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen {
                        showSplash = false
                    }
                } else {
                    DocumentScannerScreen()
                }
            }
        }
    }
}

@Composable
fun DocumentScannerScreen() {
    val context = LocalContext.current
    var pdfFiles by remember { mutableStateOf(loadScannedPdfs(context.filesDir)) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showTalkback by remember { mutableStateOf(false) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }



    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

                scanResult?.pdf?.let { pdf ->
                    val fileName = "scan_${System.currentTimeMillis()}.pdf"
                    val outputFile = File(context.filesDir, fileName)
                    val fos = FileOutputStream(outputFile)
                    context.contentResolver.openInputStream(pdf.uri)?.use {
                        it.copyTo(fos)
                    }
                    fos.close()
                    pdfFiles = loadScannedPdfs(context.filesDir)
                    showTalkback = true
                }
            }
        }
    )

    if (showTalkback) {
        LaunchedEffect(showTalkback) {
            snackbarHostState.showSnackbar("Scan completed")

            val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
            if (accessibilityManager?.isEnabled == true) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    text.add("Scan completed")
                    className = javaClass.name
                    packageName = context.packageName
                }
                accessibilityManager.sendAccessibilityEvent(event)
            }

            showTalkback = false
        }
    }

    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setPageLimit(5)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    if (showRenameDialog && fileToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val renamed = File(context.filesDir, "$newFileName.pdf")
                    if (fileToRename!!.renameTo(renamed)) {
                        pdfFiles = loadScannedPdfs(context.filesDir)
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete this file?") },
            confirmButton = {
                TextButton(onClick = {
                    if (fileToDelete!!.delete()) {
                        pdfFiles = loadScannedPdfs(context.filesDir)
                        Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }



    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scanner.getStartScanIntent(context as ComponentActivity)
                        .addOnSuccessListener {
                            scannerLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                        }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Scan")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            Text(
                text = "Scanned PDFs",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                if (pdfFiles.isEmpty()) {
                    Text("No scanned PDFs yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    pdfFiles.forEach { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val previewBitmap = remember(file) {
                                    getPdfPreview(file)
                                }

                                previewBitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    file
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW)
                                                intent.setDataAndType(uri, "application/pdf")
                                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                context.startActivity(intent)
                                            }
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 2
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Tap to open",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    IconButton(onClick = {
                                        // TODO: Rename logic
                                        fileToRename = file
                                        newFileName = file.nameWithoutExtension
                                        showRenameDialog = true

                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.DriveFileRenameOutline, // Replace with appropriate icon
                                            contentDescription = "Rename PDF"
                                        )
                                    }

                                    IconButton(onClick = {
                                        // TODO: Delete logic
                                        fileToDelete = file
                                        showDeleteDialog = true

                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete, // Replace with appropriate icon
                                            contentDescription = "Delete PDF",
                                            tint = Color.Red
                                        )
                                    }

                                    IconButton(onClick = {
                                        sharePdf(context, file)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share PDF",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(onClick = {
                                        performOcrOnPdf(context, file)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.TextSnippet, // or use any OCR-style icon
                                            contentDescription = "Extract Text from PDF",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                }

                            }
                        }
                    }
                }
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),
    background = Color(0xFFF9F9F9),
    surface = Color.White,
    onSurface = Color.Black,
)

fun getPdfPreview(file: File): Bitmap? {
    return try {
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFileDescriptor)
        val page = renderer.openPage(0)

        val bitmap = Bitmap.createBitmap(
            page.width,
            page.height,
            Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        page.close()
        renderer.close()
        parcelFileDescriptor.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun loadScannedPdfs(dir: File): List<File> {
    return dir.listFiles { file -> file.extension == "pdf" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}
