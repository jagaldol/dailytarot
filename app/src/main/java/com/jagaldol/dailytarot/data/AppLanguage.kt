package com.jagaldol.dailytarot.data

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * A context in the app language as it is set right now: the per-app choice (Android 13+) or the
 * device's. A cached process can keep the configuration it had before a language change, so the
 * widget and background draws ask the system instead of trusting their own resources.
 *
 * Releases are APKs carrying every language, so no Play language split needs downloading.
 */
@SuppressLint("AppBundleLocaleChanges")
fun Context.withAppLanguage(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val manager = getSystemService(LocaleManager::class.java) ?: return this
    // systemLocales ignores app overrides, unlike this process's possibly stale configuration.
    val locales = manager.applicationLocales.takeUnless { it.isEmpty } ?: manager.systemLocales
    if (locales == resources.configuration.locales) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(locales) })
}
