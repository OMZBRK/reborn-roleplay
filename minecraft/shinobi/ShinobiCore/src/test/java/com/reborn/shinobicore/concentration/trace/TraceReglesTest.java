package com.reborn.shinobicore.concentration.trace;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Règles des traces de pas (lot 4, étape C, planche c7) : durée de vie, exclusions, anonymat, gabarit. */
class TraceReglesTest {

    private static final TraceRegles.Etat MARCHE = new TraceRegles.Etat(true, false, false, false, false, false, false, false, false, false);

    @Test
    void sol_lesFamillesDeLaPlanche() {
        for (String m : new String[]{"SAND", "RED_SAND", "SNOW", "SNOW_BLOCK", "POWDER_SNOW", "MUD", "SOUL_SAND"}) {
            assertEquals(TraceRegles.Sol.MOU, TraceRegles.sol(m), m);
        }
        for (String m : new String[]{"GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "MOSS_BLOCK", "GRAVEL"}) {
            assertEquals(TraceRegles.Sol.NORMAL, TraceRegles.sol(m), m);
        }
        for (String m : new String[]{"STONE", "COBBLESTONE", "STONE_BRICKS", "DIRT_PATH", "OAK_PLANKS", "SPRUCE_SLAB",
            "SPRUCE_STAIRS", "OAK_LOG", "DEEPSLATE_TILES", "POLISHED_ANDESITE", "OAK_TRAPDOOR"}) {
            assertEquals(TraceRegles.Sol.DUR, TraceRegles.sol(m), m);
        }
        // pièges : le grès est de la pierre, pas du sable ; la brique de boue aussi
        assertEquals(TraceRegles.Sol.DUR, TraceRegles.sol("SANDSTONE"));
        assertEquals(TraceRegles.Sol.DUR, TraceRegles.sol("RED_SANDSTONE_SLAB"));
        assertEquals(TraceRegles.Sol.DUR, TraceRegles.sol("MUD_BRICKS"));
        assertEquals(TraceRegles.Sol.NORMAL, TraceRegles.sol(null));
    }

    @Test
    void meteo_rebornEtMonde_abriProtege() {
        assertEquals(TraceRegles.Meteo.CLAIR, TraceRegles.meteo(0, false, false, false));
        assertEquals(TraceRegles.Meteo.CLAIR, TraceRegles.meteo(3, false, false, false));   // la brume n'efface rien
        assertEquals(TraceRegles.Meteo.PLUIE, TraceRegles.meteo(1, false, false, false));
        assertEquals(TraceRegles.Meteo.PLUIE, TraceRegles.meteo(2, false, false, false));   // vent de sable
        assertEquals(TraceRegles.Meteo.PLUIE, TraceRegles.meteo(0, true, false, false));
        assertEquals(TraceRegles.Meteo.TEMPETE, TraceRegles.meteo(4, false, false, false));
        assertEquals(TraceRegles.Meteo.TEMPETE, TraceRegles.meteo(5, true, false, false));
        assertEquals(TraceRegles.Meteo.TEMPETE, TraceRegles.meteo(0, true, true, false));
        assertEquals(TraceRegles.Meteo.CLAIR, TraceRegles.meteo(5, true, true, true));      // sous un toit
    }

    @Test
    void vie_baseFoisSolFoisMeteoFoisAccroupi() {
        int b = TraceRegles.VIE_BASE_S;
        // « une trace de pas reste » (08/11) : 2 h de base
        assertEquals(7200, b);
        assertEquals(7200, TraceRegles.vieSecondes(b, TraceRegles.Sol.NORMAL, TraceRegles.Meteo.CLAIR, false));
        assertEquals(14400, TraceRegles.vieSecondes(b, TraceRegles.Sol.MOU, TraceRegles.Meteo.CLAIR, false));
        assertEquals(2880, TraceRegles.vieSecondes(b, TraceRegles.Sol.DUR, TraceRegles.Meteo.CLAIR, false));
        assertEquals(3600, TraceRegles.vieSecondes(b, TraceRegles.Sol.NORMAL, TraceRegles.Meteo.PLUIE, false));
        assertEquals(2160, TraceRegles.vieSecondes(b, TraceRegles.Sol.NORMAL, TraceRegles.Meteo.TEMPETE, false));
        assertEquals(3600, TraceRegles.vieSecondes(b, TraceRegles.Sol.NORMAL, TraceRegles.Meteo.CLAIR, true));
        // cumul : sable (× 2), tempête (× 0,3), accroupi (× 0,5)
        assertEquals(2160, TraceRegles.vieSecondes(b, TraceRegles.Sol.MOU, TraceRegles.Meteo.TEMPETE, true));
        // jamais zéro
        assertEquals(1, TraceRegles.vieSecondes(1, TraceRegles.Sol.DUR, TraceRegles.Meteo.TEMPETE, true));
    }

    @Test
    void exclusions_chacuneSuffit() {
        assertTrue(TraceRegles.marque(MARCHE));
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(false, false, false, false, false, false, false, false, false, false)), "en l'air");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, true, false, false, false, false, false, false, false, false)), "mode staff");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, true, false, false, false, false, false, false, false)), "vanish");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, true, false, false, false, false, false, false)), "spectateur");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, true, false, false, false, false, false)), "vol");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, false, true, false, false, false, false)), "eau");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, false, false, true, false, false, false)), "monture");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, false, false, false, true, false, false)), "porté");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, false, false, false, false, true, false)), "cinématique");
        assertFalse(TraceRegles.marque(new TraceRegles.Etat(true, false, false, false, false, false, false, false, false, true)), "hors jeu");
    }

    @Test
    void exclusions_sansLeSol_unSautNeCoupePasLaMarche() {
        assertFalse(TraceRegles.exclu(new TraceRegles.Etat(false, false, false, false, false, false, false, false, false, false)), "en l'air");
        assertTrue(TraceRegles.exclu(new TraceRegles.Etat(false, false, false, false, true, false, false, false, false, false)), "en vol");
        assertTrue(TraceRegles.exclu(new TraceRegles.Etat(false, false, false, false, false, true, false, false, false, false)), "dans l'eau");
        assertTrue(TraceRegles.exclu(new TraceRegles.Etat(true, true, false, false, false, false, false, false, false, false)), "mode staff");
    }

    @Test
    void sprint_aLaVitesseReelle_leTrotResteUneMarche() {
        double seuil = TraceRegles.VITESSE_SPRINT;
        // allures : marche × 0,75, trot × 1,15, sprint × 1,55 des 4,317 blocs/s de Minecraft
        assertFalse(TraceRegles.sprinte(4.317 * 0.75, seuil, false), "marche");
        assertFalse(TraceRegles.sprinte(4.317 * 1.15, seuil, false), "trot");
        assertTrue(TraceRegles.sprinte(4.317 * 1.55, seuil, false), "sprint");
        // un saut en course va plus vite que le sol (× 1,27)
        assertFalse(TraceRegles.sprinte(4.317 * 1.15 * 1.27, seuil, true), "trot en sautant");
        assertTrue(TraceRegles.sprinte(4.317 * 1.55 * 1.27, seuil, true), "sprint en sautant");
    }

    @Test
    void biomesSecs_laPluieDuMondeNyTombePas() {
        for (String b : new String[]{"desert", "savanna", "savanna_plateau", "windswept_savanna", "badlands", "eroded_badlands",
            "wooded_badlands", "nether_wastes", "basalt_deltas", "the_end", "end_highlands", "small_end_islands", "the_void"}) {
            assertTrue(TraceRegles.biomeSec(b), b);
        }
        for (String b : new String[]{"plains", "forest", "snowy_plains", "taiga", "jungle", "beach", "swamp", "lush_caves"}) {
            assertFalse(TraceRegles.biomeSec(b), b);
        }
        assertFalse(TraceRegles.biomeSec(null));
    }

    @Test
    void jour_uneCleNeChangePasApresMinuit() {
        long minuit = 20_000L * 86_400_000L;
        assertEquals(20_000L, TraceRegles.jour(minuit));
        assertEquals(19_999L, TraceRegles.jour(minuit - 1));
        UUID a = new UUID(1, 2), v = new UUID(5, 6);
        // le pas né à 23 h 58 : même clé renvoyé à 00 h 01, puisqu'elle suit sa naissance et non l'horloge
        long nee = minuit - 120_000L;
        assertEquals(TraceRegles.cle(a, v, TraceRegles.jour(nee), 42L), TraceRegles.cle(a, v, TraceRegles.jour(nee), 42L));
        assertNotEquals(TraceRegles.cle(a, v, TraceRegles.jour(nee), 42L), TraceRegles.cle(a, v, TraceRegles.jour(minuit), 42L),
            "un pas né après minuit a une autre clé");
    }

    @Test
    void espacement_selonAllureEtTaille() {
        assertEquals(1.2, TraceRegles.espacement(false, false, false, 1.0), 1e-9);
        assertEquals(2.0, TraceRegles.espacement(true, false, false, 1.0), 1e-9);
        assertEquals(0.8, TraceRegles.espacement(false, true, false, 1.0), 1e-9);
        assertEquals(1.0, TraceRegles.espacement(false, false, true, 1.0), 1e-9);
        assertEquals(0.8, TraceRegles.espacement(true, true, false, 1.0), 1e-9);    // accroupi d'abord
        assertEquals(1.2 * Math.sqrt(1.21), TraceRegles.espacement(false, false, false, 1.21), 1e-9);
        assertEquals(1.2 * Math.sqrt(TraceRegles.K_MAX), TraceRegles.espacement(false, false, false, 9.0), 1e-9);
    }

    @Test
    void pied_gaucheDroiteDeLAxe_pointeVersLExterieur() {
        double[] g = new double[3], d = new double[3];
        // marche vers +z (sud) : la droite du marcheur est vers -x
        TraceRegles.pied(0, 0, 0, false, 1.0, 3, g);
        TraceRegles.pied(0, 0, 0, true, 1.0, 3, d);
        assertEquals(TraceRegles.DECAL, g[0], 1e-9);
        assertEquals(-TraceRegles.DECAL, d[0], 1e-9);
        assertEquals(0, g[1], 1e-9);
        // même rang : seule l'ouverture sépare les deux pieds (droite : cap diminué, vers -x)
        assertEquals(2 * TraceRegles.OUVERTURE, g[2] - d[2], 1e-9);
        // l'écart suit la taille
        TraceRegles.pied(0, 0, 0, true, 1.25, 3, d);
        assertEquals(-TraceRegles.DECAL * 1.25, d[0], 1e-9);
    }

    @Test
    void drapeaux_bits() {
        assertEquals(0, TraceRegles.drapeaux(false, false, false, false, false));
        assertEquals(TraceRegles.F_DROIT | TraceRegles.F_SPRINT, TraceRegles.drapeaux(true, true, false, false, false));
        assertEquals(TraceRegles.F_ACCROUPI | TraceRegles.F_CHARGE | TraceRegles.F_SANG, TraceRegles.drapeaux(false, false, true, true, true));
    }

    @Test
    void cle_unePiste_parMarcheurSpectateurJour() {
        UUID a = new UUID(1, 2), b = new UUID(3, 4), v1 = new UUID(5, 6), v2 = new UUID(7, 8);
        long sel = 42L, jour = 20_000L;
        int k = TraceRegles.cle(a, v1, jour, sel);
        assertEquals(k, TraceRegles.cle(a, v1, jour, sel), "stable pour une même piste");
        assertTrue(k >= 0 && k <= 0xFFFF);
        assertNotEquals(k, TraceRegles.cle(b, v1, jour, sel), "autre marcheur");
        assertNotEquals(k, TraceRegles.cle(a, v2, jour, sel), "autre spectateur : on ne recoupe pas entre joueurs");
        assertNotEquals(k, TraceRegles.cle(a, v1, jour + 1, sel), "autre jour");
        assertNotEquals(k, TraceRegles.cle(a, v1, jour, sel + 1), "autre sel");
    }
}
