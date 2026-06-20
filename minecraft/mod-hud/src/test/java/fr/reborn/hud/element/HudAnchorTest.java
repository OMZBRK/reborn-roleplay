package fr.reborn.hud.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de la grille 3×3 des anchors : conversion index ↔ enum.
 */
class HudAnchorTest {

    @Test
    void gridIndex_isOrdinal() {
        assertEquals(0, HudAnchor.TOP_LEFT.gridIndex());
        assertEquals(4, HudAnchor.CENTER.gridIndex());
        assertEquals(8, HudAnchor.BOTTOM_RIGHT.gridIndex());
    }

    @Test
    void fromGridIndex_roundTrip() {
        for (HudAnchor a : HudAnchor.values()) {
            assertEquals(a, HudAnchor.fromGridIndex(a.gridIndex()));
        }
    }

    @Test
    void fromGridIndex_outOfBoundsFallsBackToCenter() {
        assertEquals(HudAnchor.CENTER, HudAnchor.fromGridIndex(-1));
        assertEquals(HudAnchor.CENTER, HudAnchor.fromGridIndex(99));
    }

    @Test
    void coefficientsMatchGridPositions() {
        assertEquals(0.0f, HudAnchor.TOP_LEFT.fx);
        assertEquals(0.0f, HudAnchor.TOP_LEFT.fy);
        assertEquals(0.5f, HudAnchor.CENTER.fx);
        assertEquals(0.5f, HudAnchor.CENTER.fy);
        assertEquals(1.0f, HudAnchor.BOTTOM_RIGHT.fx);
        assertEquals(1.0f, HudAnchor.BOTTOM_RIGHT.fy);
    }
}
