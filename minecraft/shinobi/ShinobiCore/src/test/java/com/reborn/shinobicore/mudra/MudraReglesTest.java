package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.technique.Mudra;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Règles pures des mudras et des portes scellées (14/11) : minuterie, suites, boîte, calendrier de la levée, droits. */
class MudraReglesTest {

    /* ================================================================ réglages */

    @Test
    void reglages_defautsDuCahier() {
        MudraRegles.Reglages d = MudraRegles.Reglages.DEFAUT;
        assertEquals(5000, d.tempsMs());
        assertEquals(700, d.gainMs());
        assertEquals(12, d.maxSignes());
        assertEquals(120, d.signeMinMs());
        assertEquals(1500, d.rechargeMs());
        assertEquals(24.0, d.gesteRayon());
        assertEquals(1500, d.guideMargeMs());
        assertEquals(6.0, d.portee());
        assertEquals(60, d.dureeTicks());
        assertEquals(8, d.ouverteS());
        assertEquals(5, d.essaisMax());
        assertEquals(60, d.essaisFenetreS());
        assertEquals(120, d.blocageS());
    }

    @Test
    void reglages_bornes() {
        MudraRegles.Reglages r = new MudraRegles.Reglages(10, 99_999, 40, -5, -1, 500, 99_999, 0, 1, 0, -3, 1, 1);
        assertEquals(1000, r.tempsMs(), "ligne de temps d'une seconde au moins");
        assertEquals(1000, r.gainMs(), "le gain ne dépasse jamais la ligne");
        assertEquals(12, r.maxSignes());
        assertEquals(0, r.signeMinMs());
        assertEquals(0, r.rechargeMs());
        assertEquals(64.0, r.gesteRayon());
        assertEquals(10_000, r.guideMargeMs());
        assertEquals(1.0, r.portee());
        assertEquals(10, r.dureeTicks());
        assertEquals(1, r.ouverteS());
        assertEquals(0, r.essaisMax(), "0 = jamais de blocage");
        assertEquals(5, r.essaisFenetreS());
        assertEquals(5, r.blocageS());
        assertEquals(2, new MudraRegles.Reglages(5000, 700, 0, 120, 1500, 24, 1500, 6, 60, 8, 5, 60, 120).maxSignes());
        assertEquals(0.0, new MudraRegles.Reglages(5000, 700, 12, 120, 1500, Double.NaN, 1500, 6, 60, 8, 5, 60, 120).gesteRayon());
    }

    /* ================================================================ essais en série */

    @Test
    void essais_leCinquiemeDansLaMinuteBloque() {
        ArrayDeque<Long> l = new ArrayDeque<>();
        long t = 1_000_000;
        for (int i = 0; i < 4; i++) assertFalse(MudraRegles.essaiDeTrop(l, t + i * 10_000, 5, 60_000), "essai " + (i + 1));
        assertTrue(MudraRegles.essaiDeTrop(l, t + 40_000, 5, 60_000), "le cinquième en moins d'une minute");
        assertTrue(l.isEmpty(), "le compte repart de zéro après le blocage");
    }

    @Test
    void essais_lesAnciensSortentDeLaFenetre() {
        ArrayDeque<Long> l = new ArrayDeque<>();
        long t = 1_000_000;
        for (int i = 0; i < 4; i++) assertFalse(MudraRegles.essaiDeTrop(l, t + i * 1000, 5, 60_000));
        assertFalse(MudraRegles.essaiDeTrop(l, t + 70_000, 5, 60_000), "les quatre premiers ont plus d'une minute");
        assertEquals(1, l.size());
    }

    @Test
    void essais_fenetreStricte() {
        ArrayDeque<Long> l = new ArrayDeque<>();
        for (int i = 0; i < 4; i++) assertFalse(MudraRegles.essaiDeTrop(l, i, 5, 60_000));
        assertTrue(MudraRegles.essaiDeTrop(l, 60_000, 5, 60_000), "un essai vieux de 60 s pile compte encore");
    }

