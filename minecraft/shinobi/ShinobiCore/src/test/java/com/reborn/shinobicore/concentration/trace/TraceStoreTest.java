package com.reborn.shinobicore.concentration.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mémoire des pas, découpe de la marche et message du canal (lot 4, étape C). */
class TraceStoreTest {

    private static final UUID A = new UUID(1, 1), B = new UUID(2, 2);

    private static Trace pas(TraceStore s, UUID m, double x, double z, long nee, int vie) {
        return s.ajouter(m, "world", x, 64, z, 0, 0, 100, nee, vie);
    }

    @Test
    void anneau_parTroncon_lePlusVieuxPart() {
        TraceStore s = new TraceStore(3, 100);
        Trace p1 = pas(s, A, 1, 1, 0, 600);
        pas(s, A, 2, 1, 1, 600);
        pas(s, A, 3, 1, 2, 600);
        pas(s, A, 4, 1, 3, 600);
        assertEquals(3, s.taille());
        assertTrue(p1.retiree);
        List<Trace> l = new ArrayList<>();
        s.autour("world", 0, 0, 20, B, 10, 100, l);
        assertEquals(3, l.size());
        assertFalse(l.contains(p1));
        // un autre tronçon a son propre anneau
        pas(s, A, 40, 1, 4, 600);
        assertEquals(4, s.taille());
    }

    @Test
    void plafondGlobal_lePlusVieuxDuServeurPart() {
        TraceStore s = new TraceStore(96, 4);
        Trace p1 = pas(s, A, 1, 1, 0, 600);
        pas(s, A, 40, 1, 1, 600);
        pas(s, A, 80, 1, 2, 600);
        pas(s, A, 120, 1, 3, 600);
        Trace p5 = pas(s, A, 160, 1, 4, 600);
        assertEquals(4, s.taille());
        assertTrue(p1.retiree);
        assertFalse(p5.retiree);
        List<Trace> l = new ArrayList<>();
        s.autour("world", 1, 1, 4, B, 5, 100, l);
        assertTrue(l.isEmpty(), "le tronçon du pas retiré est vide");
    }

    @Test
    void plafondGlobal_sauteLesPasDejaPartisDeLeurAnneau() {
        TraceStore s = new TraceStore(1, 3);
        pas(s, A, 1, 1, 0, 600);        // chassé par le suivant, dans le même tronçon
        pas(s, A, 2, 1, 1, 600);
        pas(s, A, 40, 1, 2, 600);
        pas(s, A, 80, 1, 3, 600);
        assertEquals(3, s.taille());
        pas(s, A, 120, 1, 4, 600);
        assertEquals(3, s.taille());
    }

    @Test
    void purge_oublieLesExpires() {
        TraceStore s = new TraceStore(96, 100);
        pas(s, A, 1, 1, 0, 10);
        Trace vivant = pas(s, A, 2, 1, 0, 600);
        pas(s, A, 40, 1, 0, 10);
        s.purger(11_000);
        assertEquals(1, s.taille());
        assertFalse(vivant.retiree);
        // le plafond global reste juste après une purge (les pas retirés ne comptent plus)
        TraceStore t = new TraceStore(96, 2);
        pas(t, A, 1, 1, 0, 10);
        pas(t, A, 2, 1, 0, 10);
        t.purger(20_000);
        assertEquals(0, t.taille());
        Trace n1 = pas(t, A, 1, 1, 21_000, 10), n2 = pas(t, A, 2, 1, 21_000, 10);
        assertFalse(n1.retiree);
        assertFalse(n2.retiree);
    }

