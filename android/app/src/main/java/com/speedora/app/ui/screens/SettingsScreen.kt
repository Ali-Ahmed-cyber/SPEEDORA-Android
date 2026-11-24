package com.speedora.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.speedora.app.BuildConfig
import com.speedora.app.R
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.data.files.DeviceStorageSummary
import com.speedora.app.data.local.SpeedoraSettings

@Composable
fun SettingsScreen(
    settings: SpeedoraSettings,
    storageSummary: DeviceStorageSummary,
    onLanguageChange: (String) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onAnimationIntensityChange: (Float) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onBatterySaverChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onSoundsChange: (Boolean) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onDownloadTreeChange: (String?) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    onAllowTrustedAutoChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var nameDraft by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onDownloadTreeChange(uri.toString())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.dest_settings), style = MaterialTheme.typography.headlineSmall)
        }

        // Language
        item {
            SettingsCard(title = stringResource(R.string.settings_language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.languageCode == "ar",
                        onClick = {
                            onLanguageChange("ar")
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                        },
                        label = { Text("العربية (RTL)") }
                    )
                    FilterChip(
                        selected = settings.languageCode == "en",
                        onClick = {
                            onLanguageChange("en")
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        },
                        label = { Text("English (LTR)") }
                    )
                }
            }
        }

        // Theme & Motion
        item {
            SettingsCard(title = stringResource(R.string.settings_theme)) {
                SettingsToggle(stringResource(R.string.settings_dark), settings.darkTheme, onDarkThemeChange)
                SettingsToggle(stringResource(R.string.settings_reduced_motion), settings.reducedMotion, onReducedMotionChange)
                SettingsToggle(stringResource(R.string.settings_battery_saver), settings.batterySavingMode, onBatterySaverChange)
                Text("شدة الحركة: ${(settings.animationIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.animationIntensity,
                    onValueChange = onAnimationIntensityChange
                )
            }
        }

        // Device Identity & Download Folder
        item {
            SettingsCard(title = stringResource(R.string.settings_device_name)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onDeviceNameChange(nameDraft) }) {
                        Text("حفظ")
                    }
                }
                OutlinedButton(onClick = { dirPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        settings.customDownloadTreeUri?.let { "مجلد الحفظ المحدد" }
                            ?: "${stringResource(R.string.settings_download_dir)} (Downloads/SPEEDORA)"
                    )
                }
            }
        }

        // Security & Privacy
        item {
            SettingsCard(title = "الخصوصية والأمان") {
                SettingsToggle(stringResource(R.string.settings_incoming_approval), settings.requireIncomingApproval, onRequireApprovalChange)
                SettingsToggle("قبول تلقائي للأجهزة الموثوقة فقط", settings.allowTrustedAutoAccept, onAllowTrustedAutoChange)
                SettingsToggle(stringResource(R.string.settings_haptics), settings.hapticFeedback, onHapticsChange)
                SettingsToggle(stringResource(R.string.settings_sounds), settings.soundEffects, onSoundsChange)
            }
        }

        // Storage & About
        item {
            SettingsCard(title = stringResource(R.string.settings_about)) {
                Text(
                    "SPEEDORA v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "التخزين: متاح ${FileSizeFormatter.format(storageSummary.availableBytes)} من ${FileSizeFormatter.format(storageSummary.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "تشفير الجلسات: ECDH P-256 + HKDF-SHA256 + AES-256-GCM + SHA-256 · تشغيل مستقل بالكامل بدون خوادم سحابية.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
