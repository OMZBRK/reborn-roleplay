package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Règles des plaques (lot 4, étape B) : ce qui décide si le texte part chez un client, et qui peut quoi. */
class PlaqueReglesTest {

    /** Stèle d'un bloc de large, deux de haut, en (10, 64, 0). */
    private static final double[] STELE = Ancre.bloc("world", 10, 64, 0, 1, 2, 1).boite();

    @Test
    void portee_toleranceDunBlocEtDemi() {
        // œil à 1,62 au-dessus du sol, face à la stèle sur l'axe x : distance à la face = 10 - x
        assertTrue(PlaqueRegles.aPortee(4.0, 65.6, 0.5, STELE, 6));      // 6 blocs
        assertTrue(PlaqueRegles.aPortee(2.6, 65.6, 0.5, STELE, 6));      // 7,4 : dans la tolérance
        assertFalse(PlaqueRegles.aPortee(2.4, 65.6, 0.5, STELE, 6));     // 7,6 : refusé
        // portée bornée de 2 à 16
        assertTrue(PlaqueRegles.aPortee(-7.0, 65.6, 0.5, STELE, 99));    // 17 ≤ 16 + 1,5
        assertTrue(PlaqueRegles.aPortee(6.6, 65.6, 0.5, STELE, 0));     // portée 0 → 2 : 3,4 ≤ 3,5
        assertFalse(PlaqueRegles.aPortee(6.4, 65.6, 0.5, STELE, 0));    // 3,6
        assertEquals(2, PlaqueRegles.bornerPortee(0));
        assertEquals(16, PlaqueRegles.bornerPortee(40));
    }

    @Test
    void distanceBoite_nulleDedans() {
        assertEquals(0, PlaqueRegles.distanceBoite(10.5, 65, 0.5, STELE), 1e-9);
        assertEquals(3, PlaqueRegles.distanceBoite(7, 65, 0.5, STELE), 1e-9);
        assertEquals(Math.sqrt(18), PlaqueRegles.distanceBoite(14, 69, 0.5, STELE), 1e-9);   // (3 ; 3 ; 0)
    }

    @Test
    void cone_faceOuDos() {
        double[] d = {1, 0, 0};   // regarde vers +x
        assertTrue(PlaqueRegles.dansCone(4, 65.6, 0.5, d[0], d[1], d[2], STELE));
        assertFalse(PlaqueRegles.dansCone(4, 65.6, 0.5, -1, 0, 0, STELE));       // dos tourné
        assertFalse(PlaqueRegles.dansCone(4, 65.6, 0.5, 0, 0, 1, STELE));        // 90° à côté
        // 45° à côté : dans le cône du serveur (60°), large exprès (caméra à l'épaule)
        assertTrue(PlaqueRegles.dansCone(4, 65.6, 0.5, 1, 0, 1, STELE));
        assertEquals(90, PlaqueRegles.angle(0, 0, 0, 1, 0, 0, 0, 0, 5), 1e-6);
    }

    @Test
    void pointProche_rentreVersLeCentre() {
        double[] p = PlaqueRegles.pointProche(4, 65.6, 0.5, STELE, 0.05);
        assertEquals(10.05, p[0], 1e-9);
        assertEquals(65.55, p[1], 1e-9);          // rentré aussi vers le centre en hauteur
        assertEquals(0.5, p[2], 1e-9);
        assertTrue(PlaqueRegles.dans(STELE, p[0], p[1], p[2], 0));
    }

