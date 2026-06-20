package fr.reborn.hud.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de {@link HudElementState} : mutations chainables, clamp scale,
 * fallback anchor.
 */
class HudElementStateTest {

    @Test
    void defaultIsZeroAndVisible() {
        assertEquals(0, HudElementState.DEFAULT.x());
        assertEquals(0, HudElementState.DEFAULT.y());
        assertEquals(1.0f, HudElementState.DEFAULT.scale());
        assertTrue(HudElementState.DEFAULT.visible());
        assertNull(HudElementState.DEFAULT.anchor());
    }

    @Test
    void withDelta_addsToCurrent() {
        HudElementState s = new HudElementState(10, 20, 1.0f, true, null);
        s = s.withDelta(5, -3);
        assertEquals(15, s.x());
        assertEquals(17, s.y());
    }

    @Test
    void withScale_clampsToBounds() {
        HudElementState s = HudElementState.DEFAULT;
        assertEquals(0.25f, s.withScale(0.1f).scale());
        assertEquals(4.0f, s.withScale(10f).scale());
        assertEquals(1.5f, s.withScale(1.5f).scale());
    }

    @Test
    void withVisible_togglesIndependently() {
        HudElementState s = new HudElementState(5, 5, 2.0f, true, HudAnchor.CENTER);
        HudElementState hidden = s.withVisible(false);
        assertFalse(hidden.visible());
        // Autres champs preserves
        assertEquals(5, hidden.x());
        assertEquals(2.0f, hidden.scale());
        assertEquals(HudAnchor.CENTER, hidden.anchor());
    }

    @Test
    void effectiveAnchor_fallsBackToElementDefault() {
        HudElementState s = HudElementState.DEFAULT; // anchor null
        assertEquals(HudAnchor.BOTTOM_LEFT, s.effectiveAnchor(HudElement.CHAT));
        assertEquals(HudAnchor.CENTER_RIGHT, s.effectiveAnchor(HudElement.SCOREBOARD));
        assertEquals(HudAnchor.TOP_CENTER, s.effectiveAnchor(HudElement.BOSS_BAR));
    }

    @Test
    void effectiveAnchor_explicitOverridesDefault() {
        HudElementState s = HudElementState.DEFAULT.withAnchor(HudAnchor.TOP_RIGHT);
        assertEquals(HudAnchor.TOP_RIGHT, s.effectiveAnchor(HudElement.CHAT));
    }
}
