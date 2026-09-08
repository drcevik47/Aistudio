package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LogEntity
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalErrorDark
import com.example.ui.theme.MinimalErrorLight
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryDark
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
import com.example.ui.theme.MinimalSuccess
import com.example.ui.theme.MinimalSuccessDark
import com.example.ui.theme.MinimalSuccessLight
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceBorderLight
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import com.example.ui.theme.MinimalWarning
import com.example.ui.theme.MinimalWarningLight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    logs: List<LogEntity>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedLevel by remember { mutableStateOf("ALL") }
    var showShareSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var copiedFeedbackId by remember { mutableStateOf<Long?>(null) }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, selectedLevel) {
        if (selectedLevel == "ALL") logs
        else logs.filter { it.level.equals(selectedLevel, ignoreCase = true) }
    }

    fun copyToClipboard(text: String, label: String) {
        clipboardManager.setText(AnnotatedString(text))
        coroutineScope.launch {
            snackbarHostState.showSnackbar("$label panoya kopyalandı! Şimdi sohbete yapıştırabilirsiniz.")
        }
        Toast.makeText(context, "$label kopyalandı!", Toast.LENGTH_SHORT).show()
    }

    fun shareViaIntent(text: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Logları Paylaş")
        context.startActivity(shareIntent)
    }

    fun shareLogsAsFile(
        logsToExport: List<LogEntity>,
        fileNamePrefix: String,
        reportTitle: String
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${fileNamePrefix}_$timestamp.txt"
            val logsDir = File(context.cacheDir, "logs").apply { if (!exists()) mkdirs() }
            val file = File(logsDir, fileName)

            val reportContent = generateDetailedLogReportText(
                context = context,
                logs = logsToExport,
                reportTitle = reportTitle,
                allLogs = logs
            )
            file.writeText(reportContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(fileName, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$reportTitle ($timestamp).txt")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Bybit Rebalancer Bot sistem log raporu ektedir: $fileName (${logsToExport.size} kayıt).\nYapay zeka analizi ve hata tespiti için hazırlanmıştır."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(sendIntent, "Log Dosyasını Paylaş (.txt)")
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Log dosyası oluşturulamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("log_viewer_screen")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Sistem Logları",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Canlı telemetri • ${logs.size} kayıt (Maks. 10.000 / 24s döngü)",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary,
                            maxLines = 1
                        )
                    }
                }

                // Action Buttons: Share, Fast Copy & Clear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (logs.isNotEmpty()) {
                        // Paylaş / Dışa Aktar Butonu (Birincil)
                        Surface(
                            onClick = { showShareSheet = true },
                            shape = RoundedCornerShape(999.dp),
                            color = MinimalPrimary,
                            modifier = Modifier.testTag("share_logs_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Logları Paylaş",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Paylaş",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Hızlı Kopyala Butonu
                        IconButton(
                            onClick = {
                                val text = formatLogsForSharing(
                                    logs = logs.take(100),
                                    title = "Son 100 Log Kaydı",
                                    dateFormat = dateFormat
                                )
                                copyToClipboard(text, "Son 100 log")
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MinimalSurfaceElevated)
                                .border(1.dp, MinimalSurfaceBorder, CircleShape)
                                .testTag("fast_copy_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Son 100 Logu Kopyala",
                                tint = MinimalPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Logları Temizle
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MinimalSurfaceElevated)
                                .border(1.dp, MinimalSurfaceBorder, CircleShape)
                                .testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = "Logları Temizle",
                                tint = MinimalTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = {
                        Text(
                            text = "Log Geçmişini Temizle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MinimalTextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "Sistem logları artık otomatik olarak 24 saatten eski kayıtları ve 10.000 adedi aşanları düzenli olarak siler.\n\nMevcut ${logs.size} log kaydının tamamını şimdi temizlemek istiyor musunuz?",
                            fontSize = 13.sp,
                            color = MinimalTextSecondary,
                            lineHeight = 18.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearConfirmDialog = false
                                onClearLogs()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Tüm loglar temizlendi.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MinimalError)
                        ) {
                            Text("Tümünü Temizle", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text("Vazgeç", color = MinimalTextSecondary)
                        }
                    },
                    containerColor = MinimalSurface,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Filter Chips (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "Hepsi (${logs.size})",
                    "ERROR" to "Hatalar",
                    "SUCCESS" to "Başarılı",
                    "WARN" to "Uyarılar",
                    "INFO" to "Bilgi"
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = level },
                        shape = RoundedCornerShape(999.dp),
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedLevel == level) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinimalPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = MinimalSurface,
                            labelColor = MinimalTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedLevel == level,
                            borderColor = MinimalSurfaceBorder,
                            selectedBorderColor = MinimalPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MinimalSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MinimalTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Henüz log kaydı yok",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Sistem olayları, bot emirleri ve API yanıtları burada listelenir.",
                            color = MinimalTextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val isCopied = copiedFeedbackId == log.id
                        LogItemCard(
                            log = log,
                            timeStr = dateFormat.format(Date(log.timestamp)),
                            isCopied = isCopied,
                            onCopyClick = {
                                val singleLogStr = "[${dateFormat.format(Date(log.timestamp))}] [${log.level}] [${log.tag}] ${log.message}${if (log.details.isNotBlank()) " | ${log.details}" else ""}"
                                copyToClipboard(singleLogStr, "Log satırı")
                                copiedFeedbackId = log.id
                            }
                        )
                    }
                }
            }
        }

        // Snackbar notification
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // Paylaşım & Dışa Aktarma Menüsü (Modal BottomSheet)
        if (showShareSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShareSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MinimalSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                ShareLogsBottomSheetContent(
                    logs = logs,
                    filteredLogs = filteredLogs,
                    selectedFilter = selectedLevel,
                    dateFormat = dateFormat,
                    onOptionCopy = { text, label ->
                        copyToClipboard(text, label)
                        showShareSheet = false
                    },
                    onOptionShare = { text, title ->
                        shareViaIntent(text, title)
                        showShareSheet = false
                    },
                    onOptionShareFile = { logsToExport, filePrefix, title ->
                        shareLogsAsFile(logsToExport, filePrefix, title)
                        showShareSheet = false
                    },
                    onDismiss = { showShareSheet = false }
                )
            }
        }
    }
}

