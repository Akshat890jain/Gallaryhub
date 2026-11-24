package com.example

import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import com.example.util.DynamicAppIconManager
import com.example.util.LauncherIconVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicAppIconUnitTest {

    @Test
    fun resolveVariant_coversAllSixAccentsInDarkAndLightModes() {
        val expectedDark = mapOf(
            AccentPreset.TERRACOTTA to LauncherIconVariant.TERRACOTTA_DARK,
            AccentPreset.CHAMPAGNE_GOLD to LauncherIconVariant.CHAMPAGNE_DARK,
            AccentPreset.BOTANICAL_SAGE to LauncherIconVariant.SAGE_DARK,
            AccentPreset.ROSE_ATELIER to LauncherIconVariant.ROSE_DARK,
            AccentPreset.VIOLET_INK to LauncherIconVariant.VIOLET_DARK,
            AccentPreset.CLASSIC_COBALT to LauncherIconVariant.COBALT_DARK
        )
        val expectedLight = mapOf(
            AccentPreset.TERRACOTTA to LauncherIconVariant.TERRACOTTA_LIGHT,
            AccentPreset.CHAMPAGNE_GOLD to LauncherIconVariant.CHAMPAGNE_LIGHT,
            AccentPreset.BOTANICAL_SAGE to LauncherIconVariant.SAGE_LIGHT,
            AccentPreset.ROSE_ATELIER to LauncherIconVariant.ROSE_LIGHT,
            AccentPreset.VIOLET_INK to LauncherIconVariant.VIOLET_LIGHT,
            AccentPreset.CLASSIC_COBALT to LauncherIconVariant.COBALT_LIGHT
        )

        for (preset in AccentPreset.entries) {
            assertEquals(
                expectedDark[preset],
                DynamicAppIconManager.resolveVariant(
                    accentHex = preset.hex,
                    themeMode = ThemeMode.DARK,
                    isSystemDark = false,
                    dynamicSyncEnabled = true
                )
            )
            assertEquals(
                expectedLight[preset],
                DynamicAppIconManager.resolveVariant(
                    accentHex = preset.hex,
                    themeMode = ThemeMode.LIGHT,
                    isSystemDark = true,
                    dynamicSyncEnabled = true
                )
            )
        }
    }

    @Test
    fun resolveVariant_systemModeFollowsDeviceDarkFlag() {
        assertEquals(
            LauncherIconVariant.VIOLET_DARK,
            DynamicAppIconManager.resolveVariant(
                accentHex = AccentPreset.VIOLET_INK.hex,
                themeMode = ThemeMode.SYSTEM,
                isSystemDark = true,
                dynamicSyncEnabled = true
            )
        )
        assertEquals(
            LauncherIconVariant.VIOLET_LIGHT,
            DynamicAppIconManager.resolveVariant(
                accentHex = AccentPreset.VIOLET_INK.hex,
                themeMode = ThemeMode.SYSTEM,
                isSystemDark = false,
                dynamicSyncEnabled = true
            )
        )
    }

    @Test
    fun resolveVariant_disabledSyncAlwaysReturnsDefaultTerracottaDark() {
        assertEquals(
            LauncherIconVariant.TERRACOTTA_DARK,
            DynamicAppIconManager.resolveVariant(
                accentHex = AccentPreset.CLASSIC_COBALT.hex,
                themeMode = ThemeMode.LIGHT,
                isSystemDark = false,
                dynamicSyncEnabled = false
            )
        )
    }

    @Test
    fun allTwelveAliasesHaveUniqueFullyQualifiedClassNames() {
        assertEquals(12, LauncherIconVariant.entries.size)
        val uniqueNames = LauncherIconVariant.entries.map { it.aliasClassName }.toSet()
        assertEquals(12, uniqueNames.size)
        assertTrue(uniqueNames.all { it.startsWith("com.example.Launcher") })
    }
}