    @Test
    void balayage_borne_repriseOuIlSEstArrete_toutFinitOublie() {
        TraceStore s = new TraceStore(96, 1_000);
        // un seul vivant, au milieu de pas expirés, dans plusieurs tronçons
        Trace vivant = null;
        for (int i = 0; i < 100; i++) {
            Trace t = pas(s, A, i * 3.0, 1, 0, i == 50 ? 600 : 10);
            if (i == 50) vivant = t;
        }
        s.balayer(11_000, 10);
        assertEquals(90, s.taille(), "dix examinés, dix oubliés : pas un de plus");
        s.balayer(11_000, 10);
        assertEquals(80, s.taille(), "et la suite reprend où il s'était arrêté");
        for (int i = 0; i < 20; i++) s.balayer(11_000, 10);
        assertEquals(1, s.taille());
        assertFalse(vivant.retiree);
        // un pas chassé de son anneau pendant le balayage ne casse pas la reprise
        TraceStore r = new TraceStore(2, 1_000);
        pas(r, A, 1, 1, 0, 10);
        pas(r, A, 2, 1, 0, 10);
        r.balayer(5_000, 1);
        pas(r, A, 3, 1, 0, 10);   // chasse le premier, le curseur est sur le deuxième
        pas(r, A, 4, 1, 0, 10);   // chasse le deuxième : le curseur passe au suivant
        r.balayer(11_000, 100);
        assertEquals(0, r.taille());
        pas(r, A, 5, 1, 20_000, 10);
        assertEquals(1, r.taille(), "la liste repart après s'être vidée");
    }

    /** « Une trace de pas reste » : quelqu'un qui fait les cent pas au même endroit ne chasse pas le pas d'un autre. */
    @Test
    void lesCentPas_neChassentPasLaTraverseeDUnAutre() {
        TraceStore s = new TraceStore(TraceRegles.MAX_TRONCON, TraceRegles.MAX_GLOBAL);
        Trace passage = pas(s, A, 1, 1, 0, 7200);
        // 4 min 30 de va-et-vient à 3,6 pas/s, dans le même tronçon
        for (int i = 0; i < 972; i++) pas(s, B, 2 + i % 12, 2 + i % 10, 300_000 + i * 277L, 7200);
        assertFalse(passage.retiree, "le pas de la traversée est toujours là");
        List<Trace> l = new ArrayList<>();
        s.autour("world", 8, 8, 28, B, 600_000, TraceRegles.MAX_ENVOI, l);
        assertTrue(l.contains(passage), "et il part vers le spectateur");
    }

    @Test
    void plafondGlobal_apresBalayage_lOrdreDArriveeTient() {
        TraceStore s = new TraceStore(96, 3);
        Trace p1 = pas(s, A, 1, 1, 0, 10);        // expire
        Trace p2 = pas(s, A, 40, 1, 0, 600);
        Trace p3 = pas(s, A, 80, 1, 0, 600);
        s.purger(11_000);
        assertTrue(p1.retiree);
        Trace p4 = pas(s, A, 120, 1, 12_000, 600);
        Trace p5 = pas(s, A, 160, 1, 13_000, 600);
        assertEquals(3, s.taille());
        assertTrue(p2.retiree, "le plus vieux encore là part");
        assertFalse(p3.retiree || p4.retiree || p5.retiree);
    }

    @Test
    void autour_pasLesSiens_niExpires_niHorsRayon_lesPlusProchesDAbord() {
        TraceStore s = new TraceStore(96, 100);
        Trace autre = pas(s, A, 5, 0, 0, 600);
        pas(s, B, 6, 0, 0, 600);                // les miens
        pas(s, A, 7, 0, 0, 1);                  // expiré
        pas(s, A, 40, 0, 0, 600);               // trop loin
        Trace loin = pas(s, A, 20, 0, 0, 600);
        List<Trace> l = new ArrayList<>();
        s.autour("world", 0, 0, 28, B, 5_000, 100, l);
        assertEquals(2, l.size());
        assertTrue(l.contains(autre) && l.contains(loin));
        s.autour("world", 0, 0, 28, B, 5_000, 1, l);
        assertEquals(List.of(autre), l);
        s.autour("autre_monde", 0, 0, 28, B, 5_000, 100, l);
        assertTrue(l.isEmpty());
    }