    @Test
    void droits_decisionDu0711() {
        // Helper voit seulement
        assertTrue(PlaqueRegles.peutVoir(StaffGrades.HELPER, false));
        assertFalse(PlaqueRegles.peutCreer(StaffGrades.HELPER, false));
        assertFalse(PlaqueRegles.peutModifier(StaffGrades.HELPER, false, true, false));
        // joueur sans grade ni mode animation : rien
        assertFalse(PlaqueRegles.peutVoir(StaffGrades.NONE, false));
        // Modérateur crée, modifie celles des autres
        assertTrue(PlaqueRegles.peutCreer(StaffGrades.MODO, false));
        assertTrue(PlaqueRegles.peutModifier(StaffGrades.MODO, false, false, false));
        assertFalse(PlaqueRegles.peutRestaurer(StaffGrades.MODO));
        assertTrue(PlaqueRegles.peutRestaurer(StaffGrades.ADMIN));
        // animateur : temporaires, les siennes
        assertTrue(PlaqueRegles.peutCreer(StaffGrades.NONE, true));
        assertTrue(PlaqueRegles.temporaire(StaffGrades.HELPER, true));
        assertFalse(PlaqueRegles.temporaire(StaffGrades.MODO, true));
        assertTrue(PlaqueRegles.peutModifier(StaffGrades.NONE, true, true, true));
        assertFalse(PlaqueRegles.peutModifier(StaffGrades.NONE, true, false, true));    // pas la sienne
        assertFalse(PlaqueRegles.peutModifier(StaffGrades.NONE, true, true, false));    // permanente
        // animateur sans grade : l'outil, mais ni les textes des autres, ni la corbeille, ni le téléport
        assertTrue(PlaqueRegles.peutVoir(StaffGrades.NONE, true));
        assertFalse(PlaqueRegles.voirTout(StaffGrades.NONE));
        assertTrue(PlaqueRegles.voirTout(StaffGrades.HELPER));
    }

    @Test
    void saisie_texteExpireBoite() {
        assertNull(PlaqueRegles.validerTexte(700));
        assertNotNull(PlaqueRegles.validerTexte(701));
        assertNotNull(PlaqueRegles.validerTexte(0));
        assertNull(PlaqueRegles.validerExpire(false, 0));
        assertNotNull(PlaqueRegles.validerExpire(true, 0));       // animateur : expiration obligatoire
        assertNull(PlaqueRegles.validerExpire(true, 1440));
        assertNotNull(PlaqueRegles.validerExpire(true, 1441));
        assertTrue(PlaqueRegles.boiteValide(3, 2, 1));
        assertFalse(PlaqueRegles.boiteValide(33, 1, 1));
        assertFalse(PlaqueRegles.boiteValide(0, 1, 1));
        assertEquals("P1", PlaqueRegles.id(1));
        assertEquals("PZ", PlaqueRegles.id(35));
        assertEquals("P10", PlaqueRegles.id(36));
        assertFalse(PlaqueRegles.motifValide("ok"));
        assertTrue(PlaqueRegles.motifValide("doublon"));
    }

    @Test
    void limiteur_fenetreGlissante() {
        PlaqueRegles.Limiteur<String> l = new PlaqueRegles.Limiteur<>(4, 1000);
        for (int i = 0; i < 4; i++) assertTrue(l.essayer("a", 100 + i));
        assertFalse(l.essayer("a", 500));          // 5e bascule dans la seconde
        assertTrue(l.essayer("b", 500));           // un autre joueur
        assertTrue(l.essayer("a", 1101));          // la plus ancienne est sortie de la fenêtre
        l.oublier("a");
        for (int i = 0; i < 4; i++) assertTrue(l.essayer("a", 1200));
    }

    @Test
    void ancreEntite_toleranceDunBloc() {
        // ItemDisplay sans boîte : compte pour 1 × 1 × 1 autour de sa position
        double[] b = Ancre.boiteEntite(5.5, 70, 5.5, 0, 0);
        assertEquals(5.0, b[0], 1e-9);
        assertEquals(71.0, b[4], 1e-9);
        assertEquals("boîte 3×2×1 world 11 64 -30", Ancre.bloc("world", 11, 64, -30, 3, 2, 1).libelle());
        assertEquals(6, Ancre.bloc("world", 0, 0, 0, 3, 2, 1).blocs());
    }