    @Test
    void essais_blocageDureEtNeSeProlongePas() {
        MudraRegles.Essais e = new MudraRegles.Essais();
        long t = 5_000_000;
        for (int i = 0; i < 4; i++) assertFalse(e.compter(t + i * 1000, 5, 60_000, 120_000));
        assertFalse(e.bloquee(t + 4000));
        assertTrue(e.compter(t + 4000, 5, 60_000, 120_000), "le cinquième bloque");
        assertTrue(e.bloquee(t + 4001));
        for (int i = 0; i < 20; i++) assertFalse(e.compter(t + 5000 + i * 1000, 5, 60_000, 120_000), "rien n'est compté pendant");
        assertTrue(e.bloquee(t + 4000 + 119_999));
        assertFalse(e.bloquee(t + 4000 + 120_000), "le blocage ne s'est pas prolongé");
        assertFalse(e.compter(t + 4000 + 120_000, 5, 60_000, 120_000), "le compte repart de zéro");
    }

    @Test
    void essais_eteint() {
        MudraRegles.Essais e = new MudraRegles.Essais();
        assertTrue(e.eteint(1000, 60_000), "jamais d'essai");
        e.compter(1000, 2, 60_000, 120_000);
        assertFalse(e.eteint(2000, 60_000), "un essai dans la fenêtre");
        assertTrue(e.eteint(61_001, 60_000));
        e.compter(70_000, 2, 60_000, 120_000);
        assertTrue(e.compter(70_500, 2, 60_000, 120_000));
        assertFalse(e.eteint(100_000, 60_000), "bloqué");
        assertTrue(e.eteint(190_500, 60_000), "blocage fini, plus aucun essai");
        assertFalse(new MudraRegles.Essais().compter(1, 0, 60_000, 120_000), "0 = jamais");
    }

    @Test
    void commencePar_reconnaitLaBonneSuite() {
        assertTrue(MudraRegles.commencePar(List.of(2, 5, 10), List.of(2, 5, 10)));
        assertTrue(MudraRegles.commencePar(List.of(2, 5, 10, 3), List.of(2, 5, 10)), "signes posés après la bonne suite");
        assertFalse(MudraRegles.commencePar(List.of(2, 5), List.of(2, 5, 10)));
        assertFalse(MudraRegles.commencePar(List.of(3, 2, 5, 10), List.of(2, 5, 10)));
        assertFalse(MudraRegles.commencePar(List.of(2, 5, 10), List.of()));
        assertFalse(MudraRegles.commencePar(null, List.of(2)));
    }

    @Test
    void essais_zeroNeBloqueJamais() {
        ArrayDeque<Long> l = new ArrayDeque<>();
        for (int i = 0; i < 100; i++) assertFalse(MudraRegles.essaiDeTrop(l, 1000 + i, 0, 60_000));
        assertTrue(l.isEmpty(), "rien n'est noté");
    }

    /* ================================================================ minuterie */

    @Test
    void minuterie_echeanceEtTolerance() {
        long e = MudraRegles.echeance(10_000, 5000);
        assertEquals(15_000, e);
        assertEquals(5000, MudraRegles.reste(e, 10_000));
        assertEquals(0, MudraRegles.reste(e, 16_000), "jamais négatif");
        assertFalse(MudraRegles.echue(e, 14_999));
        assertFalse(MudraRegles.echue(e, 15_000));
        assertFalse(MudraRegles.echue(e, 15_500), "500 ms de tolérance en faveur du joueur");
        assertTrue(MudraRegles.echue(e, 15_501));
    }

    @Test
    void minuterie_gainSansDepasserLaLigne() {
        long e = MudraRegles.echeance(0, 5000);
        // signe tout de suite : la ligne est déjà pleine, elle ne déborde pas
        assertEquals(5100, MudraRegles.apresSigne(e, 100, 5000, 700));
        // signe à 2 s : il reste 3 s, + 0,7 s
        assertEquals(2000 + 3700, MudraRegles.apresSigne(e, 2000, 5000, 700));
        // signe dans la tolérance (ligne vide) : il repart avec le gain seul
        assertEquals(5200 + 700, MudraRegles.apresSigne(e, 5200, 5000, 700));
        // gain négatif d'un réglage abîmé : ignoré
        assertEquals(2000 + 3000, MudraRegles.apresSigne(e, 2000, 5000, -50));
    }

