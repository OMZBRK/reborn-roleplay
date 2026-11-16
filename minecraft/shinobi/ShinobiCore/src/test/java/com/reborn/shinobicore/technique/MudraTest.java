package com.reborn.shinobicore.technique;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Suite fixe des techniques à signes sans mudras déclarés (15/11) : règles pures de {@link Mudra}. */
class MudraTest {

    @Test
    void suiteStable_estLaMemePartout() {
        // valeurs de référence calculées hors de la JVM (FNV-1a 64 + SplitMix64) : elles ne doivent jamais changer,
        // sinon la suite apprise par les joueurs changerait avec une mise à jour
        assertEquals(List.of(Mudra.HORSE, Mudra.OX, Mudra.DRAGON, Mudra.RAT), Mudra.suiteStable("katon_hosenka", 4));
        assertEquals(List.of(Mudra.DOG, Mudra.SNAKE, Mudra.DOG, Mudra.BIRD, Mudra.OX, Mudra.HARE),
                Mudra.suiteStable("suiton_suijinheki", 6));
        assertEquals(List.of(Mudra.RAT, Mudra.DOG), Mudra.suiteStable("", 2));
        assertEquals(Mudra.suiteStable(null, 2), Mudra.suiteStable("", 2));
    }

    @Test
    void suiteStable_longueurEtPasDeuxSignesDeSuite() {
        for (int i = 0; i < 500; i++) {
            List<Mudra> s = Mudra.suiteStable("technique_" + i, 6);
            assertEquals(6, s.size());
            for (int j = 1; j < s.size(); j++) assertNotEquals(s.get(j - 1), s.get(j), "technique_" + i);
        }
        assertTrue(Mudra.suiteStable("x", 0).isEmpty());
        assertTrue(Mudra.suiteStable("x", -3).isEmpty());
        assertNotEquals(Mudra.suiteStable("katon_a", 4), Mudra.suiteStable("katon_b", 4));
    }

    @Test
    void aSignes_lesArtsAMainsSeulement() {
        assertTrue(Mudra.aSignes("ninjutsu/katon"));
        assertTrue(Mudra.aSignes("Ninjutsu/Iryo"));
        assertTrue(Mudra.aSignes("kekkei/mokuton"));
        assertTrue(Mudra.aSignes("senjutsu/crapaud"));
        assertTrue(Mudra.aSignes("autres/genjutsu"));
        assertTrue(Mudra.aSignes("autres/kinjutsu"));
        assertTrue(Mudra.aSignes("autres/juinjutsu"));
        assertFalse(Mudra.aSignes("kekkei/dojutsu/sharingan"));
        assertFalse(Mudra.aSignes("taijutsu/goken"));
        assertFalse(Mudra.aSignes("bukijutsu/kenjutsu"));
        assertFalse(Mudra.aSignes("autres/kugutsu"));
        assertFalse(Mudra.aSignes("autres/divers"));
        assertFalse(Mudra.aSignes(null));
    }
}
