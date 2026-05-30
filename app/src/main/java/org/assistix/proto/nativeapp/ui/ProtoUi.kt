package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.ProtoLegal

/** Мягкий фон с оранжевым свечением — онбординг, welcome. */
@Composable
fun ProtoBrandBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(260.dp)
                .offset(x = (-72).dp, y = 32.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(ProtoOrange.copy(0.28f), Color.Transparent))),
        )
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = 96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(ProtoOrange.copy(0.16f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                ),
        )
    }
}

@Composable
fun ProtoBrandWordmark(modifier: Modifier = Modifier) {
    Text(
        "PROTO",
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, letterSpacing = 2.sp),
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun ProtoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ProtoSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base =
        Modifier
            .fillMaxWidth()
            .clip(ProtoShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), ProtoShapes.card)
    Box(
        if (onClick != null) base.clickable(onClick = onClick) else base,
    ) {
        content()
    }
}

@Composable
fun ProtoLanguagePicker(
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val languages = listOf("en" to "English", "ru" to "Русский", "it" to "Italiano")
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        languages.forEach { (code, label) ->
            val selected = selectedCode == code
            val bg =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(0.65f)
            val fg =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            TextButton(
                onClick = { onSelect(code) },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(ProtoShapes.button)
                        .background(bg)
                        .then(
                            if (selected) Modifier.border(1.dp, ProtoOrange.copy(0.5f), ProtoShapes.button)
                            else Modifier,
                        ),
            ) {
                Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

private data class ProtoLangOption(val code: String, val label: String, val flag: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoLanguageDropdown(
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localeKey by org.assistix.proto.nativeapp.ui.l10n.AppLocale.currentCode()
    val options =
        remember(localeKey) {
            listOf(
                ProtoLangOption("en", UiStrings.langNativeEn, "🇬🇧", UiStrings.langLabelEn),
                ProtoLangOption("ru", UiStrings.langNativeRu, "🇷🇺", UiStrings.langLabelRu),
                ProtoLangOption("it", UiStrings.langNativeIt, "🇮🇹", UiStrings.langLabelIt),
            )
        }
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code == selectedCode } ?: options.first()
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "langChevron")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = ProtoShapes.field,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            shadowElevation = if (expanded) 4.dp else 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.flag, fontSize = 30.sp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selected.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        UiStrings.language,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .width(menuWidth)
                    .clip(ProtoShapes.dialog)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), ProtoShapes.dialog),
        ) {
            options.forEach { opt ->
                val isSelected = opt.code == selectedCode
                val rowBg by animateColorAsState(
                    if (isSelected) ProtoOrange.copy(alpha = 0.14f) else Color.Transparent,
                    label = "langRowBg",
                )
                DropdownMenuItem(
                    text = {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(opt.flag, fontSize = 24.sp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    opt.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(
                                    opt.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ProtoOrange,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (opt.code != selectedCode) onSelect(opt.code)
                    },
                    modifier = Modifier.background(rowBg),
                )
            }
        }
    }
}

@Composable
fun ProtoChatsEmptyState(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(ProtoOrange.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = null,
                tint = ProtoOrange,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            UiStrings.chatsEmptyTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            UiStrings.chatsEmptyBody,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        ProtoPrimaryButton(
            text = UiStrings.chatsEmptyAction,
            onClick = onOpenSearch,
            modifier = Modifier.width(220.dp),
        )
    }
}

@Composable
fun ProtoChatsFilteredEmptyState(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            UiStrings.chatsFilterEmptyTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            UiStrings.chatsFilterEmptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        ProtoPrimaryButton(
            text = UiStrings.chatsFilterEmptyAction,
            onClick = onClearFilters,
            modifier = Modifier.widthIn(min = 200.dp),
        )
    }
}

@Composable
fun ProtoPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier =
            modifier
                .height(52.dp)
                .clip(ProtoShapes.button)
                .background(
                    Brush.horizontalGradient(listOf(ProtoOrange, ProtoOrange.copy(alpha = 0.88f))),
                ),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProtoPolicyConsentCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ctx = LocalContext.current
    val borderColor =
        animateColorAsState(
            if (checked) ProtoOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            label = "policyBorder",
        )
    val fillColor =
        animateColorAsState(
            if (checked) ProtoOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            label = "policyFill",
        )
    val shape = ProtoShapes.card
    val cardModifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillColor.value)
            .border(1.dp, borderColor.value, shape)
    Box(
        if (enabled) cardModifier.clickable { onCheckedChange(!checked) } else cardModifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, borderColor.value, RoundedCornerShape(8.dp))
                    .background(if (checked) ProtoOrange else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    UiStrings.policyConsent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        UiStrings.policyLinkPrivacy,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.clickable(enabled = enabled) {
                                openLegalUrl(ctx, ProtoLegal.PRIVACY_URL)
                            },
                    )
                    Text(
                        UiStrings.policyLinkRules,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.clickable(enabled = enabled) {
                                openLegalUrl(ctx, ProtoLegal.RULES_URL)
                            },
                    )
                }
            }
        }
    }
}

@Composable
fun ProtoDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    TextButton(
        onClick = onClick,
        modifier =
            modifier
                .height(52.dp)
                .clip(ProtoShapes.button)
                .background(error.copy(alpha = 0.12f))
                .border(1.dp, error.copy(alpha = 0.55f), ProtoShapes.button),
    ) {
        Text(text, color = error, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProtoGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = ProtoHaptics.rememberSender()
    TextButton(
        onClick = {
            haptic(HapticKind.Tap)
            onClick()
        },
        modifier =
            modifier
                .height(48.dp)
                .clip(ProtoShapes.button)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.35f), ProtoShapes.button),
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProtoSettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ProtoTopBarDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        thickness = 1.dp,
    )
}