    @Test
    void minuterie_guideRepartPleine() {
        long temps = 3500;
        long e = MudraRegles.echeance(0, temps);
        e = MudraRegles.apresSigne(e, 3400, temps, temps);
        assertEquals(3400 + temps, e, "gain = temps : la ligne repart pleine à chaque bon signe");
        e = MudraRegles.apresSigne(e, 3500, temps, temps);
        assertEquals(3500 + temps, e);
    }

    @Test
    void signeRecevable_bornes() {
        assertTrue(MudraRegles.signeRecevable(0, 0, 1000), "premier signe : aucun écart exigé");
        assertTrue(MudraRegles.signeRecevable(11, 0, 1000));
        assertFalse(MudraRegles.signeRecevable(12, 0, 1000), "hors bornes");
        assertFalse(MudraRegles.signeRecevable(-1, 0, 1000));
    }

    /** Rejoue des arrivées (ms) contre la cadence ; rend le nombre de signes gardés. */
    private static int gardes(long signeMinMs, long... arrivees) {
        long auPlusTot = 0;
        int n = 0;
        for (long t : arrivees) {
            if (!MudraRegles.signeRecevable(3, auPlusTot, t)) continue;
            auPlusTot = MudraRegles.signeSuivant(auPlusTot, t, signeMinMs);
            n++;
        }
        return n;
    }

    @Test
    void cadence_unSignePoseABonneDistanceNestJamaisJete() {
        // deux signes posés à 130 ms d'écart, lus à 100 ms d'écart par le serveur (battement de 50 ms) : gardés tous deux
        assertEquals(2, gardes(120, 10_050, 10_150));
        // tick long : trois signes posés à 200 ms d'écart arrivent au même instant
        assertEquals(3, gardes(120, 20_000, 20_000, 20_000));
        // retransmission : cinq signes du mod (200 ms d'écart) tassés en une rafale, puis la suite au rythme normal
        assertEquals(8, gardes(120, 1000, 1000, 1000, 1000, 1000, 1200, 1400, 1600));
        // rythme régulier, à l'écart minimal exact puis plus lent : rien n'est jeté, quelle que soit la durée
        long[] regulier = new long[12];
        for (int i = 0; i < regulier.length; i++) regulier[i] = 5000 + i * 120L;
        assertEquals(12, gardes(120, regulier));
        assertEquals(4, gardes(2000, 0, 2000, 4000, 9000));
        // l'avance tolérée ne se cumule pas : après une pause, la marge est de nouveau entière, pas davantage
        assertEquals(1 + 5, gardes(120, 0, 60_000, 60_000, 60_000, 60_000, 60_000, 60_000, 60_000));
    }

    @Test
    void cadence_uneRafaleResteBornee() {
        // douze signes au même instant : le premier, puis ce que couvre la marge de 500 ms (4 de plus à 120 ms)
        long[] rafale = new long[12];
        java.util.Arrays.fill(rafale, 7000);
        assertEquals(5, gardes(120, rafale));
        // dix secondes de signes toutes les 10 ms : jamais plus que le plafond moyen + la marge
        long[] flot = new long[1000];
        for (int i = 0; i < flot.length; i++) flot[i] = 1000 + i * 10L;
        int n = gardes(120, flot);
        assertTrue(n <= 1 + (10_000 + MudraRegles.GIGUE_MS) / 120, "plafond d'un signe par 120 ms : " + n);
        assertTrue(n >= 10_000 / 120, "le plafond n'est pas plus dur que le réglage : " + n);
        // écart minimal à 0 : jamais trop tôt
        assertEquals(12, gardes(0, rafale));
        // la marge est bornée : un signe en avance de plus de 500 ms sur la cadence est jeté
        long suivant = MudraRegles.signeSuivant(0, 1000, 2000);
        assertEquals(3000, suivant);
        assertTrue(MudraRegles.signeRecevable(3, suivant, 2500), "en avance de 500 ms : gardé");
        assertFalse(MudraRegles.signeRecevable(3, suivant, 2499));
        assertEquals(1000, MudraRegles.signeSuivant(0, 1000, -50), "réglage abîmé : pas d'écart négatif");
    }

