package fr.reborn.hud.editor;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du calcul de snap + guides dans {@link AlignmentGuides}.
 */
class AlignmentGuidesTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    @Test
    void shiftHeldDisablesSnap() {
        AlignmentGuides.SnapResult r = AlignmentGuides.compute(
            960 - 50, 540 - 50, 100, 100, HudElement.CHAT,
            e -> new HudElementBounds(0, 0, 1, 1),
            SCREEN_W, SCREEN_H, true);
        assertEquals(960 - 50, r.newX());
        assertEquals(540 - 50, r.newY());
        assertTrue(r.guides().isEmpty());
    }

    @Test
    void snapsCenterXToScreenCenter() {
        // Box 100×100, son centre X = targetX + 50.
        // Si targetX + 50 = 960 ± 4 → snap.
        // targetX = 908 → centerX = 958 (distance 2 du centre 960) → snap a 910
        AlignmentGuides.SnapResult r = AlignmentGuides.compute(
            908, 0, 100, 100, HudElement.CHAT,
            e -> new HudElementBounds(0, 0, 1, 1),
            SCREEN_W, SCREEN_H, false);
        assertEquals(910, r.newX(), "centerX snappe a 960 → targetX = 910");
        assertFalse(r.guides().isEmpty());
        assertTrue(r.guides().stream().anyMatch(g -> g.vertical() && g.pos() == 960));
    }

    @Test
    void doesNotSnapIfDistanceTooLarge() {
        // targetX = 800 → centerX = 850, distance au centre = 110 > 4
        AlignmentGuides.SnapResult r = AlignmentGuides.compute(
            800, 0, 100, 100, HudElement.CHAT,
            e -> new HudElementBounds(0, 0, 1, 1),
            SCREEN_W, SCREEN_H, false);
        // Pas de snap X (mais Y peut snapper sur edge haut si proche de 0)
        assertEquals(800, r.newX());
    }

    @Test
    void snapsOnOtherElementCenter() {
        // Scoreboard "virtuel" a centerX = 1500.
        // On drag CHAT (100x100) avec targetX = 1448 → centerX = 1498, distance 2 → snap.
        AlignmentGuides.SnapResult r = AlignmentGuides.compute(
            1448, 200, 100, 100, HudElement.CHAT,
            e -> {
                if (e == HudElement.SCOREBOARD) {
                    return new HudElementBounds(1450, 100, 100, 100);
                }
                return new HudElementBounds(-9999, -9999, 1, 1);
            },
            SCREEN_W, SCREEN_H, false);
        assertEquals(1450, r.newX(), "snap sur scoreboard centerX = 1500 → targetX = 1450");
    }

    @Test
    void snapsOnEdgeNotJustCenter() {
        // Si la box left aligne sur left ecran (= 0), snap aussi.
        AlignmentGuides.SnapResult r = AlignmentGuides.compute(
            2, 200, 100, 100, HudElement.CHAT,
            e -> new HudElementBounds(-9999, -9999, 1, 1),
            SCREEN_W, SCREEN_H, false);
        assertEquals(0, r.newX(), "left edge snappe a 0");
    }
}
