package fr.reborn.hud.ui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests des helpers de la palette : withAlpha conserve le RGB, lerp
 * interpole linéairement les composantes en gardant l'alpha de la couleur
 * source.
 */
class RebornColorsTest {

    @Test
    void withAlpha_overwritesOnlyAlpha() {
        int red = 0xFFFF0000;
        int result = RebornColors.withAlpha(red, 0x80);
        assertEquals(0x80FF0000, result);
    }

    @Test
    void withAlpha_clampsHighBitsOfAlpha() {
        // alphaByte=0x1FF doit être masque a 0xFF
        int result = RebornColors.withAlpha(0xFF112233, 0x1FF);
        assertEquals(0xFF112233, result);
    }

    @Test
    void lerp_zeroReturnsA() {
        int result = RebornColors.lerp(RebornColors.ACCENT, RebornColors.DANGER, 0f);
        assertEquals(RebornColors.ACCENT, result);
    }

    @Test
    void lerp_oneReturnsBWithAAlpha() {
        // lerp ne touche pas l'alpha — on garde celui de a
        int a = 0x80FF0000; // alpha 0x80, rouge
        int b = 0xFF00FF00; // alpha 0xFF, vert
        int result = RebornColors.lerp(a, b, 1f);
        assertEquals(0x8000FF00, result);
    }

    @Test
    void lerp_halfMidpoint() {
        int a = 0xFF000000;
        int b = 0xFF808080;
        int result = RebornColors.lerp(a, b, 0.5f);
        assertEquals(0xFF404040, result);
    }
}
