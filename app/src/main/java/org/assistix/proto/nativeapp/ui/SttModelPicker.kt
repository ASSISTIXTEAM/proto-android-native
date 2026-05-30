package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.ProtoSttCoordinator
import org.assistix.proto.nativeapp.data.SttDeviceCapability
import org.assistix.proto.nativeapp.data.WhisperModelTier

@Composable
fun SttModelPickerSection(
    stt: ProtoSttCoordinator,
    modelLevel: Int,
    deviceClass: SttDeviceCapability.Class,
    recommendedLevel: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val packState by stt.packState.collectAsState()
    var pendingHeavyLevel by remember { mutableStateOf<Int?>(null) }
    val tier = WhisperModelTier.fromLevel(modelLevel)
    val maxLevel = WhisperModelTier.MAX_LEVEL.toFloat()
    var sliderValue by remember(modelLevel) { mutableFloatStateOf(modelLevel.toFloat()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            UiStrings.sttModelPickBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    UiStrings.sttTierLabel(tier),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (modelLevel == recommendedLevel) {
                    Text(
                        UiStrings.sttModelRecommendedForDevice,
                        style = MaterialTheme.typography.labelSmall,
                        color = ProtoOrange,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                UiStrings.sttTierDesc(tier),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                UiStrings.sttTierSize(tier),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            Text(
                UiStrings.sttTierEta(tier),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    UiStrings.sttSliderFast,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    UiStrings.sttSliderPowerful,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val level = sliderValue.toInt().coerceIn(WhisperModelTier.MIN_LEVEL, WhisperModelTier.MAX_LEVEL)
                    sliderValue = level.toFloat()
                    if (SttDeviceCapability.isLevelHeavyForDevice(deviceClass, level)) {
                        pendingHeavyLevel = level
                    } else {
                        stt.setModelLevel(level)
                        scope.launch { runCatching { stt.ensurePackDownloaded() } }
                    }
                },
                valueRange = 0f..maxLevel,
                steps = WhisperModelTier.MAX_LEVEL - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            val installedCount = remember(packState) { stt.installedModelCount() }
            Text(
                UiStrings.sttModelsInstalled(installedCount, WhisperModelTier.LADDER.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    pendingHeavyLevel?.let { level ->
        val heavyTier = WhisperModelTier.fromLevel(level)
        AlertDialog(
            onDismissRequest = { pendingHeavyLevel = null },
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(UiStrings.sttModelPowerfulWarningTitle) },
            text = { Text(UiStrings.sttModelHeavyWarningBody(heavyTier)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHeavyLevel = null
                        stt.setModelLevel(level)
                        scope.launch { runCatching { stt.ensurePackDownloaded() } }
                    },
                ) { Text(UiStrings.sttModelPowerfulConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHeavyLevel = null }) { Text(UiStrings.cancel) }
            },
        )
    }
}
