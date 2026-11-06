package dev.jamie.uikit.ui.hud

import dev.jamie.uikit.ui.Canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What makes a layout survive resolution and GUI-scale changes: the geometry, which needs no game. */
class HudElementTest {
    private class Box(anchor: Anchor = Anchor.TOP_LEFT) : HudElement("box", "Box", anchor) {
        init { lastSize = Size(40f, 20f) }
        override fun draw(c: Canvas, preview: Boolean) = Size(40f, 20f)
    }

    private val eps = 0.001f

    @Test fun placeAtKeepsTheSpotItWasDroppedOn() {
        val e = Box()
        e.scale = 2f
        e.placeAt(300f, 200f, 640, 360)
        assertEquals(300f, e.screenX(640), eps)
        assertEquals(200f, e.screenY(360), eps)
    }

    @Test fun droppingReanchorsToTheNearestSide() {
        val e = Box()
        e.placeAt(590f, 330f, 640, 360)
        assertEquals(Anchor.BOTTOM_RIGHT, e.anchor)
        e.placeAt(300f, 170f, 640, 360)
        assertEquals(Anchor.CENTER, e.anchor)
    }

    @Test fun anAnchoredElementStaysOnItsSideWhenTheScreenChanges() {
        val e = Box()
        e.placeAt(590f, 330f, 640, 360)
        val gapRight = 640f - (e.screenX(640) + 40f)
        assertEquals(gapRight, 1280f - (e.screenX(1280) + 40f), eps)
    }

    @Test fun scalingKeepsTheCornerWithReanchorOff() {
        val e = Box(Anchor.BOTTOM_RIGHT)
        val x = e.screenX(640); val y = e.screenY(360)
        e.scale = 1.5f
        e.placeAt(x, y, 640, 360, reanchor = false)
        assertEquals(Anchor.BOTTOM_RIGHT, e.anchor)
        assertEquals(x, e.screenX(640), eps)
        assertEquals(y, e.screenY(360), eps)
    }

    @Test fun resetGoesBackToTheDefaults() {
        val e = Box()
        assertTrue(e.unplaced)
        e.placeAt(300f, 200f, 640, 360)
        assertFalse(e.unplaced)
        e.scale = 3f
        e.resetPosition()
        assertTrue(e.unplaced)
        assertEquals(1f, e.scale, eps)
    }

    @Test fun elementsAreOnUnlessTheModSaysOtherwise() {
        val plain = Box()
        assertTrue(plain.shouldRender())
        val inactive = Box().apply { activeWhen = { false } }
        assertFalse(inactive.shouldRender())
        assertTrue(inactive.available)
        val off = Box().apply { availableWhen = { false } }
        assertFalse(off.shouldRender())
    }
}