    @Test
    void recharge() {
        assertTrue(MudraRegles.rechargeFinie(0, 50, 1500), "jamais de fin : libre");
        assertFalse(MudraRegles.rechargeFinie(1000, 2499, 1500));
        assertTrue(MudraRegles.rechargeFinie(1000, 2500, 1500));
        assertEquals(500, MudraRegles.bornerGuide(10));
        assertEquals(60_000, MudraRegles.bornerGuide(999_999));
        assertEquals(3500, MudraRegles.bornerGuide(3500));
    }

    /* ================================================================ suites */

    @Test
    void ordinaux_ordreDeLaBarre() {
        assertEquals(12, Mudra.values().length);
        assertEquals(0, Mudra.RAT.ordinal());
        assertEquals(11, Mudra.BOAR.ordinal());
        assertEquals(List.of(2, 5, 10), MudraRegles.ordinaux(List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG)));
    }

    @Test
    void memeSuite_exacte() {
        List<Integer> porte = List.of(2, 5, 10);
        assertTrue(MudraRegles.memeSuite(List.of(2, 5, 10), porte));
        assertFalse(MudraRegles.memeSuite(List.of(2, 5), porte), "préfixe : pas encore");
        assertFalse(MudraRegles.memeSuite(List.of(2, 5, 10, 1), porte), "un signe de trop");
        assertFalse(MudraRegles.memeSuite(List.of(5, 2, 10), porte), "l'ordre compte");
        assertFalse(MudraRegles.memeSuite(List.of(), List.of()), "une suite vide n'ouvre rien");
        assertFalse(MudraRegles.memeSuite(null, porte));
    }

    @Test
    void pasGuide_bonDernierMauvais() {
        List<Integer> suite = List.of(2, 5, 5);
        assertEquals(MudraRegles.Pas.BON, MudraRegles.pasGuide(suite, 0, 2));
        assertEquals(MudraRegles.Pas.BON, MudraRegles.pasGuide(suite, 1, 5));
        assertEquals(MudraRegles.Pas.DERNIER, MudraRegles.pasGuide(suite, 2, 5));
        assertEquals(MudraRegles.Pas.MAUVAIS, MudraRegles.pasGuide(suite, 0, 5));
        assertEquals(MudraRegles.Pas.MAUVAIS, MudraRegles.pasGuide(suite, 3, 5), "au-delà de la suite");
        assertEquals(MudraRegles.Pas.MAUVAIS, MudraRegles.pasGuide(null, 0, 2));
        assertEquals(MudraRegles.Pas.DERNIER, MudraRegles.pasGuide(List.of(7), 0, 7));
    }

    @Test
    void pleine() {
        assertFalse(MudraRegles.pleine(11, 12));
        assertTrue(MudraRegles.pleine(12, 12));
    }

    @Test
    void lireSigne_nomsEtPlaces() {
        assertEquals(Mudra.TIGER, MudraRegles.lireSigne("tigre"));
        assertEquals(Mudra.TIGER, MudraRegles.lireSigne("TIGER"));
        assertEquals(Mudra.HARE, MudraRegles.lireSigne("Lièvre"));
        assertEquals(Mudra.OX, MudraRegles.lireSigne("buffle"));
        assertEquals(Mudra.RAT, MudraRegles.lireSigne("1"), "1 = première place de la barre");
        assertEquals(Mudra.BOAR, MudraRegles.lireSigne("12"));
        assertNull(MudraRegles.lireSigne("0"));
        assertNull(MudraRegles.lireSigne("13"));
        assertNull(MudraRegles.lireSigne("licorne"));
        assertNull(MudraRegles.lireSigne(" "));
        assertNull(MudraRegles.lireSigne(null));
    }

    @Test
    void lireSuite_saisieDuStaff() {
        MudraRegles.Suite s = MudraRegles.lireSuite(List.of("tigre", "6", "DOG"));
        assertTrue(s.ok());
        assertEquals(List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG), s.signes());
        // virgules, répétitions
        s = MudraRegles.lireSuite(List.of("rat,rat", "sanglier;"));
        assertTrue(s.ok());
        assertEquals(List.of(Mudra.RAT, Mudra.RAT, Mudra.BOAR), s.signes());
        // inconnu : l'erreur nomme le mot
        s = MudraRegles.lireSuite(List.of("tigre", "licorne"));
        assertFalse(s.ok());
        assertTrue(s.erreur().contains("licorne"), s.erreur());
        assertTrue(s.signes().isEmpty());
        // trop court, trop long
        assertFalse(MudraRegles.lireSuite(List.of("tigre")).ok());
        assertFalse(MudraRegles.lireSuite(List.of()).ok());
        assertFalse(MudraRegles.lireSuite(null).ok());
        assertTrue(MudraRegles.lireSuite(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")).ok());
        assertFalse(MudraRegles.lireSuite(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "1")).ok());
    }

    @Test
    void libelles() {
        assertEquals("Tigre · Serpent · Chien", MudraRegles.libelle(List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG)));
        assertEquals("Tigre · ? · Sanglier", MudraRegles.libelleOrdinaux(List.of(2, 40, 11)));
        assertEquals("", MudraRegles.libelle(List.of()));
    }

    @Test
    void nomTitreIdentifiant() {
        assertEquals("Porte du temple", MudraRegles.nom("  Porte_du_temple "));
        assertEquals(32, MudraRegles.nom("x".repeat(80)).length());
        assertEquals("", MudraRegles.nom(null));
        assertEquals(48, MudraRegles.titre("y".repeat(90)).length());
        assertEquals("Boule de feu", MudraRegles.titre(" Boule de feu "));
        assertEquals("D1", MudraRegles.id(1));
        assertEquals("DZ", MudraRegles.id(35));
        assertEquals("D10", MudraRegles.id(36));
        assertEquals("D1", MudraRegles.id(0));
    }

    /* ================================================================ boîte */

    @Test
    void boite_entreDeuxCoins() {
        MudraRegles.Boite b = MudraRegles.Boite.entre(5, 70, -3, 3, 64, -3);
        assertEquals(new MudraRegles.Boite(3, 64, -3, 3, 7, 1), b);
        assertEquals(21, b.volume());
        assertEquals(1, b.epaisseur());
        assertTrue(b.contient(3, 64, -3));
        assertTrue(b.contient(5, 70, -3));
        assertFalse(b.contient(6, 70, -3));
        assertFalse(b.contient(5, 71, -3));
        assertFalse(b.contient(5, 70, -2));
        assertArrayEquals(new double[]{4.5, 67.5, -2.5}, b.centre(), 1e-9);
    }

    @Test
    void boite_indiceOrdreFixe() {
        MudraRegles.Boite b = new MudraRegles.Boite(10, 60, 20, 3, 4, 2);
        boolean[] vu = new boolean[b.volume()];
        for (int i = 0; i < b.volume(); i++) {
            int[] p = b.position(i);
            assertTrue(b.contient(p[0], p[1], p[2]));
            assertEquals(i, b.indice(p[0], p[1], p[2]), "aller-retour indice ↔ position");
            vu[i] = true;
        }
        for (boolean v : vu) assertTrue(v);
        assertArrayEquals(new int[]{10, 60, 20}, b.position(0), "le coin bas d'abord");
        assertArrayEquals(new int[]{10, 60, 21}, b.position(1), "z d'abord, puis x, puis la rangée du dessus");
        assertArrayEquals(new int[]{11, 60, 20}, b.position(2));
        assertArrayEquals(new int[]{10, 61, 20}, b.position(6));
        assertEquals(-1, b.indice(9, 60, 20));
    }

    @Test
    void boite_recoupeEtDistance() {
        MudraRegles.Boite a = new MudraRegles.Boite(0, 64, 0, 3, 3, 1);
        assertTrue(a.recoupe(new MudraRegles.Boite(2, 66, 0, 3, 3, 1)));
        assertFalse(a.recoupe(new MudraRegles.Boite(3, 64, 0, 3, 3, 1)), "côte à côte : pas de recoupe");
        assertFalse(a.recoupe(new MudraRegles.Boite(0, 67, 0, 3, 3, 1)), "l'une sur l'autre : pas de recoupe");
        assertFalse(a.recoupe(new MudraRegles.Boite(0, 64, 1, 3, 3, 1)));
        assertEquals(0.0, a.distance(1.5, 64, 0.5), 1e-9, "dedans");
        assertEquals(4.0, a.distance(1.5, 64, 5.0), 1e-9, "pieds à 4 blocs de la face");
        assertEquals(5.0, a.distance(-3.0, 64, -4.0), 1e-9, "en diagonale du coin : 3-4-5");
        assertEquals(2.0, a.distance(1.5, 62, 0.5), 1e-9, "sous la dalle");
    }

    @Test
    void validerBoite_limites() {
        assertNull(MudraRegles.validerBoite(1, 1, 1));
        assertNull(MudraRegles.validerBoite(9, 9, 2), "9 × 9 × 2 = 162 : la plus grosse dalle");
        assertNull(MudraRegles.validerBoite(2, 9, 9));
        assertNull(MudraRegles.validerBoite(3, 6, 9), "3 × 6 × 9 = 162");
        assertNotNull(MudraRegles.validerBoite(9, 9, 3), "243 blocs : trop gros");
        assertNotNull(MudraRegles.validerBoite(10, 3, 1), "un côté de 10");
        assertNotNull(MudraRegles.validerBoite(1, 10, 1));
        assertNotNull(MudraRegles.validerBoite(4, 2, 4), "épaisseur 4");
        assertNotNull(MudraRegles.validerBoite(0, 2, 1));
        assertTrue(MudraRegles.validerBoite(4, 2, 4).contains("épaisse"));
    }

    @Test
    void validerContenu_airEtBlocsAContenu() {
        assertNull(MudraRegles.validerContenu(6, 0, 0));
        assertNotNull(MudraRegles.validerContenu(0, 0, 0), "que de l'air");
        assertNotNull(MudraRegles.validerContenu(6, 1, 0), "un coffre dans la dalle");
        // un cadre ou un tableau accroché à la dalle : il tomberait en objet à la levée
        String e = MudraRegles.validerContenu(6, 0, 2);
        assertNotNull(e, "un cadre accroché à la dalle");
        assertTrue(e.contains("2") && e.contains("cadre"), e);
        assertTrue(MudraRegles.validerContenu(6, 1, 2).contains("coffre"), "le bloc à contenu d'abord");
    }

    /* ================================================================ journal */

    @Test
    void masquerCommande_laSuiteNeVaPasAuJournal() {
        String m = MudraRegles.SUITE_MASQUEE;
        assertEquals("/porte creer Temple " + m, MudraRegles.masquerCommande("/porte creer Temple tigre serpent chien"));
        assertEquals("/porte signes D1 " + m, MudraRegles.masquerCommande("/porte signes D1 3 6 11"));
        assertEquals("/porte signes d1 " + m, MudraRegles.masquerCommande("/porte signes d1 tigre,serpent"));
        // alias, espace de noms, casse, accent, console (sans barre), espaces en trop
        assertEquals("/portescellee creer Temple " + m, MudraRegles.masquerCommande("/portescellee creer Temple 1 2"));
        assertEquals("/shinobicore:porte signes D2 " + m, MudraRegles.masquerCommande("/shinobicore:porte signes D2 rat boeuf"));
        assertEquals("/PORTE CREER Temple " + m, MudraRegles.masquerCommande("/PORTE CREER Temple TIGER DOG"));
        assertEquals("/porte créer Temple " + m, MudraRegles.masquerCommande("/porte créer Temple tigre chien"));
        assertEquals("porte signes D1 " + m, MudraRegles.masquerCommande("porte signes D1 tigre chien"));
        assertEquals("/porte signes D1 " + m, MudraRegles.masquerCommande("  /porte   signes  D1   tigre   chien "));
        // identifiant oublié : le premier mot est déjà un signe, il ne reste pas en clair
        assertEquals("/porte signes " + m, MudraRegles.masquerCommande("/porte signes tigre serpent chien"));
        assertEquals("/porte signes " + m, MudraRegles.masquerCommande("/porte signes 3"));
        assertEquals("/porte signes " + m, MudraRegles.masquerCommande("/porte signes tigre,serpent chien"));
        // aucun signe ne reste dans la ligne masquée
        for (String ligne : List.of("/porte creer Temple tigre serpent chien", "/porte signes D1 tigre serpent chien")) {
            String masquee = MudraRegles.masquerCommande(ligne);
            for (String signe : List.of("tigre", "serpent", "chien")) assertFalse(masquee.contains(signe), masquee);
        }
        // le reste passe tel quel : autres sous-commandes, autres commandes, lignes sans suite
        for (String ligne : List.of("/porte liste", "/porte info D1", "/porte nom D1 Porte du temple", "/porte leve D1 3",
                "/porte creer Temple", "/porte signes D1", "/porte systeme on", "/plaque texte P1 signes tigre", "/me creer signes",
                "/porter creer Temple tigre", "", "/")) {
            assertEquals(ligne, MudraRegles.masquerCommande(ligne));
        }
        assertNull(MudraRegles.masquerCommande(null));
    }

    @Test
    void validerContenu_ordreDesRefus() {
        assertTrue(MudraRegles.validerContenu(0, 3, 3).contains("air"), "que de l'air : dit d'abord");
    }

    @Test
    void bornerLeve() {
        assertEquals(4, MudraRegles.bornerLeve(0, 4), "défaut : toute la hauteur");
        assertEquals(4, MudraRegles.bornerLeve(9, 4));
        assertEquals(2, MudraRegles.bornerLeve(2, 4));
        assertEquals(4, MudraRegles.bornerLeve(-3, 4));
        assertEquals(1, MudraRegles.bornerLeve(1, 1));
    }

    /* ================================================================ calendrier de la levée */

    @Test
    void levee_rangeeLibreQuandLeBasLaDepassee() {
        // dalle de 3 de haut levée de 3 en 60 ticks : une rangée toutes les 20 ticks
        assertEquals(20, MudraRegles.tickLiberation(0, 60, 3));
        assertEquals(40, MudraRegles.tickLiberation(1, 60, 3));
        assertEquals(60, MudraRegles.tickLiberation(2, 60, 3));
        assertEquals(-1, MudraRegles.tickLiberation(3, 60, 3), "au-dessus de la levée : reste en barrière");
        assertEquals(-1, MudraRegles.tickLiberation(-1, 60, 3));
        // arrondi au tick supérieur : jamais libre avant que la dalle soit passée
        assertEquals(9, MudraRegles.tickLiberation(0, 60, 7));
        assertEquals(18, MudraRegles.tickLiberation(1, 60, 7));
        assertEquals(60, MudraRegles.tickLiberation(6, 60, 7));
        // levée partielle : 2 blocs sur une dalle de 4, les rangées 2 et 3 gardent leurs barrières
        assertEquals(30, MudraRegles.tickLiberation(0, 60, 2));
        assertEquals(60, MudraRegles.tickLiberation(1, 60, 2));
        assertEquals(-1, MudraRegles.tickLiberation(2, 60, 2));
        assertEquals(-1, MudraRegles.tickLiberation(0, 60, 0));
    }

    @Test
    void levee_jamaisAvantLePassageDeLaDalle() {
        for (int leve = 1; leve <= 9; leve++) {
            for (int duree : new int[]{10, 37, 60, 400}) {
                int avant = 0;
                for (int r = 0; r < leve; r++) {
                    int t = MudraRegles.tickLiberation(r, duree, leve);
                    // au tick t, le bas de la dalle est à leve × t / duree : au moins r + 1
                    assertTrue((double) leve * t / duree >= r + 1 - 1e-9, "rangée " + r + " libérée trop tôt (levée " + leve + ")");
                    assertTrue(t >= avant, "de bas en haut");
                    assertTrue(t >= 1 && t <= duree);
                    avant = t;
                }
                assertEquals(duree, MudraRegles.tickLiberation(leve - 1, duree, leve), "la dernière rangée à la fin du mouvement");
            }
        }
    }

    @Test
    void descente_barrieresDeHautEnBasAvantLaDalle() {
        assertEquals(0, MudraRegles.tickPose(2, 60, 3), "la rangée du haut tout de suite");
        assertEquals(20, MudraRegles.tickPose(1, 60, 3));
        assertEquals(40, MudraRegles.tickPose(0, 60, 3));
        assertEquals(-1, MudraRegles.tickPose(3, 60, 3));
        for (int leve = 1; leve <= 9; leve++) {
            for (int duree : new int[]{10, 37, 60, 400}) {
                int avant = duree;
                for (int r = 0; r < leve; r++) {
                    int t = MudraRegles.tickPose(r, duree, leve);
                    // au tick t de la descente, le bas de la dalle est à leve × (1 - t / duree) : pas encore sous r + 1
                    assertTrue(leve * (1.0 - (double) t / duree) >= r + 1 - 1e-9, "rangée " + r + " posée trop tard (levée " + leve + ")");
                    assertTrue(t <= avant && t >= 0 && t < duree, "de haut en bas");
                    avant = t;
                }
            }
        }
    }

    /* ================================================================ droits */

    @Test
    void droitsParGrade() {
        assertFalse(MudraRegles.peutLister(StaffGrades.NONE));
        assertTrue(MudraRegles.peutLister(StaffGrades.HELPER));
        assertFalse(MudraRegles.peutGerer(StaffGrades.HELPER), "le Helper ne voit pas la suite et ne crée pas");
        assertTrue(MudraRegles.peutGerer(StaffGrades.MODO));
        assertFalse(MudraRegles.peutSysteme(StaffGrades.MODO));
        assertTrue(MudraRegles.peutSysteme(StaffGrades.ADMIN));
        assertTrue(MudraRegles.peutSysteme(StaffGrades.OWNER));
    }

    private static MudraRegles.Contexte libre() {
        return new MudraRegles.Contexte(false, false, false, false, false, false, false, false, null);
    }

    @Test
    void refus_ordreEtRaisons() {
        assertNull(MudraRegles.refus(libre()));
        assertEquals("Indisponible.", MudraRegles.refus(new MudraRegles.Contexte(true, true, true, true, true, true, true, true, "x")),
                "système éteint : avant tout le reste");
        assertEquals("Ton personnage est mort.", MudraRegles.refus(new MudraRegles.Contexte(false, true, true, false, false, false, false, false, null)));
        assertEquals("Tu es à terre.", MudraRegles.refus(new MudraRegles.Contexte(false, false, true, true, false, false, false, false, null)));
        assertEquals("Tu es inconscient.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, true, false, false, false, false, null)));
        assertEquals("Impossible en spectateur.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, true, false, false, false, null)));
        assertEquals("Cinématique en cours.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, false, true, false, false, null)));
        assertEquals("Douane : aucune mécanique RP.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, false, false, true, false, null)));
        assertEquals("Hors jeu.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, false, false, false, true, "Entravé : pas de mudra.")),
                "le cadre avant la matrice");
        assertEquals("Entravé : pas de mudra.", MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, false, false, false, false, "Entravé : pas de mudra.")));
        assertNull(MudraRegles.refus(new MudraRegles.Contexte(false, false, false, false, false, false, false, false, " ")));
    }
}
