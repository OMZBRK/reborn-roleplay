package com.reborn.shinobicore.dojutsu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Règles pures du Byakugan (lot 5, étape B) : droits, coût, blocages, reprise de /sense. */
class ByakuganReglesTest {

    private static final List<String> CLANS = List.of("Hyuga", "Hyūga", "Otsutsuki", "Ōtsutsuki");

    /** Mode staff : la vision du joueur est refusée (critique du 09/11), la vision de test du staff passe. */
    @Test
    void horsJeuEnModeStaff() {
        assertTrue(ByakuganRegles.horsJeu(false, false, true, false), "mode staff, personnage Hyūga : refusé");
        assertFalse(ByakuganRegles.horsJeu(false, true, true, false), "vision de test du staff en mode staff : permise");
        assertFalse(ByakuganRegles.horsJeu(false, true, true, true), "vision de test sans personnage : permise");
        assertTrue(ByakuganRegles.horsJeu(false, false, false, true), "joueur sans personnage : refusé");
        assertTrue(ByakuganRegles.horsJeu(true, true, false, false), "sélection : refusé même au staff");
        assertFalse(ByakuganRegles.horsJeu(false, false, false, false), "joueur en jeu : permis");
    }

    private static ByakuganRegles.Contexte libre() {
        return new ByakuganRegles.Contexte(false, false, false, false, false, false, false, false, null);
    }

    @Test
    void clansAccentsEtCasseIgnores() {
        assertTrue(ByakuganRegles.clanAutorise("Hyūga", CLANS));
        assertTrue(ByakuganRegles.clanAutorise("  HYUGA ", CLANS));
        assertTrue(ByakuganRegles.clanAutorise("ōtsutsuki", List.of("Otsutsuki")));
        assertFalse(ByakuganRegles.clanAutorise("Uchiha", CLANS));
        assertFalse(ByakuganRegles.clanAutorise("", CLANS));
        assertFalse(ByakuganRegles.clanAutorise(null, CLANS));
        assertFalse(ByakuganRegles.clanAutorise("Hyuga", List.of()));
    }

    @Test
    void yeux() {
        // Hyūga intact
        assertTrue(ByakuganRegles.yeuxPermettent("byakugan", false, "byakugan", false, "byakugan"));
        // un seul œil qui voit suffit
        assertTrue(ByakuganRegles.yeuxPermettent("byakugan", true, "byakugan", false, "byakugan"));
        // deux yeux perdus ou aveugles
        assertFalse(ByakuganRegles.yeuxPermettent("byakugan", true, "byakugan", true, "byakugan"));
        // Hyūga qui a reçu des yeux ordinaires
        assertFalse(ByakuganRegles.yeuxPermettent("normal", false, "normal", false, "byakugan"));
        // Ōtsutsuki : œil natif « normal » dans la chirurgie, le Byakugan vient du clan
        assertTrue(ByakuganRegles.yeuxPermettent("normal", false, "normal", true, "normal"));
        // un Byakugan greffé voit, quel que soit l'œil natif
        assertTrue(ByakuganRegles.yeuxPermettent("Byakugan", false, "sharingan", false, "normal"));
    }

    @Test
    void cout() {
        // 0,1 %/s sur une réserve de 50 000 : 50 par seconde, 25 par demi-seconde
        assertEquals(25.0, ByakuganRegles.coutPeriode(50_000, 0.1, 500), 1e-9);
        assertEquals(16.0, ByakuganRegles.coutPeriode(16_000, 0.1, 1000), 1e-9);
        assertEquals(0.0, ByakuganRegles.coutPeriode(50_000, 0.0, 500), 1e-9);
        assertEquals(0.0, ByakuganRegles.coutPeriode(0, 0.1, 500), 1e-9);
        // réserve pleine : 1000 s (16 min 40 s) de vision ; à 5 % : 50 s
        assertEquals(1000.0, ByakuganRegles.autonomieSecondes(1.0, 0.1), 1e-9);
        assertEquals(50.0, ByakuganRegles.autonomieSecondes(0.05, 0.1), 1e-9);
        assertTrue(Double.isInfinite(ByakuganRegles.autonomieSecondes(0.5, 0)));
    }

    @Test
    void reglagesBornes() {
        ByakuganRegles.Reglages r = new ByakuganRegles.Reglages(true, 900, -2, true, true, 99);
        assertEquals(128.0, r.portee(), 1e-9);
        assertEquals(0.0, r.pctParSeconde(), 1e-9);
        assertEquals(32.0, r.signeRayon(), 1e-9);
        assertEquals(50.0, ByakuganRegles.Reglages.DEFAUT.portee(), 1e-9);
        assertEquals(0.1, ByakuganRegles.Reglages.DEFAUT.pctParSeconde(), 1e-9);
    }

    @Test
    void blocages() {
        assertNull(ByakuganRegles.refus(libre()));
        assertEquals("Tu es inconscient.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, true, false, false, false, false, false, null)));
        assertEquals("Tu es à terre.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, true, false, false, false, false, false, false, null)));
        assertEquals("Ton personnage est mort.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(true, true, true, true, true, true, true, true, "x")));
        assertEquals("Cinématique en cours.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, false, true, false, false, false, null)));
        assertEquals("Douane : aucune mécanique RP.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, false, false, true, false, false, null)));
        assertEquals("Hors jeu.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, false, false, false, true, false, null)));
        // la matrice d'états (TECHNIQUE à 0) passe avant l'écran noir ; une raison vide ne compte pas
        assertEquals("On te porte.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, false, false, false, false, true, "On te porte.")));
        assertEquals("Écran noir.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, false, false, false, false, true, " ")));
        assertEquals("Impossible en spectateur.", ByakuganRegles.refus(
            new ByakuganRegles.Contexte(false, false, false, true, false, false, false, false, null)));
    }

    @Test
    void repriseDeSense() {
        assertEquals(ByakuganRegles.Reprise.DOJUTSU, ByakuganRegles.reprise("/sense dojutsu"));
        assertEquals(ByakuganRegles.Reprise.DOJUTSU, ByakuganRegles.reprise("/SENTIR   yeux"));
        assertEquals(ByakuganRegles.Reprise.DOJUTSU, ByakuganRegles.reprise("/shinobisense:perception doujutsu"));
        assertEquals(ByakuganRegles.Reprise.STAFF, ByakuganRegles.reprise("/sense byakugan"));
        assertEquals(ByakuganRegles.Reprise.AUCUNE, ByakuganRegles.reprise("/sense"));
        assertEquals(ByakuganRegles.Reprise.AUCUNE, ByakuganRegles.reprise("/sense suppress"));
        assertEquals(ByakuganRegles.Reprise.AUCUNE, ByakuganRegles.reprise("/autre:sense dojutsu"));
        assertEquals(ByakuganRegles.Reprise.AUCUNE, ByakuganRegles.reprise("/msg sense dojutsu"));
        assertEquals(ByakuganRegles.Reprise.AUCUNE, ByakuganRegles.reprise(null));
    }

    @Test
    void plier() {
        assertEquals("hyuga", ByakuganRegles.plier("Hyūga"));
        assertEquals("otsutsuki", ByakuganRegles.plier(" Ōtsutsuki "));
        assertEquals("", ByakuganRegles.plier(null));
    }
}
