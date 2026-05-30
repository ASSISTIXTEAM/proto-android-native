package org.assistix.proto.nativeapp.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val LocalL10n = compositionLocalOf { L10nData.en }

object AppLocale {
    private val _code = MutableStateFlow("en")
    val code: StateFlow<String> = _code.asStateFlow()

    fun setLanguage(raw: String) {
        val c =
            when (raw.lowercase()) {
                "ru" -> "ru"
                "it" -> "it"
                else -> "en"
            }
        _code.value = c
    }

    fun bundleFor(code: String): L10nBundle = L10nData.forCode(code)

    fun privateMarkPreview(): String = bundleFor(_code.value).privateMessagePreview

    @Composable
    fun currentCode(): State<String> = _code.collectAsState()

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        val code by currentCode()
        val bundle = remember(code) { bundleFor(code) }
        CompositionLocalProvider(LocalL10n provides bundle) {
            content()
        }
    }
}

/** Prefer [LocalL10n.current] in composables; [org.assistix.proto.nativeapp.ui.UiStrings] mirrors this for non-Compose code. */
@Composable
fun rememberStrings(): L10nBundle = LocalL10n.current
