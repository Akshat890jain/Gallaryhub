package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode

/**
 * Represents each of GalleryHub's 12 dynamically switchable launcher icon variants
 * (6 Curated Studio Accent Presets × Obsidian Dark / Warm Paper Light surfaces).
 *
 * Each entry corresponds 1:1 with an `<activity-alias>` in `AndroidManifest.xml`
 * targeting `.MainActivity` and carrying an adaptive icon with an Android 13+ Material You
 * monochrome silhouette layer.
 */
enum class LauncherIconVariant(
    val aliasClassName: String,
    val preset: AccentPreset,
    val isDark: Boolean,
    val displayLabel: String
) {
    TERRACOTTA_DARK(
        aliasClassName = "com.example.LauncherTerracottaDark",
        preset = AccentPreset.TERRACOTTA,
        isDark = true,
        displayLabel = "Terracotta · Dark"
    ),
    TERRACOTTA_LIGHT(
        aliasClassName = "com.example.LauncherTerracottaLight",
        preset = AccentPreset.TERRACOTTA,
        isDark = false,
        displayLabel = "Terracotta · Light"
    ),
    CHAMPAGNE_DARK(
        aliasClassName = "com.example.LauncherChampagneDark",
        preset = AccentPreset.CHAMPAGNE_GOLD,
        isDark = true,
        displayLabel = "Champagne · Dark"
    ),
    CHAMPAGNE_LIGHT(
        aliasClassName = "com.example.LauncherChampagneLight",
        preset = AccentPreset.CHAMPAGNE_GOLD,
        isDark = false,
        displayLabel = "Champagne · Light"
    ),
    SAGE_DARK(
        aliasClassName = "com.example.LauncherSageDark",
        preset = AccentPreset.BOTANICAL_SAGE,
        isDark = true,
        displayLabel = "Sage · Dark"
    ),
    SAGE_LIGHT(
        aliasClassName = "com.example.LauncherSageLight",
        preset = AccentPreset.BOTANICAL_SAGE,
        isDark = false,
        displayLabel = "Sage · Light"
    ),
    ROSE_DARK(
        aliasClassName = "com.example.LauncherRoseDark",
        preset = AccentPreset.ROSE_ATELIER,
        isDark = true,
        displayLabel = "Rose · Dark"
    ),
    ROSE_LIGHT(
        aliasClassName = "com.example.LauncherRoseLight",
        preset = AccentPreset.ROSE_ATELIER,
        isDark = false,
        displayLabel = "Rose · Light"
    ),
    VIOLET_DARK(
        aliasClassName = "com.example.LauncherVioletDark",
        preset = AccentPreset.VIOLET_INK,
        isDark = true,
        displayLabel = "Violet · Dark"
    ),
    VIOLET_LIGHT(
        aliasClassName = "com.example.LauncherVioletLight",
        preset = AccentPreset.VIOLET_INK,
        isDark = false,
        displayLabel = "Violet · Light"
    ),
    COBALT_DARK(
        aliasClassName = "com.example.LauncherCobaltDark",
        preset = AccentPreset.CLASSIC_COBALT,
        isDark = true,
        displayLabel = "Cobalt · Dark"
    ),
    COBALT_LIGHT(
        aliasClassName = "com.example.LauncherCobaltLight",
        preset = AccentPreset.CLASSIC_COBALT,
        isDark = false,
        displayLabel = "Cobalt · Light"
    );

    companion object {
        val DEFAULT: LauncherIconVariant = TERRACOTTA_DARK
    }
}

/**
 * Coordinates safe, crash-free switching of GalleryHub's app drawer / home screen launcher icon
 * using `<activity-alias>` components.
 *
 * Key safety guarantees:
 * 1. `MainActivity` is NEVER disabled, preserving Android App Links (`ACTION_VIEW`) and deep links.
 * 2. The target alias is ALWAYS enabled BEFORE disabling old aliases so the app never disappears
 *    from the launcher even if the process is interrupted mid-switch.
 * 3. Invoked from `MainActivity.onStop()` (when `!isChangingConfigurations`) using
 *    `PackageManager.DONT_KILL_APP` so changing themes inside the app never interrupts or restarts
 *    the user's active session.
 */
object DynamicAppIconManager {

    fun isSystemDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Deterministically resolves which launcher icon variant should be active given the user's
     * accent hex, theme mode, system dark flag, and whether dynamic app icon syncing is enabled.
     */
    fun resolveVariant(
        accentHex: String?,
        themeMode: ThemeMode,
        isSystemDark: Boolean,
        dynamicSyncEnabled: Boolean = true
    ): LauncherIconVariant {
        if (!dynamicSyncEnabled) return LauncherIconVariant.DEFAULT
        val resolvedDark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemDark
        }
        val preset = AccentPreset.presetFromHex(accentHex)
        return LauncherIconVariant.entries.firstOrNull {
            it.preset == preset && it.isDark == resolvedDark
        } ?: LauncherIconVariant.DEFAULT
    }

    /**
     * Reads `PackageManager` to identify which launcher alias is currently enabled.
     */
    fun getCurrentlyEnabledVariant(context: Context): LauncherIconVariant {
        val pm = context.packageManager
        val pkg = context.packageName
        for (variant in LauncherIconVariant.entries) {
            val state = try {
                pm.getComponentEnabledSetting(ComponentName(pkg, variant.aliasClassName))
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return variant
            }
        }
        // If none explicitly enabled, the manifest default (TERRACOTTA_DARK) is enabled unless explicitly disabled.
        val defaultState = try {
            pm.getComponentEnabledSetting(ComponentName(pkg, LauncherIconVariant.DEFAULT.aliasClassName))
        } catch (_: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        return if (defaultState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            LauncherIconVariant.DEFAULT
        } else {
            LauncherIconVariant.DEFAULT
        }
    }

    /**
     * Immediately switches the active launcher `<activity-alias>` to [target] if it is not already active.
     * Synchronized so rapid consecutive taps across theme swatches never interleave component states.
     * Always enables the target alias FIRST before disabling others, using `PackageManager.DONT_KILL_APP`.
     * Returns `true` if a switch was actually performed, or `false` if already synced.
     */
    @Synchronized
    fun applyVariantIfChanged(context: Context, target: LauncherIconVariant): Boolean {
        val pm = context.packageManager
        val pkg = context.packageName

        val current = getCurrentlyEnabledVariant(context)
        val targetComponent = ComponentName(pkg, target.aliasClassName)
        val targetCurrentState = try {
            pm.getComponentEnabledSetting(targetComponent)
        } catch (_: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        val targetAlreadyExplicitlyActive =
            current == target && (
                targetCurrentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (target == LauncherIconVariant.DEFAULT && targetCurrentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                )

        if (targetAlreadyExplicitlyActive) {
            return false
        }

        return try {
            // Step 1: Enable the target alias FIRST so there is never zero launcher entries.
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Step 2: Disable all other aliases.
            for (variant in LauncherIconVariant.entries) {
                if (variant == target) continue
                val comp = ComponentName(pkg, variant.aliasClassName)
                val state = try {
                    pm.getComponentEnabledSetting(comp)
                } catch (_: Exception) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                }
                if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
