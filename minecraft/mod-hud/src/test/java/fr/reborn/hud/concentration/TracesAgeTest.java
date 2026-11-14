package fr.reborn.hud.concentration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'âge estimé d'une trace de pas (retour du 10/11) : paliers, phrases, et un texte qui ne bouge qu'aux bornes. */
class TracesAgeTest {

    private static int p(int minutes, int secondes) { return TracesAge.palier(minutes * 60L + secondes); }

    @Test
    void bornesDesPaliers() {
        assertEquals(0, p(0, 0));
        assertEquals(0, p(0, 59));
        assertEquals(1, p(1, 0));
        assertEquals(1, p(4, 59));
        assertEquals(5, p(5, 0));
        assertEquals(5, p(7, 59));
        assertEquals(10, p(8, 0));
        assertEquals(10, p(12, 59));
        assertEquals(15, p(13, 0));
        assertEquals(15, p(17, 59));
        assertEquals(20, p(18, 0));
        assertEquals(20, p(24, 59));
        assertEquals(30, p(25, 0));
        assertEquals(30, p(34, 59));
        assertEquals(40, p(35, 0));
        assertEquals(50, p(45, 0));
        assertEquals(50, p(54, 59));
        assertEquals(60, p(55, 0));
        assertEquals(60, p(74, 59));
        assertEquals(90, p(75, 0));
        assertEquals(90, p(119, 59));
        assertEquals(120, p(120, 0));
        assertEquals(120, p(600, 0));
        assertEquals(0, TracesAge.palier(-5), "un âge négatif (horloges décalées) = à l'instant");
    }

    @Test
    void phrases() {
        assertEquals("Laissée à l'instant", TracesAge.texte(p(0, 20)));
        assertEquals("Laissée il y a quelques minutes", TracesAge.texte(p(3, 0)));
        assertEquals("Laissée il y a environ 10 min", TracesAge.texte(p(10, 0)));
        assertEquals("Laissée il y a une demi-heure", TracesAge.texte(p(31, 0)));
        assertEquals("Laissée il y a environ 40 min", TracesAge.texte(p(40, 0)));
        assertEquals("Laissée il y a environ une heure", TracesAge.texte(p(62, 0)));
        assertEquals("Laissée il y a environ 1 h 30", TracesAge.texte(p(90, 0)));
        assertEquals("Laissée il y a plus de deux heures", TracesAge.texte(p(150, 0)));
    }

    /** Seconde par seconde sur quatre heures : le palier ne décroît jamais, et ne change qu'aux onze bornes. */
    @Test
    void stableEntreLesBornes() {
        int changements = 0, avant = TracesAge.palier(0);
        for (long s = 1; s <= 4 * 3600; s++) {
            int pa = TracesAge.palier(s);
            assertTrue(pa >= avant, "palier qui recule à " + s + " s");
            if (pa != avant) changements++;
            avant = pa;
        }
        assertEquals(11, changements);
    }

    /** Chaque palier a sa phrase, toutes différentes. */
    @Test
    void phrasesDistinctes() {
        int[] paliers = {0, 1, 5, 10, 15, 20, 30, 40, 50, 60, 90, 120};
        java.util.Set<String> vues = new java.util.HashSet<>();
        for (int pa : paliers) assertTrue(vues.add(TracesAge.texte(pa)), "phrase en double : " + TracesAge.texte(pa));
    }
}