    @Test
    void autour_prendLesTronconsNegatifs() {
        TraceStore s = new TraceStore(96, 100);
        Trace p = pas(s, A, -20, -30, 0, 600);
        List<Trace> l = new ArrayList<>();
        s.autour("world", -25, -25, 10, B, 1, 100, l);
        assertEquals(List.of(p), l);
    }

    @Test
    void marche_unPasTousLesEspacements_surPlusieursReleves() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        assertEquals(0, m.avancer("w", 0, 0, 1.2, o), "le premier relevé amorce");
        assertEquals(0, m.avancer("w", 0, 0.5, 1.2, o));
        assertEquals(0, m.avancer("w", 0, 1.0, 1.2, o));
        assertEquals(1, m.avancer("w", 0, 1.5, 1.2, o));
        assertEquals(0, o[0], 1e-9);
        assertEquals(1.2, o[1], 1e-9);
        assertEquals(0, o[2], 1e-9, "cap vers +z");
        // un long relevé pose plusieurs pas le long du segment
        assertEquals(2, m.avancer("w", 0, 4.0, 1.2, o));
        assertEquals(2.4, o[1], 1e-9);
        assertEquals(3.6, o[4], 1e-9);
    }

    @Test
    void marche_immobile_saut_monde_coupure() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 1.2, o);
        assertEquals(0, m.avancer("w", 0, 0, 1.2, o), "immobile");
        assertEquals(0, m.avancer("w", 0, 7, 1.2, o), "téléportation : rien de tracé");
        assertEquals(0, m.avancer("w", 0, 8, 1.2, o), "et la distance repart de zéro");
        assertEquals(0, m.avancer("nether", 0, 9, 1.2, o), "autre monde : amorce");
        m.couper();
        assertEquals(0, m.avancer("nether", 0, 12, 1.2, o), "après une coupure : amorce, pas de ligne à travers le saut");
    }

    @Test
    void marche_courirEnSautant_unPasAChaqueAtterrissage() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 2.0, o);
        // des bonds de 3,5 blocs : jamais deux relevés au sol de suite, un pas à chaque retombée
        double z = 0;
        for (int i = 0; i < 5; i++) {
            m.enLair();
            z += 3.5;
            assertEquals(1, m.avancer("w", 0, z, 2.0, o), "bond " + i);
            assertEquals(z, o[1], 1e-9, "le pas est là où il retombe");
            assertEquals(0, o[2], 1e-9, "cap vers +z");
        }
    }

    @Test
    void marche_bondPlusLongQuUnReleve_nestPasUneTeleportation() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 1.2, o);
        m.enLair();
        assertEquals(1, m.avancer("w", 0, 9, 1.2, o), "un seul pas, pas de ligne en l'air");
        assertEquals(9, o[1], 1e-9);
        assertEquals(0, m.avancer("w", 0, 9.5, 1.2, o), "la distance repart de la retombée");
        assertEquals(0, m.avancer("w", 0, 20, 1.2, o), "au sol, une téléportation ne trace toujours rien");
    }

    @Test
    void marche_sauterSurPlace_nePoseRien() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 1.2, o);
        for (int i = 0; i < 20; i++) {
            m.enLair();
            assertEquals(0, m.avancer("w", 0, 0.01 * i, 1.2, o), "sauter sur place n'use pas l'anneau du tronçon");
        }
    }

    @Test
    void marche_petitSaut_compteCommeDeLaMarche() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 1.2, o);
        m.avancer("w", 0, 1.0, 1.2, o);
        m.enLair();
        assertEquals(0, m.avancer("w", 0, 1.1, 1.2, o), "pas encore l'espacement");
        assertEquals(1, m.avancer("w", 0, 1.3, 1.2, o), "la marche reprend sans trou");
    }

    @Test
    void marche_uneExclusionEnLair_coupe() {
        TraceMarche m = new TraceMarche();
        double[] o = new double[12];
        m.avancer("w", 0, 0, 1.2, o);
        m.enLair();
        m.couper();
        assertEquals(0, m.avancer("w", 0, 3, 1.2, o), "amorce");
        assertEquals(0, m.avancer("w", 0, 3.1, 1.2, o));
    }

    @Test
    void marche_vitesseMesureeSurLaFenetre() {
        TraceMarche m = new TraceMarche();
        m.mesurer(0, 0, 0, true);
        m.mesurer(4, 0, 0.5, true);
        assertEquals(0, m.vitesse(), 1e-9, "fenêtre pas finie");
        m.mesurer(8, 0, 2.0, true);
        assertEquals(5.0, m.vitesse(), 1e-9, "2 blocs en 8 ticks");
        assertFalse(m.mesureEnSaut());
        m.mesurer(12, 0, 3.0, false);
        m.mesurer(16, 0, 4.0, true);
        assertTrue(m.mesureEnSaut(), "un relevé en l'air dans la fenêtre");
        m.mesurer(24, 0, 6.0, true);
        assertFalse(m.mesureEnSaut());
    }

    @Test
    void marche_lesPiedsAlternent() {
        TraceMarche m = new TraceMarche();
        boolean a = m.piedSuivant(), b = m.piedSuivant(), c = m.piedSuivant();
        assertTrue(a != b && a == c);
        assertEquals(3, m.rang());
    }

    @Test
    void protocole_compact_agePortee_sansNomNiUuid() {
        TraceStore s = new TraceStore(96, 100);
        Trace t = s.ajouter(A, "world", 10.5, 64.9375, -3.25, 123, TraceRegles.F_DROIT | TraceRegles.F_SPRINT, 112, 1_000, 600);
        String m = TraceProtocole.message(1, true, 10, 64, -4, List.of(t), List.of(7, 9), 241_000, u -> 4242);
        JsonObject o = JsonParser.parseString(m).getAsJsonObject();
        assertEquals("traces", o.get("t").getAsString());
        assertTrue(o.get("plein").getAsBoolean());
        JsonArray a = o.getAsJsonArray("a");
        assertEquals(TraceProtocole.CHAMPS, a.size());
        // un message vide « plein » : ce qu'envoie la coupure de concentration.traces.actif
        assertEquals(0, JsonParser.parseString(TraceProtocole.message(1, true, 0, 0, 0, List.of(), List.of(), 0, u -> 0))
            .getAsJsonObject().getAsJsonArray("a").size());
        assertEquals(t.id, a.get(0).getAsInt());
        assertEquals(4242, a.get(1).getAsInt());
        assertEquals(16, a.get(2).getAsInt(), "dx en 1/32 de bloc");
        assertEquals(30, a.get(3).getAsInt(), "le dessus d'un chemin (15/16) reste exact");
        assertEquals(24, a.get(4).getAsInt());
        assertEquals(123, a.get(5).getAsInt());
        assertEquals(3, a.get(6).getAsInt());
        assertEquals(112, a.get(7).getAsInt());
        assertEquals(240, a.get(8).getAsInt(), "âge en secondes");
        assertEquals(600, a.get(9).getAsInt());
        assertEquals(2, o.getAsJsonArray("r").size());
        assertFalse(m.contains(A.toString()), "jamais d'UUID");
        assertFalse(m.contains("world"), "ni de nom de monde");
        // 30 à 53 octets par pas : un différentiel de 100 pas tient en quelques Ko
        List<Trace> cent = new ArrayList<>();
        for (int i = 0; i < 100; i++) cent.add(s.ajouter(A, "world", 10 + i * 0.3, 64, i * 0.3, i, 1, 100, 1_000, 600));
        String d = TraceProtocole.message(1, false, 10, 64, 0, cent, List.of(), 2_000, u -> 4242);
        assertTrue(d.length() < 100 * 36, "taille : " + d.length());
        assertFalse(d.contains("plein"));
    }
}
