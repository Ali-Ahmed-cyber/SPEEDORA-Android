package com.speedora.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import com.speedora.app.R
import com.speedora.app.core.designsystem.SpeedoraColors

@Composable
fun MigrationScreen(
    onStartMigration: (
        photos: Boolean,
        videos: Boolean,
        audio: Boolean,
        docs: Boolean,
        downloads: Boolean,
        contacts: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(true) }
    var videos by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var docs by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf(false) }

    var hasContactsPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPerm = granted
        contacts = granted
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.dest_migration),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SpeedoraColors.SoftBlue,
                border = BorderStroke(1.dp, SpeedoraColors.BorderSubtle)
            ) {
                Text(
                    text = stringResource(R.string.migration_restriction_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeedoraColors.MidnightNavy,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        item { MigrationToggleRow(stringResource(R.string.cat_photos), photos) { photos = it } }
        item { MigrationToggleRow(stringResource(R.string.cat_videos), videos) { videos = it } }
        item { MigrationToggleRow(stringResource(R.string.cat_music), audio) { audio = it } }
        item { MigrationToggleRow(stringResource(R.string.cat_documents), docs) { docs = it } }
        item { MigrationToggleRow(stringResource(R.string.cat_downloads), downloads) { downloads = it } }
        item {
            MigrationToggleRow(
                label = "جهات الاتصال (vCard بإذن صريح)",
                checked = contacts
            ) { wantsContacts ->
                if (wantsContacts && !hasContactsPerm) {
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                } else {
                    contacts = wantsContacts
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onStartMigration(photos, videos, audio, docs, downloads, contacts) },
                enabled = photos || videos || audio || docs || downloads || contacts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpeedoraColors.RoyalBlue)
            ) {
                Text("تجهيز الحزمة واختيار الجهاز المستلم")
            }
        }
    }
}

@Composable
private fun MigrationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (checked) SpeedoraColors.RoyalBlue else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
