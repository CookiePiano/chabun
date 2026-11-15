package com.syncdesk.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pleos Connect asks for a contrast ratio of at least 4.5:1 for text and 3:1 for large text
 * (24 dp bold and up) and graphics. The pairs are those of the design review (`review.md`
 * section 4, computed from `chabun.css` lines 37-38, glass composited on the ground) plus the
 * ones the implementation adds, for day and night.
 */
class PaletteContrastTest {

    private val themes = mapOf("night" to Palette.night, "day" to Palette.day)

    private fun assertContrast(theme: String, pair: String, foreground: Long, background: Long, minimum: Double) {
        val ratio = Palette.contrast(foreground, background)
        assertTrue(
            "$theme: $pair is %.2f:1, below %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    /** The glass card as the eye sees it: its fill over the ground. */
    private fun PaletteTokens.glassOnGround() = Palette.over(glass, ground)

    @Test
    fun `body and secondary text are readable on the ground and on glass`() {
        themes.forEach { (name, t) ->
            assertContrast(name, "ink on ground", t.ink, t.ground, TEXT)
            assertContrast(name, "muted on ground", t.muted, t.ground, TEXT)
            assertContrast(name, "ink on glass", t.ink, t.glassOnGround(), TEXT)
            assertContrast(name, "muted on glass", t.muted, t.glassOnGround(), TEXT)
        }
    }

    @Test
    fun `text is readable on the sheet and on the selected row`() {
        themes.forEach { (name, t) ->
            assertContrast(name, "ink on glass-strong", t.ink, t.glassStrong, TEXT)
            assertContrast(name, "muted on glass-strong", t.muted, t.glassStrong, TEXT)
            val chip = Palette.over(t.chipOn, t.glassOnGround())
            assertContrast(name, "ink on chip-on", t.ink, chip, TEXT)
            assertContrast(name, "muted on chip-on", t.muted, chip, TEXT)
        }
    }

    @Test
    fun `earlier turns in muted stay readable at full opacity (review item 2)`() {
        // The earlier turns were 50 % opacity in the web mock-up (2.16:1 by day); now muted.
        themes.forEach { (name, t) ->
            assertContrast(name, "earlier turn on ground", t.muted, t.ground, TEXT)
        }
    }

    @Test
    fun `labels on buttons are readable`() {
        themes.forEach { (name, t) ->
            assertContrast(name, "on-accent on accent", t.onAccent, t.accent, TEXT)
            assertContrast(name, "on-solid on solid", t.onSolid, t.solid, TEXT)
            assertContrast(name, "ink on glass button", t.ink, t.glassOnGround(), TEXT)
        }
    }

    @Test
    fun `the accent is used only for large bold text and graphics`() {
        // By day the accent is 4.28:1 on the ground: enough for the 24 dp bold and larger text it
        // is used for ("내 약속", the timeline labels), not for body text (review section 3).
        themes.forEach { (name, t) ->
            assertContrast(name, "accent on ground", t.accent, t.ground, LARGE)
            assertContrast(name, "accent on glass", t.accent, t.glassOnGround(), LARGE)
            assertContrast(name, "accent on glass-strong", t.accent, t.glassStrong, LARGE)
        }
        assertEquals(4.28, Palette.contrast(Palette.day.accent, Palette.day.ground), 0.01)
    }

    @Test
    fun `what was missed is readable and its marks stand out`() {
        themes.forEach { (name, t) ->
            val tintOnGlass = Palette.over(t.missTint, t.glassOnGround())
            val tintOnGround = Palette.over(t.missTint, t.ground)
            assertContrast(name, "miss-ink on glass", t.missInk, t.glassOnGround(), TEXT)
            assertContrast(name, "miss-ink on miss-tint over glass", t.missInk, tintOnGlass, TEXT)
            // The reason under the call wave and the tag in Gleo's turn: 24 dp bold.
            assertContrast(name, "miss-ink on miss-tint over ground", t.missInk, tintOnGround, LARGE)
            assertContrast(name, "miss-ink on ground", t.missInk, t.ground, LARGE)
            assertContrast(name, "miss-bar on ground", t.missBar, t.ground, GRAPHIC)
        }
    }

    @Test
    fun `Gleo's voice bars stand out from the ground`() {
        themes.forEach { (name, t) ->
            assertContrast(name, "gleo on ground", t.gleo, t.ground, GRAPHIC)
        }
    }

    @Test
    fun `the always-dark car stage is readable`() {
        val stage = Palette.stage
        listOf(stage.center, stage.middle, stage.edge, stage.consentMiddle, stage.consentEdge).forEach { background ->
            assertContrast("stage", "ink on stage", stage.ink, background, TEXT)
            assertContrast("stage", "muted on stage", stage.muted, background, TEXT)
            assertContrast("stage", "accent on stage", stage.accent, background, TEXT)
        }
    }

    @Test
    fun `compositing a translucent colour gives what the eye sees`() {
        assertEquals(0xFFFFFFFF, Palette.over(0xFFFFFFFF, 0xFF000000))
        assertEquals(0xFF000000, Palette.over(0x00FFFFFF, 0xFF000000))
        assertEquals(0xFF808080, Palette.over(0x80FFFFFF, 0xFF000000))
    }

    private companion object {
        const val TEXT = 4.5
        const val LARGE = 3.0
        const val GRAPHIC = 3.0
    }
}
