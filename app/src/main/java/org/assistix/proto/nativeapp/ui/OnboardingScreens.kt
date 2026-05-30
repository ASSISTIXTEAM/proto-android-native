package org.assistix.proto.nativeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import org.assistix.proto.nativeapp.data.ProtoSttCoordinator
import org.assistix.proto.nativeapp.data.ProtoThemeMode
import org.assistix.proto.nativeapp.data.ProtoThemeStore
import org.assistix.proto.nativeapp.ui.l10n.AppLocale
import org.assistix.proto.nativeapp.ui.l10n.L10nBundle
import org.assistix.proto.nativeapp.ui.l10n.LocalL10n
import kotlin.math.abs

private enum class OnboardPermissionKind {
    Notifications,
    Microphone,
    Camera,
    Media,
}

private sealed interface OnboardSlide {
    data object Welcome : OnboardSlide

    data class Feature(
        val icon: ImageVector,
        val title: String,
        val body: String,
    ) : OnboardSlide

    data class Permission(val kind: OnboardPermissionKind) : OnboardSlide

    data object Theme : OnboardSlide

    /** Mandatory PROTO Cells — mutual encrypted hosting. */
    data object Cells : OnboardSlide

    data object Policy : OnboardSlide
}

private fun buildSlides(l: L10nBundle): List<OnboardSlide> =
    listOf(
        OnboardSlide.Welcome,
        OnboardSlide.Feature(Icons.Default.Shield, l.onboardWhatTitle, l.onboardWhatBody),
        OnboardSlide.Feature(Icons.Default.Lock, l.onboardSecurityTitle, l.onboardSecurityBody),
        OnboardSlide.Feature(Icons.AutoMirrored.Filled.Chat, l.onboardChatsTitle, l.onboardChatsBody),
        OnboardSlide.Feature(Icons.Default.AutoAwesome, l.onboardingAiTitle, l.onboardingAiBody),
        OnboardSlide.Feature(Icons.AutoMirrored.Filled.Chat, l.onboardCleanTitle, l.onboardCleanBody),
        OnboardSlide.Feature(Icons.Default.Phone, l.onboardCallsTitle, l.onboardCallsBody),
        OnboardSlide.Feature(Icons.Default.Mic, l.onboardSttTitle, l.onboardSttBody),
        OnboardSlide.Permission(OnboardPermissionKind.Notifications),
        OnboardSlide.Permission(OnboardPermissionKind.Microphone),
        OnboardSlide.Permission(OnboardPermissionKind.Camera),
        OnboardSlide.Permission(OnboardPermissionKind.Media),
        OnboardSlide.Theme,
        OnboardSlide.Cells,
        OnboardSlide.Policy,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingFlow(
    prefs: ProtoAppPreferences,
    themeStore: ProtoThemeStore,
    stt: ProtoSttCoordinator,
    appScope: CoroutineScope,
    onFinished: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val l = LocalL10n.current
    var pendingLang by remember { mutableStateOf("en") }
    val slides = remember(pendingLang) { buildSlides(AppLocale.bundleFor(pendingLang)) }
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val themeMode by themeStore.mode.collectAsState(initial = ProtoThemeMode.DARK)
    var permTick by remember { mutableIntStateOf(0) }
    var policyAccepted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pendingLang = runCatching { prefs.languageCodeFlow.first() }.getOrDefault("en")
    }

    fun commitLanguage() {
        scope.launch {
            prefs.setLanguageCode(pendingLang)
            AppLocale.setLanguage(pendingLang)
            UiStrings.setLanguage(pendingLang)
        }
    }

    fun finishOnboarding() {
        if (!policyAccepted) return
        commitLanguage()
        scope.launch {
            prefs.setPolicyAccepted(org.assistix.proto.nativeapp.ProtoLegal.POLICY_VERSION)
        }
        stt.startBackgroundPackDownload(appScope, delayMs = 2_000L)
        onFinished()
    }

    fun isPermissionGranted(kind: OnboardPermissionKind): Boolean {
        permTick
        return when (kind) {
            OnboardPermissionKind.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            OnboardPermissionKind.Microphone ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            OnboardPermissionKind.Camera ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            OnboardPermissionKind.Media ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                        PackageManager.PERMISSION_GRANTED
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
        }
    }

    val singlePermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permTick++
        }
    val multiPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permTick++
        }

    fun requestPermission(kind: OnboardPermissionKind) {
        when (kind) {
            OnboardPermissionKind.Notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            OnboardPermissionKind.Microphone ->
                singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            OnboardPermissionKind.Camera ->
                singlePermLauncher.launch(Manifest.permission.CAMERA)
            OnboardPermissionKind.Media -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    multiPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                        ),
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    singlePermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    val currentSlide = slides[pagerState.currentPage]
    val isLast = pagerState.currentPage == slides.lastIndex
    val onPermissionSlide = currentSlide is OnboardSlide.Permission
    val permKind = (currentSlide as? OnboardSlide.Permission)?.kind
    val permGranted = permKind?.let { isPermissionGranted(it) } == true

    val onPolicySlide = currentSlide is OnboardSlide.Policy
    val primaryLabel =
        when {
            isLast -> UiStrings.getStarted
            onPermissionSlide && !permGranted -> UiStrings.onboardPermAllow
            else -> UiStrings.next
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ProtoBrandBackdrop()

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
                TextButton(onClick = {
                    commitLanguage()
                    onSkip()
                }) {
                    Text(UiStrings.skip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
            ) { index ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .pagerSlideTransform(index, pagerState),
                ) {
                    when (val slide = slides[index]) {
                        OnboardSlide.Welcome ->
                            OnboardWelcomePage(
                                pendingLang = pendingLang,
                                onSelectLanguage = { pendingLang = it },
                            )
                        is OnboardSlide.Feature ->
                            OnboardFeaturePage(
                                icon = slide.icon,
                                title = slide.title,
                                body = slide.body,
                            )
                        is OnboardSlide.Permission -> {
                            val bundle = AppLocale.bundleFor(pendingLang)
                            val (icon, title, body) =
                                when (slide.kind) {
                                    OnboardPermissionKind.Notifications ->
                                        Triple(
                                            Icons.Default.Notifications,
                                            bundle.onboardPermNotifTitle,
                                            bundle.onboardPermNotifBody,
                                        )
                                    OnboardPermissionKind.Microphone ->
                                        Triple(
                                            Icons.Default.Mic,
                                            bundle.onboardPermMicTitle,
                                            bundle.onboardPermMicBody,
                                        )
                                    OnboardPermissionKind.Camera ->
                                        Triple(
                                            Icons.Default.CameraAlt,
                                            bundle.onboardPermCameraTitle,
                                            bundle.onboardPermCameraBody,
                                        )
                                    OnboardPermissionKind.Media ->
                                        Triple(
                                            Icons.Default.PermMedia,
                                            bundle.onboardPermMediaTitle,
                                            bundle.onboardPermMediaBody,
                                        )
                                }
                            OnboardPermissionPage(
                                icon = icon,
                                title = title,
                                body = body,
                                granted = isPermissionGranted(slide.kind),
                                onAllow = { requestPermission(slide.kind) },
                            )
                        }
                        OnboardSlide.Theme ->
                            OnboardThemePage(
                                pendingLang = pendingLang,
                                themeMode = themeMode,
                                onTheme = { mode -> scope.launch { themeStore.setMode(mode) } },
                            )
                        OnboardSlide.Cells -> OnboardCellsPage()
                        OnboardSlide.Policy -> {
                            val bundle = AppLocale.bundleFor(pendingLang)
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 28.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    bundle.onboardPolicyTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    bundle.onboardPolicyBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(20.dp))
                                ProtoPolicyConsentCard(
                                    checked = policyAccepted,
                                    onCheckedChange = { policyAccepted = it },
                                )
                            }
                        }
                    }
                }
            }

            OnboardPageIndicator(
                count = slides.size,
                current = pagerState.currentPage,
            )

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                ProtoPrimaryButton(
                    text = primaryLabel,
                    onClick = {
                        if (pagerState.currentPage == 0) commitLanguage()
                        if (onPermissionSlide && !permGranted && permKind != null) {
                            requestPermission(permKind)
                            return@ProtoPrimaryButton
                        }
                        if (isLast) {
                            if (onPolicySlide && !policyAccepted) return@ProtoPrimaryButton
                            finishOnboarding()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onPermissionSlide) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (isLast) {
                                finishOnboarding()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(UiStrings.onboardPermLater, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pagerSlideTransform(page: Int, pagerState: PagerState): Modifier {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val dist = abs(offset).coerceIn(0f, 1f)
    val scale = 1f - dist * 0.07f
    val alpha = 1f - dist * 0.38f
    return graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationX = offset * 28.dp.toPx()
    }
}

@Composable
private fun OnboardWelcomePage(
    pendingLang: String,
    onSelectLanguage: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        ProtoLogo(height = 80.dp)
        Spacer(Modifier.height(10.dp))
        ProtoBrandWordmark()
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = pendingLang,
            transitionSpec = {
                (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 6 })
            },
            label = "welcomeLang",
        ) { lang ->
            val l = AppLocale.bundleFor(lang)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    l.onboardWelcomeTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    l.onboardWelcomeSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    l.onboardPickLanguage,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ProtoLanguagePicker(pendingLang, onSelectLanguage)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardFeaturePage(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(ProtoOrange.copy(0.9f), ProtoOrange.copy(0.55f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun OnboardPermissionPage(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    onAllow: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    if (granted) ProtoOrange.copy(0.22f) else ProtoOrange.copy(0.12f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (granted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = ProtoOrange,
                modifier = Modifier.size(if (granted) 48.dp else 52.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        if (granted) {
            Spacer(Modifier.height(16.dp))
            Text(
                UiStrings.onboardPermGranted,
                style = MaterialTheme.typography.labelLarge,
                color = ProtoOrange,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Spacer(Modifier.height(24.dp))
            ProtoGhostButton(UiStrings.onboardPermAllow, onAllow, Modifier.fillMaxWidth(0.85f))
        }
    }
}

@Composable
private fun OnboardThemePage(
    pendingLang: String,
    themeMode: ProtoThemeMode,
    onTheme: (ProtoThemeMode) -> Unit,
) {
    val l = remember(pendingLang) { AppLocale.bundleFor(pendingLang) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ProtoOrange.copy(0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Tune, null, tint = ProtoOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            l.onboardSetupTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            l.onboardSetupSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeChip(UiStrings.darkTheme, themeMode == ProtoThemeMode.DARK, Modifier.weight(1f)) {
                onTheme(ProtoThemeMode.DARK)
            }
            ThemeChip(l.onboardThemeLight, themeMode == ProtoThemeMode.LIGHT, Modifier.weight(1f)) {
                onTheme(ProtoThemeMode.LIGHT)
            }
            ThemeChip(l.onboardThemeSystem, themeMode == ProtoThemeMode.SYSTEM, Modifier.weight(1f)) {
                onTheme(ProtoThemeMode.SYSTEM)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            l.onboardOtherInSettings,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, textAlign = TextAlign.Center) },
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = ProtoOrange.copy(0.22f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Composable
private fun OnboardPageIndicator(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = current == i
            val w by animateDpAsState(if (active) 22.dp else 7.dp, animationSpec = tween(280), label = "dotW")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(
                        if (active) ProtoOrange else MaterialTheme.colorScheme.outline.copy(0.35f),
                    ),
            )
        }
    }
}

@Composable
fun WelcomeAuthScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onShowOnboarding: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        ProtoBrandBackdrop()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProtoLogo(height = 72.dp)
            Spacer(Modifier.height(12.dp))
            ProtoBrandWordmark()
            Spacer(Modifier.height(16.dp))
            Text(
                UiStrings.becomePartOfProto,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                UiStrings.welcomeTagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            ProtoPrimaryButton(UiStrings.signIn, onLogin, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ProtoGhostButton(UiStrings.createAccount, onRegister, Modifier.fillMaxWidth())
            if (onShowOnboarding != null) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onShowOnboarding) {
                    Text(
                        UiStrings.showOnboardingAgain,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
