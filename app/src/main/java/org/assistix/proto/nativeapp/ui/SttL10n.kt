package org.assistix.proto.nativeapp.ui

import org.assistix.proto.nativeapp.data.WhisperModelTier

fun UiStrings.sttTierLabel(tier: WhisperModelTier): String =
    when (tier) {
        WhisperModelTier.TINY_FAST -> sttTier0Title
        WhisperModelTier.TINY_PLUS -> sttTier1Title
        WhisperModelTier.BASE -> sttTier2Title
        WhisperModelTier.SMALL_Q5 -> sttTier3Title
        WhisperModelTier.SMALL -> sttTier4Title
        WhisperModelTier.MEDIUM_Q5 -> sttTier5Title
    }

fun UiStrings.sttTierDesc(tier: WhisperModelTier): String =
    when (tier) {
        WhisperModelTier.TINY_FAST -> sttTier0Desc
        WhisperModelTier.TINY_PLUS -> sttTier1Desc
        WhisperModelTier.BASE -> sttTier2Desc
        WhisperModelTier.SMALL_Q5 -> sttTier3Desc
        WhisperModelTier.SMALL -> sttTier4Desc
        WhisperModelTier.MEDIUM_Q5 -> sttTier5Desc
    }

fun UiStrings.sttTierSize(tier: WhisperModelTier): String =
    when (tier) {
        WhisperModelTier.TINY_FAST -> sttTier0Size
        WhisperModelTier.TINY_PLUS -> sttTier1Size
        WhisperModelTier.BASE -> sttTier2Size
        WhisperModelTier.SMALL_Q5 -> sttTier3Size
        WhisperModelTier.SMALL -> sttTier4Size
        WhisperModelTier.MEDIUM_Q5 -> sttTier5Size
    }

fun UiStrings.sttModelHeavyWarningBody(tier: WhisperModelTier): String =
    sttModelHeavyWarningFmt.replace("%s", sttTierLabel(tier))

fun UiStrings.sttModelsInstalled(count: Int, total: Int): String =
    sttModelsInstalledFmt.replace("%1", count.toString()).replace("%2", total.toString())

fun UiStrings.sttTierEta(tier: WhisperModelTier): String =
    when (tier) {
        WhisperModelTier.TINY_FAST -> sttTier0Eta
        WhisperModelTier.TINY_PLUS -> sttTier1Eta
        WhisperModelTier.BASE -> sttTier2Eta
        WhisperModelTier.SMALL_Q5 -> sttTier3Eta
        WhisperModelTier.SMALL -> sttTier4Eta
        WhisperModelTier.MEDIUM_Q5 -> sttTier5Eta
    }