    @Test
    void face_seLitDuSeulCoteVise() {
        // mur de tombeau : bloc (10, 64, 0), inscription sur sa face ouest (intérieur du tombeau, x < 10)
        double[] mur = Ancre.bloc("world", 10, 64, 0, 1, 1, 1, "west").boite();
        assertTrue(PlaqueRegles.faceVisible("west", 7.5, 65.6, 0.5, mur), "dans le tombeau, face à l'inscription : lue");
        assertFalse(PlaqueRegles.faceVisible("west", 13.5, 65.6, 0.5, mur), "dehors, face au même bloc : rien");
        assertTrue(PlaqueRegles.faceVisible("", 13.5, 65.6, 0.5, mur), "sans face : tous côtés");
        assertTrue(PlaqueRegles.faceVisible("up", 10.5, 65.6, 0.5, mur), "dessus : l'œil plus haut que le bloc");
        assertFalse(PlaqueRegles.faceVisible("down", 10.5, 65.6, 0.5, mur));
        assertEquals("west", Ancre.bloc("w", 0, 0, 0, 1, 1, 1, "WEST").face(), "nom Paper (BlockFace) accepté");
        assertEquals("", Ancre.bloc("w", 0, 0, 0, 1, 1, 1, "nimporte").face());
        assertTrue(Ancre.bloc("world", 10, 64, 0, 1, 1, 1, "west").libelle().endsWith("face ouest"));
    }

    @Test
    void expiration_animateurPlafonneeA24hApresCreation() {
        long h = 3_600_000L, cree = 1_000_000_000_000L;
        // animateur, 20 h après la création, redemande 24 h : la plaque meurt quand même à cree + 24 h
        PlaqueRegles.Expiration x = PlaqueRegles.expireModif(false, true, cree, cree + 20 * h, 1440);
        assertNull(x.erreur());
        assertEquals(cree + 24 * h, x.expire());
        assertTrue(x.temporaire());
        // plus de 24 h après la création : plus de prolongation
        assertNotNull(PlaqueRegles.expireModif(false, true, cree, cree + 25 * h, 60).erreur());
        // jamais / plus de 24 h : refusé pour un animateur
        assertNotNull(PlaqueRegles.expireModif(false, true, cree, cree + h, 0).erreur());
        assertNotNull(PlaqueRegles.expireModif(false, true, cree, cree + h, 1441).erreur());
        // un Modérateur rend permanente la plaque temporaire d'un animateur
        PlaqueRegles.Expiration m = PlaqueRegles.expireModif(true, true, cree, cree + h, 0);
        assertNull(m.erreur());
        assertEquals(0, m.expire());
        assertFalse(m.temporaire(), "« jamais » par un modérateur : plus temporaire");
        // un Modérateur fixe 7 jours : la plaque reste temporaire (l'animateur la garde en main)
        m = PlaqueRegles.expireModif(true, true, cree, cree + h, 10080);
        assertEquals(cree + h + 10080 * 60_000L, m.expire());
        assertTrue(m.temporaire());
        assertNotNull(PlaqueRegles.expireModif(true, false, cree, cree, -1).erreur());
    }

    @Test
    void boiteLecture_cadreAutourDeSonCentre_meubleDepuisSonPied() {
        // cadre accroché sur la face sud d'un mur (z = 9..10) : boîte réelle 0,75 × 0,75 × 1/16
        double[] c = Ancre.boiteLecture(5.125, 65.125, 10.0, 5.875, 65.875, 10.0625, true);
        assertEquals(5.0, c[0], 1e-9);
        assertEquals(65.0, c[1], 1e-9, "centré en hauteur sur le cadre, pas posé sur son milieu");
        assertEquals(66.0, c[4], 1e-9);
        assertEquals(9.53125, c[2], 1e-9);
        assertEquals(10.53125, c[5], 1e-9);
        // ItemDisplay de meuble Nexo (pas de boîte) : 1 × 1 × 1 depuis son pied
        double[] m = Ancre.boiteLecture(5.5, 64, 11.5, 5.5, 64, 11.5, false);
        assertEquals(64.0, m[1], 1e-9);
        assertEquals(65.0, m[4], 1e-9);
        assertEquals(5.0, m[0], 1e-9);
        // tableau 4 × 3, mince en x : 4 de large en z, 1 en x
        double[] t = Ancre.boiteLecture(9.97, 64, 0, 10.0, 67, 4, true);
        assertEquals(1.0, t[3] - t[0], 1e-9);
        assertEquals(4.0, t[5] - t[2], 1e-9);
        // l'ancre garde la boîte telle quelle (aller-retour)
        double[] r = Ancre.entite("world", java.util.UUID.randomUUID(), t).boite();
        for (int i = 0; i < 6; i++) assertEquals(t[i], r[i], 1e-9);
    }
}
