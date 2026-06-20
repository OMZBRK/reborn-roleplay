package fr.reborn.ost.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de la courbe d'atténuation par distance — c'est la seule logique
 * du moteur qui peut être testée sans contexte OpenAL.
 */
class OstAudioEngineTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void distance_below_reference_gives_full_gain() {
        assertEquals(1.0f, OstAudioEngine.distanceFactor(0.0, 4f, 16f), EPSILON);
        assertEquals(1.0f, OstAudioEngine.distanceFactor(2.0, 4f, 16f), EPSILON);
        assertEquals(1.0f, OstAudioEngine.distanceFactor(4.0, 4f, 16f), EPSILON);
    }

    @Test
    void distance_at_or_above_max_gives_zero() {
        assertEquals(0.0f, OstAudioEngine.distanceFactor(16.0, 4f, 16f), EPSILON);
        assertEquals(0.0f, OstAudioEngine.distanceFactor(50.0, 4f, 16f), EPSILON);
    }

    @Test
    void distance_in_fade_band_follows_smoothstep_curve() {
        // refDistance=4, maxDistance=16. Smoothstep f(t) = 1 - (3t^2 - 2t^3).
        // Midpoint t=0.5 -> smooth=0.5, factor=0.5 (idem que linear, par symetrie).
        assertEquals(0.5f, OstAudioEngine.distanceFactor(10.0, 4f, 16f), EPSILON);
        // Distance 7 -> t=(7-4)/12=0.25 -> smooth=3*0.0625-2*0.015625=0.15625 -> factor=0.84375
        assertEquals(0.84375f, OstAudioEngine.distanceFactor(7.0, 4f, 16f), EPSILON);
        // Distance 13 -> t=0.75 -> smooth=0.84375 -> factor=0.15625
        assertEquals(0.15625f, OstAudioEngine.distanceFactor(13.0, 4f, 16f), EPSILON);
    }

    @Test
    void smoothstep_has_zero_slope_at_boundaries() {
        // Juste au-dessus de refDistance la pente smoothstep est ~0, donc le
        // factor reste tres proche de 1.0. Garantit l'absence de pop audible
        // au passage de la limite "core" -> "fade".
        assertEquals(1.0f, OstAudioEngine.distanceFactor(4.01, 4f, 16f), 1e-3f);
        // Juste avant maxDistance, factor reste tres proche de 0.0.
        assertEquals(0.0f, OstAudioEngine.distanceFactor(15.99, 4f, 16f), 1e-3f);
    }

    @Test
    void degenerate_ref_equals_max_is_step_function() {
        // Si refDistance == maxDistance, pas de bande de fade — soit on est dedans
        // (factor 1), soit dehors (factor 0). Garde-fou contre une division par
        // zéro silencieuse si le caller passe deux fois la même valeur.
        assertEquals(1.0f, OstAudioEngine.distanceFactor(4.0, 4f, 4f), EPSILON);
        assertEquals(0.0f, OstAudioEngine.distanceFactor(4.1, 4f, 4f), EPSILON);
    }
}
