package com.example.opsc

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * Checks the WCAG 2.2 contrast ratio of every text/background colour pair in the design
 * system, in light and dark mode, by reading the real colour files. Text needs 4.5:1,
 * borders and icons need 3:1.
 */
class DesignContrastTest {

    private fun load(path: String): Map<String, String> {
        val xml = File(path).readText()
        return Regex("""<color name="([a-z_]+)">(#[0-9A-Fa-f]{6,8})</color>""")
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun luminance(hex: String): Double {
        val rgb = hex.removePrefix("#").takeLast(6)
        val channels = (0..2).map { rgb.substring(it * 2, it * 2 + 2).toInt(16) / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** (foreground token, background token, minimum ratio) */
    private val pairs = listOf(
        Triple("md_on_primary", "md_primary", 4.5),
        Triple("md_on_primary_container", "md_primary_container", 4.5),
        Triple("md_on_secondary", "md_secondary", 4.5),
        Triple("md_on_secondary_container", "md_secondary_container", 4.5),
        Triple("md_on_background", "md_background", 4.5),
        Triple("md_on_surface", "md_surface", 4.5),
        Triple("md_on_surface", "md_surface_container_low", 4.5),
        Triple("md_on_surface", "md_surface_container_lowest", 4.5),
        Triple("md_on_surface_variant", "md_surface_variant", 4.5),
        Triple("md_on_surface_variant", "md_background", 4.5),
        Triple("md_on_error", "md_error", 4.5),
        Triple("md_on_error_container", "md_error_container", 4.5),
        Triple("bf_on_success", "bf_success", 4.5),
        Triple("bf_on_success_container", "bf_success_container", 4.5),
        Triple("md_primary", "md_background", 4.5),
        Triple("md_error", "md_background", 4.5),
        Triple("bf_success", "md_background", 4.5),
        Triple("bf_on_brand", "bf_brand", 4.5),
        Triple("md_outline", "md_background", 3.0)
    )

    private fun check(mode: String, colours: Map<String, String>) {
        val failures = pairs.mapNotNull { (fg, bg, min) ->
            val f = colours[fg] ?: return@mapNotNull "$mode: missing $fg"
            val b = colours[bg] ?: return@mapNotNull "$mode: missing $bg"
            val ratio = contrast(f, b)
            if (ratio < min) "$mode: $fg on $bg is %.2f (needs $min)".format(ratio) else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun lightModeMeetsContrast() = check("light", load("src/main/res/values/colors.xml"))

    @Test fun darkModeMeetsContrast() {
        // Dark mode overrides the light tokens; anything it does not define falls back to light.
        val merged = load("src/main/res/values/colors.xml") + load("src/main/res/values-night/colors.xml")
        check("dark", merged)
    }
}
