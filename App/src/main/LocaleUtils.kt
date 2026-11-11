package com.prosatfinder.ui

import android.icu.text.LocaleDisplayNames
import android.icu.util.ULocale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

private val rtlLanguages = setOf("ar", "fa", "ur", "ckb", "azb", "ps", "sd", "ug", "dv", "he")

/** Arapça, Farsça, Urduca, Sorani, Güney Azerbaycan ve Arap alfabeli Azerbaycan Türkçesi sağdan sola. */
fun isRtl(locale: Locale): Boolean =
    locale.script.equals("Arab", ignoreCase = true) || locale.language in rtlLanguages

/** "" = sistem dili. Android 13+ sistem ayarıyla, altında AppLocalesMetadataHolderService ile kalıcıdır. */
fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    )
}

fun currentLanguageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

/** Ülke (ISO) ve kıta (UN M.49) adlarını seçili dilde ICU'dan alır; çeviri dosyasına gerek kalmaz. */
fun regionName(code: String, locale: Locale): String =
    runCatching { LocaleDisplayNames.getInstance(ULocale.forLocale(locale)).regionDisplayName(code) }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: code

/** 13.0 -> "13.0°E", -7.0 -> "7.0°W". Rakamlar seçili dile göre yazılır. */
fun positionLabel(position: Double, east: String, west: String, locale: Locale): String =
    String.format(locale, "%.1f°%s", kotlin.math.abs(position), if (position >= 0) east else west)
