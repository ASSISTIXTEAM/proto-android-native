package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class MainTab { Settings, Chats, Pulse, Assistix, Profile }

@Composable
fun ProtoBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = ProtoHaptics.rememberSender()
    val barShape = RoundedCornerShape(36.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(12.dp, barShape, ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.12f))
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(MainTab.Settings, selected, UiStrings.settings, "PROTO/settings.png", "PROTO/settings-light.png") { tab ->
                    if (tab != selected) haptic(HapticKind.Toggle)
                    onSelect(tab)
                }
                NavItem(MainTab.Chats, selected, UiStrings.chats, "PROTO/chat.png", "PROTO/chat-light.png") { tab ->
                    if (tab != selected) haptic(HapticKind.Toggle)
                    onSelect(tab)
                }
                PulseNavItem(MainTab.Pulse, selected) { tab ->
                    if (tab != selected) haptic(HapticKind.Toggle)
                    onSelect(tab)
                }
                NavItem(MainTab.Assistix, selected, UiStrings.assistixAi, "PROTO/assistix.png", "PROTO/assistix-light.png") { tab ->
                    if (tab != selected) haptic(HapticKind.Toggle)
                    onSelect(tab)
                }
                NavItem(MainTab.Profile, selected, UiStrings.profile, "PROTO/profile.png", "PROTO/profile-light.png") { tab ->
                    if (tab != selected) haptic(HapticKind.Toggle)
                    onSelect(tab)
                }
            }
        }
    }
}

@Composable
private fun PulseNavItem(
    tab: MainTab,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val on = tab == selected
    val pillColor by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "pulsePill",
    )
    val padH by animateDpAsState(if (on) 20.dp else 12.dp, label = "pulsePad")
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(0.35f)),
                    onClick = { onSelect(tab) },
                )
                .background(pillColor)
                .padding(horizontal = padH, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = UiStrings.pulseTitle,
            modifier = Modifier.size(28.dp),
            tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: MainTab,
    label: String,
    iconDark: String,
    iconLight: String,
    onSelect: (MainTab) -> Unit,
) {
    val on = tab == selected
    val pillColor by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "pill",
    )
    val padH by animateDpAsState(
        if (on) 22.dp else 14.dp,
        animationSpec =
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessVeryLow,
            ),
        label = "pad",
    )
    val iconAsset = if (on) iconLight else iconDark
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(0.35f)),
                    onClick = { onSelect(tab) },
                )
                .background(pillColor)
                .padding(horizontal = padH, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "file:///android_asset/$iconAsset",
            contentDescription = label,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
