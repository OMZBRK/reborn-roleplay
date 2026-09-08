package fr.reborn.hud.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link HudElementBounds#currentFor} avec différents anchors.
 * Vérifient que le point d'ancrage reste fixe sous le scale.
 */
class HudElementBoundsTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    @Test
    void defaultStateMatchesVanilla() {
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.CHAT, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(
            HudElement.CHAT, HudElementState.DEFAULT, SCREEN_W, SCREEN_H);
        assertEquals(v.x(), c.x());
        assertEquals(v.y(), c.y());
        assertEquals(v.width(),  c.width());
        assertEquals(v.height(), c.height());
    }

    @Test
    void anchorTopLeftKeepsTLOnScale() {
        HudElementState s = new HudElementState(0, 0, 2.0f, true, HudAnchor.TOP_LEFT);
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.CHAT, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(HudElement.CHAT, s, SCREEN_W, SCREEN_H);
        // TL fixe à vanilla TL ; box agrandie vers BR
        assertEquals(v.x(), c.x());
        assertEquals(v.y(), c.y());
        assertEquals(v.width()  * 2, c.width());
        assertEquals(v.height() * 2, c.height());
    }

    @Test
    void anchorBottomRightKeepsBROnScale() {
        HudElementState s = new HudElementState(0, 0, 2.0f, true, HudAnchor.BOTTOM_RIGHT);
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.CHAT, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(HudElement.CHAT, s, SCREEN_W, SCREEN_H);
        // BR fixe à vanilla BR ; agrandissement vers TL
        assertEquals(v.right(),  c.right());
        assertEquals(v.bottom(), c.bottom());
        assertEquals(v.width()  * 2, c.width());
        assertEquals(v.height() * 2, c.height());
    }

    @Test
    void anchorCenterKeepsCenterOnScale() {
        HudElementState s = new HudElementState(0, 0, 2.0f, true, HudAnchor.CENTER);
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.CHAT, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(HudElement.CHAT, s, SCREEN_W, SCREEN_H);
        // Centre fixe — vanilla cx == current cx (1px tolerance pour rounding)
        assertTrue(Math.abs(v.centerX() - c.centerX()) <= 1, "centre X stable");
        assertTrue(Math.abs(v.centerY() - c.centerY()) <= 1, "centre Y stable");
    }

    @Test
    void offsetAppliedAtAnchorPoint() {
        HudElementState s = new HudElementState(7, -3, 1.0f, true, HudAnchor.TOP_LEFT);
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.CHAT, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(HudElement.CHAT, s, SCREEN_W, SCREEN_H);
        assertEquals(v.x() + 7, c.x());
        assertEquals(v.y() - 3, c.y());
    }

    @Test
    void hotbarDefaultAnchorIsBottomCenter() {
        // HOTBAR a defaultAnchor BOTTOM_CENTER. Scale 1.5x avec offset 0
        // → center X reste = screen / 2, bottom Y reste = vanilla bottom.
        HudElementState s = new HudElementState(0, 0, 1.5f, true, null);
        HudElementBounds v = HudElementBounds.vanillaFor(HudElement.HOTBAR, SCREEN_W, SCREEN_H);
        HudElementBounds c = HudElementBounds.currentFor(HudElement.HOTBAR, s, SCREEN_W, SCREEN_H);
        // BOTTOM_CENTER : fx=0.5 fy=1
        // anchor X = v.x + 0.5 * v.w = v.centerX
        // anchor Y = v.y + 1.0 * v.h = v.bottom
        assertTrue(Math.abs(v.centerX() - c.centerX()) <= 1);
        assertEquals(v.bottom(),  c.bottom());
    }

    /**
     * Round-trip drag : poser la box à une position visée doit la ramener
     * EXACTEMENT là, quel que soit l'anchor et l'échelle. C'est la garantie que
     * l'élément suit le curseur dans l'éditeur (la soustraction naïve de
     * vanilla.x() dérivait dès que scale != 1).
     */
    @Test
    void offsetForTopLeftRoundTripsAtAnyScale() {
        for (HudElement e : HudElement.EDITABLE) {
            for (float scale : new float[]{0.45f, 0.6f, 1.0f, 1.75f, 3.0f}) {
                HudElementState st = new HudElementState(0, 0, scale, true, null);
                int targetX = 640, targetY = 360;
                int[] off = HudElementBounds.offsetForTopLeft(e, st, SCREEN_W, SCREEN_H, targetX, targetY);
                HudElementBounds b = HudElementBounds.currentFor(
                    e, st.withPos(off[0], off[1]), SCREEN_W, SCREEN_H);
                assertEquals(targetX, b.x(), e + " @x" + scale + " : dérive horizontale");
                assertEquals(targetY, b.y(), e + " @x" + scale + " : dérive verticale");
            }
        }
    }

    /** Changer d'échelle en gardant le coin haut-gauche : la box grandit sans bouger. */
    @Test
    void offsetForTopLeftPinsCornerAcrossScaleChange() {
        HudElementState small = new HudElementState(12, -7, 0.5f, true, null);
        HudElementBounds before = HudElementBounds.currentFor(
            HudElement.VITALS, small, SCREEN_W, SCREEN_H);

        HudElementState big = small.withScale(2.0f);
        int[] off = HudElementBounds.offsetForTopLeft(
            HudElement.VITALS, big, SCREEN_W, SCREEN_H, before.x(), before.y());
        HudElementBounds after = HudElementBounds.currentFor(
            HudElement.VITALS, big.withPos(off[0], off[1]), SCREEN_W, SCREEN_H);

        assertEquals(before.x(), after.x());
        assertEquals(before.y(), after.y());
        assertTrue(after.width() > before.width());
    }
}
