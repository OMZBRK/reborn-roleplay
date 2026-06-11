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
    void distance_in_fade_band_lerps_linearly() {
        // refDistance=4, maxDistance=16. Distance 10 (midpoint) → factor 0.5.
        assertEquals(0.5f, OstAudioEngine.distanceFactor(10.0, 4f, 16f), EPSILON);
        // Distance 7 → 75% du chemin restant : (16-7)/(16-4) = 9/12 = 0.75
        assertEquals(0.75f, OstAudioEngine.distanceFactor(7.0, 4f, 16f), EPSILON);
        // Distance 13 → (16-13)/(16-4) = 3/12 = 0.25
        assertEquals(0.25f, OstAudioEngine.distanceFactor(13.0, 4f, 16f), EPSILON);
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