@Composable
private fun ShareLogsBottomSheetContent(
    logs: List<LogEntity>,
    filteredLogs: List<LogEntity>,
    selectedFilter: String,
    dateFormat: SimpleDateFormat,
    onOptionCopy: (text: String, label: String) -> Unit,
    onOptionShare: (text: String, title: String) -> Unit,
    onOptionShareFile: (logsToExport: List<LogEntity>, filePrefix: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val errorLogs = remember(logs) {
        logs.filter { it.level.equals("ERROR", true) || it.level.equals("WARN", true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Logları Paylaş & Dışa Aktar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextPrimary
                )
                Text(
                    text = "Yapay zekaya (Gemini/Claude) veya geliştiriciye iletmek için seçenek belirleyin",
                    fontSize = 12.sp,
                    color = MinimalTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // BÖLÜM 1: DOSYA OLARAK PAYLAŞ (.TXT) - Yapay Zeka İçin En İyi Seçenek
        Text(
            text = "📄 DOSYA OLARAK DIŞA AKTAR (.TXT - EN İYİ FORMAT)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MinimalPrimary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Seçenek A: Tüm Logları TXT Dosyası Olarak Paylaş (.txt)
        ShareActionCard(
            title = "Tüm Logları TXT Dosyası Olarak Paylaş (.txt)",
            subtitle = "Sınırsız satır • Yapay zeka sohbetine doğrudan dosya olarak yükleyin (${logs.size} satır)",
            icon = Icons.Default.Description,
            badge = "En İyi Seçenek",
            badgeColor = MinimalPrimary,
            trailingIcon = Icons.Default.FileUpload,
            isPrimaryHighlight = true,
            onClick = {
                onOptionShareFile(logs, "bybit_bot_tum_loglar", "Bybit Bot - Tüm Sistem Telemetri Logları")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Seçenek B: Hata ve Teşhis Raporunu TXT Dosyası Olarak Paylaş (.txt)
        ShareActionCard(
            title = "Hata Raporunu TXT Dosyası Olarak Paylaş (${errorLogs.size} Kayıt)",
            subtitle = "Sadece ERROR ve WARN loglarını içeren hata teşhis dosyası (.txt)",
            icon = Icons.Default.BugReport,
            badge = "Hata Teşhis",
            badgeColor = MinimalError,
            trailingIcon = Icons.Default.FileUpload,
            isPrimaryHighlight = true,
            onClick = {
                onOptionShareFile(errorLogs, "bybit_bot_hata_raporu", "Bybit Bot - Hata ve Teşhis Raporu")
            }
        )

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = MinimalSurfaceBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // BÖLÜM 2: PANOYA KOPYALA (Metin Olarak)
        Text(
            text = "📋 PANOYA KOPYALA (HIZLI YAPIŞTIRMA)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MinimalTextSecondary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Seçenek 1: Son 50 Log
        ShareActionCard(
            title = "Son 50 Logu Kopyala",
            subtitle = "Sohbet mesajına doğrudan yapıştırmak için optimize edilmiş metin",
            icon = Icons.Default.ContentCopy,
            badge = "Hızlı",
            badgeColor = MinimalSuccess,
            onClick = {
                val text = formatLogsForSharing(
                    logs = logs.take(50),
                    title = "Son 50 Log Kaydı",
                    dateFormat = dateFormat
                )
                onOptionCopy(text, "Son 50 log")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Seçenek 2: Son 100 Log
        ShareActionCard(
            title = "Son 100 Logu Kopyala",
            subtitle = "Son durum ve detaylı son işlemler için panoya kopyalar",
            icon = Icons.Default.ContentCopy,
            onClick = {
                val text = formatLogsForSharing(
                    logs = logs.take(100),
                    title = "Son 100 Log Kaydı",
                    dateFormat = dateFormat
                )
                onOptionCopy(text, "Son 100 log")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Seçenek 3: Sadece Hatalar ve Uyarılar
        ShareActionCard(
            title = "Sadece Hata & Uyarıları Kopyala (${errorLogs.size} Adet)",
            subtitle = "Sistemdeki aksaklık ve hata teşhisinde en kritik kayıtlar",
            icon = Icons.Default.Warning,
            badge = "Sorun Tespiti",
            badgeColor = MinimalError,
            onClick = {
                val text = formatLogsForSharing(
                    logs = errorLogs.take(100),
                    title = "Hata ve Uyarı Logları",
                    dateFormat = dateFormat
                )
                onOptionCopy(text, "Hata ve uyarı logları")
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Seçenek 4: Şu anki filtrelenmiş görünüm
        ShareActionCard(
            title = "Filtrelenen Logları Kopyala ($selectedFilter - ${filteredLogs.size} Kayıt)",
            subtitle = "Şu anda ekranda açık olan listenin tamamını kopyalar",
            icon = Icons.Default.Terminal,
            onClick = {
                val text = formatLogsForSharing(
                    logs = filteredLogs.take(300),
                    title = "Filtrelenmiş Loglar ($selectedFilter)",
                    dateFormat = dateFormat
                )
                onOptionCopy(text, "Filtrelenmiş loglar")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MinimalSurfaceBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // Android Paylaşım Sayfası (WhatsApp, Telegram, Mail, Notlar)
        Button(
            onClick = {
                val text = formatLogsForSharing(
                    logs = logs.take(100),
                    title = "Bybit Bot Logları",
                    dateFormat = dateFormat
                )
                onOptionShare(text, "Bybit Rebalancer Bot Logları")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MinimalPrimary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Android Paylaşım Menüsü ile Gönder (Metin)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ShareActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = MinimalPrimary,
    trailingIcon: ImageVector = Icons.Default.ContentCopy,
    isPrimaryHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimaryHighlight) MinimalPrimaryLight.copy(alpha = 0.35f) else MinimalSurfaceElevated
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isPrimaryHighlight) 1.5.dp else 1.dp,
            if (isPrimaryHighlight) badgeColor.copy(alpha = 0.45f) else MinimalSurfaceBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary
                        )
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = badgeColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (isPrimaryHighlight) badgeColor else MinimalTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatLogsForSharing(
    logs: List<LogEntity>,
    title: String,
    dateFormat: SimpleDateFormat
): String {
    val sb = StringBuilder()
    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val now = fullDateFormat.format(Date())

    sb.appendLine("```text")
    sb.appendLine("==================================================")
    sb.appendLine("🤖 BYBIT SPOT REBALANCER TELEMETRİ LOGLARI")
    sb.appendLine("📅 Dışa Aktarma Zamanı: $now")
    sb.appendLine("📋 Kapsam: $title (Toplam ${logs.size} Satır)")
    sb.appendLine("==================================================")

    if (logs.isEmpty()) {
        sb.appendLine("(Kayıtlı log bulunamadı)")
    } else {
        for (log in logs) {
            val time = dateFormat.format(Date(log.timestamp))
            val levelTag = String.format("%-7s", log.level.uppercase())
            val categoryTag = String.format("%-12s", "[${log.tag}]")
            val detailsStr = if (log.details.isNotBlank()) "\n      ↳ Detay: ${log.details}" else ""
            sb.appendLine("[$time] $levelTag $categoryTag ${log.message}$detailsStr")
        }
    }
    sb.appendLine("==================================================")
    sb.appendLine("```")
    return sb.toString()
}

/**
 * Yapay zeka analizi, dosya paylaşımı ve hata teşhisi için zenginleştirilmiş .txt formatında rapor metni üretir.
 */
private fun generateDetailedLogReportText(
    context: Context,
    logs: List<LogEntity>,
    reportTitle: String,
    allLogs: List<LogEntity>
): String {
    val sb = StringBuilder()
    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    val errorCount = allLogs.count { it.level.equals("ERROR", true) }
    val warnCount = allLogs.count { it.level.equals("WARN", true) }
    val infoCount = allLogs.count { it.level.equals("INFO", true) }
    val successCount = allLogs.count { it.level.equals("SUCCESS", true) }

    sb.appendLine("================================================================================")
    sb.appendLine("🤖 BYBIT SPOT REBALANCER - SİSTEM VE TELEMETRİ RAPORU (.TXT)")
    sb.appendLine("================================================================================")
    sb.appendLine("Rapor Türü         : $reportTitle")
    sb.appendLine("Dışa Aktarma Tarihi: $now")
    sb.appendLine("Uygulama Paketi    : ${context.packageName}")
    sb.appendLine("Cihaz & Model      : ${Build.MANUFACTURER.uppercase(Locale.US)} ${Build.MODEL}")
    sb.appendLine("Android Sürümü     : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    sb.appendLine("Raporlanan Log     : ${logs.size} adet satır")
    sb.appendLine("Veritabanı Toplamı : ${allLogs.size} adet log kaydı")
    sb.appendLine("İstatistik Dağılımı: ERROR: $errorCount | WARN: $warnCount | INFO: $infoCount | SUCCESS: $successCount")
    sb.appendLine("================================================================================")
    sb.appendLine()

    // 1. Kritik Hatalar Özeti (Varsa en başta dikkat çeker)
    val recentErrors = allLogs.filter { it.level.equals("ERROR", true) }.take(15)
    if (recentErrors.isNotEmpty()) {
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("🚨 SON KRİTİK HATALAR ÖZETİ (${recentErrors.size} Adet Kayıt)")
        sb.appendLine("--------------------------------------------------------------------------------")
        for (err in recentErrors) {
            val time = fullDateFormat.format(Date(err.timestamp))
            sb.appendLine("[$time] [${err.tag}] ${err.message}")
            if (err.details.isNotBlank()) {
                sb.appendLine("   ↳ Detay: ${err.details}")
            }
        }
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine()
    }

    // 2. Kronolojik Log Akışı
    sb.appendLine("--------------------------------------------------------------------------------")
    sb.appendLine("📜 LOG KAYITLARI AKIŞI (${logs.size} Satır)")
    sb.appendLine("Format: [Zaman Damgası] [Seviye] [Kategori/Tag] Mesaj")
    sb.appendLine("--------------------------------------------------------------------------------")

    if (logs.isEmpty()) {
        sb.appendLine("(Kayıtlı log bulunmamaktadır)")
    } else {
        for (log in logs) {
            val time = fullDateFormat.format(Date(log.timestamp))
            val levelTag = String.format("%-7s", log.level.uppercase(Locale.US))
            val categoryTag = String.format("%-15s", "[${log.tag}]")
            val detailsStr = if (log.details.isNotBlank()) "\n     ↳ Detay: ${log.details}" else ""
            sb.appendLine("[$time] $levelTag $categoryTag ${log.message}$detailsStr")
        }
    }

    sb.appendLine()
    sb.appendLine("================================================================================")
    sb.appendLine("RAPOR SONU - Bybit Rebalancer Diagnostic Log File (.txt)")
    sb.appendLine("================================================================================")

    return sb.toString()
}

@Composable
private fun LogItemCard(
    log: LogEntity,
    timeStr: String,
    isCopied: Boolean,
    onCopyClick: () -> Unit
) {
    val levelColor = when (log.level.uppercase()) {
        "ERROR" -> MinimalErrorDark
        "WARN" -> MinimalWarning
        "SUCCESS" -> MinimalSuccessDark
        else -> MinimalPrimary
    }

    val levelBg = when (log.level.uppercase()) {
        "ERROR" -> MinimalErrorLight
        "WARN" -> MinimalWarningLight
        "SUCCESS" -> MinimalSuccessLight
        else -> MinimalPrimaryLight
    }

    val levelIcon = when (log.level.uppercase()) {
        "ERROR" -> Icons.Default.Error
        "WARN" -> Icons.Default.Warning
        "SUCCESS" -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopyClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(levelBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = levelIcon,
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = levelBg
                        ) {
                            Text(
                                text = log.level,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.tag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinimalTextSecondary,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeStr,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinimalTextMuted
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onCopyClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Logu Kopyala",
                                tint = if (isCopied) MinimalSuccessDark else MinimalTextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = log.message,
                    fontSize = 12.sp,
                    color = MinimalTextPrimary,
                    fontWeight = if (log.level == "ERROR") FontWeight.SemiBold else FontWeight.Normal
                )

                if (log.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.details,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MinimalTextMuted
                    )
                }
            }
        }
    }
}

