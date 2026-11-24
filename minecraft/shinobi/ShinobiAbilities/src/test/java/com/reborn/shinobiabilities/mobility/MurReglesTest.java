package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.mobility.MurRegles.Action;
import com.reborn.shinobiabilities.mobility.MurRegles.Cadence;
import com.reborn.shinobiabilities.mobility.MurRegles.CompteurRefus;
import com.reborn.shinobiabilities.mobility.MurRegles.Compteurs;
import com.reborn.shinobiabilities.mobility.MurRegles.Demande;
import com.reborn.shinobiabilities.mobility.MurRegles.Face;
import com.reborn.shinobiabilities.mobility.MurRegles.LecteurBlocs;
import com.reborn.shinobiabilities.mobility.MurRegles.Raison;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles pures de l'essai de la marche sur les murs (lot 5, étape C0). Les messages sont le contrat avec le mod
 * ({@code reborn-hud}, {@code mur/}) : si une chaîne de ce fichier bouge, le mod doit bouger avec.
 */
class MurReglesTest {

    private static final List<double[]> CUBE = List.of(new double[] {0, 0, 0, 1, 1, 1});
    private static final List<double[]> DALLE_BASSE = List.of(new double[] {0, 0, 0, 1, 0.5, 1});
    private static final List<double[]> DALLE_HAUTE = List.of(new double[] {0, 0.5, 0, 1, 1, 1});
    /** Escalier dont le dos (la partie haute) est du côté des x qui baissent. */
    private static final List<double[]> ESCALIER_DOS_OUEST = List.of(
            new double[] {0, 0, 0, 1, 0.5, 1}, new double[] {0, 0.5, 0, 0.5, 1, 1});
    private static final List<double[]> BARRIERE = List.of(new double[] {0.375, 0, 0.375, 0.625, 1.5, 0.625});

    /** Un monde de quelques blocs, clé {@code x,y,z}. */
    private static final class Monde implements LecteurBlocs {
        private final Map<String, List<double[]>> blocs = new HashMap<>();
        int lectures;

        Monde bloc(int x, int y, int z, List<double[]> boites) {
            blocs.put(x + "," + y + "," + z, boites);
            return this;
        }

        @Override
        public Collection<double[]> boites(int x, int y, int z) {
            lectures++;
            return blocs.get(x + "," + y + "," + z);
        }
    }

    private static String texte(byte[] octets) { return new String(octets, StandardCharsets.UTF_8); }

    /* ================================================================ protocole */

    @Test
    void messagesDuServeurAuFormatDuCahier() {
        assertEquals("{\"v\":1,\"t\":\"permis\",\"on\":true}", texte(MurRegles.permis(true)));
        assertEquals("{\"v\":1,\"t\":\"permis\",\"on\":false}", texte(MurRegles.permis(false)));
        assertEquals("{\"v\":1,\"t\":\"etat\",\"e\":4182,\"f\":\"E\"}", texte(MurRegles.etat(4182, Face.E)));
        assertEquals("{\"v\":1,\"t\":\"etat\",\"e\":4182,\"f\":\"-\"}", texte(MurRegles.etat(4182, null)));
        assertEquals("{\"v\":1,\"t\":\"refus\",\"m\":\"Pas de mur plein derrière toi.\"}",
                texte(MurRegles.refus("Pas de mur plein derrière toi.")));
        // un texte libre ne casse jamais le JSON
        assertEquals("{\"v\":1,\"t\":\"refus\",\"m\":\"a \\\"b\\\"\\n\"}", texte(MurRegles.refus("a \"b\"\n")));
        assertEquals("{\"v\":1,\"t\":\"refus\",\"m\":\"\"}", texte(MurRegles.refus(null)));
    }

    @Test
    void demandesDuModLues() {
        assertEquals(new Demande(Action.ACCROCHE, Face.E, 0), MurRegles.lire("{\"v\":1,\"a\":\"accroche\",\"f\":\"E\"}"));
        assertEquals(new Demande(Action.FACE, Face.N, 0), MurRegles.lire("{\"v\":1,\"a\":\"face\",\"f\":\"N\"}"));
        assertEquals(new Demande(Action.LACHE, null, 3), MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":3}"));
        // l'ordre des champs est libre, les champs inconnus sont ignorés
        assertEquals(new Demande(Action.ACCROCHE, Face.W, 0),
                MurRegles.lire("{\"f\":\"W\",\"x\":[1,2],\"a\":\"accroche\",\"v\":1,\"pos\":{\"y\":64}}"));
        assertEquals(new Demande(Action.LACHE, null, 0), MurRegles.lire("{\"v\":1,\"a\":\"lache\"}"));
        assertEquals(new Demande(Action.ACCROCHE, Face.S, 0),
                MurRegles.lire("{\"v\":1,\"a\":\"accroche\",\"f\":\"S\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void demandesInvalidesJetees() {
        assertNull(MurRegles.lire((String) null));
        assertNull(MurRegles.lire(""));
        assertNull(MurRegles.lire("accroche"));
        assertNull(MurRegles.lire("[1]"));
        assertNull(MurRegles.lire("{\"v\":1"), "JSON coupé");
        assertNull(MurRegles.lire("{\"a\":\"accroche\",\"f\":\"E\"}"), "sans version");
        assertNull(MurRegles.lire("{\"v\":2,\"a\":\"accroche\",\"f\":\"E\"}"), "autre version");
        assertNull(MurRegles.lire("{\"v\":\"1\",\"a\":\"accroche\",\"f\":\"E\"}"), "version en texte");
        assertNull(MurRegles.lire("{\"v\":1,\"f\":\"E\"}"), "sans action");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"vole\",\"f\":\"E\"}"), "action inconnue");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"accroche\"}"), "accroche sans face");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"accroche\",\"f\":\"-\"}"), "« - » n'est pas une face à demander");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"face\",\"f\":\"e\"}"), "face en minuscule");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"face\",\"f\":\"NE\"}"), "diagonale");
        assertNull(MurRegles.lire("{\"v\":1,\"a\":\"face\",\"f\":3}"), "face en nombre");
        // un message du serveur renvoyé par un client n'est pas une demande
        assertNull(MurRegles.lire("{\"v\":1,\"t\":\"etat\",\"e\":4182,\"f\":\"E\"}"));
        assertNull(MurRegles.lire(new byte[0]));
        assertNull(MurRegles.lire((byte[]) null));
        byte[] long1 = ("{\"v\":1,\"a\":\"lache\",\"x\":\"" + "a".repeat(MurRegles.TAILLE_MAX) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertNull(MurRegles.lire(long1), "message trop long");
    }

    @Test
    void correctionsDuModBornees() {
        assertEquals(0, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":-4}").corrections());
        assertEquals(MurRegles.CORRECTIONS_MAX, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":2000000000}").corrections());
        assertEquals(0, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":\"beaucoup\"}").corrections());
        assertEquals(0, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":1.5}").corrections());
        assertEquals(0, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":1e30}").corrections());
        assertEquals(12, MurRegles.lire("{\"v\":1,\"a\":\"lache\",\"c\":12}").corrections());
    }

    /* ================================================================ faces */

    @Test
    void facesEtNormales() {
        assertEquals(Face.N, Face.de("N"));
        assertEquals(Face.S, Face.de("S"));
        assertEquals(Face.E, Face.de("E"));
        assertEquals(Face.W, Face.de("W"));
        assertNull(Face.de(null));
        assertNull(Face.de(""));
        assertNull(Face.de("-"));
        // Minecraft : est = +x, sud = +z ; la normale va du mur vers l'air
        assertArrayEquals(new int[] {1, 0}, new int[] {Face.E.nx, Face.E.nz});
        assertArrayEquals(new int[] {-1, 0}, new int[] {Face.W.nx, Face.W.nz});
        assertArrayEquals(new int[] {0, 1}, new int[] {Face.S.nx, Face.S.nz});
        assertArrayEquals(new int[] {0, -1}, new int[] {Face.N.nx, Face.N.nz});
        for (Face f : Face.values()) assertEquals(1, Math.abs(f.nx) + Math.abs(f.nz), "normale cardinale : " + f);
    }

    /* ================================================================ contrôle de paroi */

    @Test
    void faceEstLeMurEstALOuest() {
        // mur plein en x = 4 (sa face est tournée vers l'est, en x = 5) ; le joueur est à l'est du mur
        Monde m = new Monde().bloc(4, 64, 0, CUBE);
        assertTrue(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE), "debout contre le mur");
        assertTrue(MurRegles.murDerriere(m, 5.9, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "couché, P à 0,9");
        // le même mur n'est derrière personne pour les trois autres faces
        assertFalse(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.W, MurRegles.PORTEE_MARCHE));
        assertFalse(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.N, MurRegles.PORTEE_MARCHE));
        assertFalse(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.S, MurRegles.PORTEE_MARCHE));
    }

    @Test
    void lesQuatreFaces() {
        // un pilier plein en (0, 64, 0) : on s'y tient à 0,9 de chaque côté
        Monde m = new Monde().bloc(0, 64, 0, CUBE);
        assertTrue(MurRegles.murDerriere(m, 1.9, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE));
        assertTrue(MurRegles.murDerriere(m, -0.9, 64.3, 0.5, Face.W, MurRegles.PORTEE_MARCHE));
        assertTrue(MurRegles.murDerriere(m, 0.5, 64.3, 1.9, Face.S, MurRegles.PORTEE_MARCHE));
        assertTrue(MurRegles.murDerriere(m, 0.5, 64.3, -0.9, Face.N, MurRegles.PORTEE_MARCHE));
        // du mauvais côté du pilier (le mur serait devant), rien
        assertFalse(MurRegles.murDerriere(m, -0.9, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE));
        assertFalse(MurRegles.murDerriere(m, 0.5, 64.3, 1.9, Face.N, MurRegles.PORTEE_MARCHE));
    }

    @Test
    void porteesDeLAccrocheEtDeLaMarche() {
        Monde m = new Monde().bloc(4, 64, 0, CUBE);
        // la face du mur est en x = 5
        assertTrue(MurRegles.murDerriere(m, 6.3, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE), "1,3 : dernier pas permis");
        assertFalse(MurRegles.murDerriere(m, 6.35, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE), "1,35 : trop loin");
        assertTrue(MurRegles.murDerriere(m, 6.35, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "1,35 en marche");
        assertTrue(MurRegles.murDerriere(m, 6.5, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "1,5 : dernier pas permis");
        assertFalse(MurRegles.murDerriere(m, 6.55, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "1,55 : le serveur lâche");
        // collé à la face (0) : toujours derrière
        assertTrue(MurRegles.murDerriere(m, 5.0, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE));
        // même chose du côté des coordonnées qui montent (face ouest d'un mur en x = 4 : elle est en x = 4)
        assertTrue(MurRegles.murDerriere(m, 2.7, 64.3, 0.5, Face.W, MurRegles.PORTEE_ACCROCHE));
        assertFalse(MurRegles.murDerriere(m, 2.65, 64.3, 0.5, Face.W, MurRegles.PORTEE_ACCROCHE));
        assertTrue(MurRegles.murDerriere(m, 2.5, 64.3, 0.5, Face.W, MurRegles.PORTEE_MARCHE));
        assertFalse(MurRegles.murDerriere(m, 2.45, 64.3, 0.5, Face.W, MurRegles.PORTEE_MARCHE));
    }

    @Test
    void coordonneesNegatives() {
        Monde m = new Monde().bloc(-12, 70, -31, CUBE);
        // face est du bloc x = -12 : elle est en x = -11
        assertTrue(MurRegles.murDerriere(m, -10.1, 70.3, -30.4, Face.E, MurRegles.PORTEE_MARCHE));
        // face sud du bloc z = -31 : elle est en z = -30
        assertTrue(MurRegles.murDerriere(m, -11.5, 70.3, -29.1, Face.S, MurRegles.PORTEE_MARCHE));
        // face nord du bloc z = -31 : elle est en z = -31
        assertTrue(MurRegles.murDerriere(m, -11.5, 70.3, -31.9, Face.N, MurRegles.PORTEE_MARCHE));
        assertFalse(MurRegles.murDerriere(m, -11.5, 70.3, -33.0, Face.N, MurRegles.PORTEE_MARCHE));
    }

    @Test
    void laHauteurEtLaColonneLuesSontCellesDuPoint() {
        Monde m = new Monde().bloc(4, 64, 0, CUBE);
        assertFalse(MurRegles.murDerriere(m, 5.9, 65.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "un rang plus haut : pas de mur");
        assertFalse(MurRegles.murDerriere(m, 5.9, 63.9, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "un rang plus bas");
        assertFalse(MurRegles.murDerriere(m, 5.9, 64.3, 1.2, Face.E, MurRegles.PORTEE_MARCHE), "une colonne à côté : bord du mur");
        // accroche d'un joueur debout en y = 64 : pieds (cube) puis torse
        Monde haut = new Monde().bloc(4, 65, 0, CUBE);
        assertFalse(MurRegles.murDerriere(haut, 5.3, 64 + MurRegles.HAUTEUR_CUBE, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE));
        assertFalse(MurRegles.murDerriere(haut, 5.3, 64 + MurRegles.HAUTEUR_TORSE, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE),
                "torse d'un joueur posé sur un bloc entier : même rang que ses pieds");
        assertTrue(MurRegles.murDerriere(haut, 5.3, 64.5 + MurRegles.HAUTEUR_TORSE, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE),
                "posé sur une dalle, son torse est au rang du dessus");
    }

    @Test
    void auPlusDeuxBlocsLusParControle() {
        Monde vide = new Monde();
        assertFalse(MurRegles.murDerriere(vide, 5.9, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE));
        assertTrue(vide.lectures <= 2, "lectures : " + vide.lectures);
        List<int[]> cols = MurRegles.blocsDerriere(5.9, 0.5, Face.E, MurRegles.PORTEE_MARCHE);
        assertEquals(1, cols.size(), "à 0,9 d'une face, la suivante est à 1,9 : hors de portée");
        assertArrayEquals(new int[] {4, 0}, cols.get(0));
        // debout contre un mur : deux colonnes à portée, la plus proche d'abord
        cols = MurRegles.blocsDerriere(5.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE);
        assertEquals(2, cols.size());
        assertArrayEquals(new int[] {4, 0}, cols.get(0));
        assertArrayEquals(new int[] {3, 0}, cols.get(1));
        // face nord : le mur est du côté des z qui montent
        cols = MurRegles.blocsDerriere(0.5, 2.7, Face.N, MurRegles.PORTEE_ACCROCHE);
        assertEquals(2, cols.size());
        assertArrayEquals(new int[] {0, 3}, cols.get(0));
        assertArrayEquals(new int[] {0, 4}, cols.get(1));
    }

    @Test
    void unBlocNonPleinNEstPasUnMur() {
        for (List<double[]> forme : List.of(DALLE_BASSE, DALLE_HAUTE, BARRIERE, List.<double[]>of())) {
            Monde m = new Monde().bloc(4, 64, 0, forme);
            assertFalse(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE));
        }
        // un bloc non plein devant un mur plein : c'est le mur plein, plus loin, qui compte s'il est à portée
        Monde m = new Monde().bloc(4, 64, 0, BARRIERE).bloc(3, 64, 0, CUBE);
        assertTrue(MurRegles.murDerriere(m, 5.3, 64.3, 0.5, Face.E, MurRegles.PORTEE_ACCROCHE));
        assertFalse(MurRegles.murDerriere(m, 5.9, 64.3, 0.5, Face.E, MurRegles.PORTEE_MARCHE), "1,9 : hors de portée");
    }

    @Test
    void facePleine() {
        for (Face f : Face.values()) {
            assertTrue(MurRegles.facePleine(CUBE, f), "cube plein, face " + f);
            assertFalse(MurRegles.facePleine(DALLE_BASSE, f), "dalle, face " + f);
            assertFalse(MurRegles.facePleine(BARRIERE, f), "barrière, face " + f);
            assertFalse(MurRegles.facePleine(List.of(), f));
            assertFalse(MurRegles.facePleine(null, f));
        }
        // deux dalles empilées font une face pleine
        List<double[]> deuxDalles = List.of(DALLE_BASSE.get(0), DALLE_HAUTE.get(0));
        for (Face f : Face.values()) assertTrue(MurRegles.facePleine(deuxDalles, f), "deux dalles, face " + f);
        // escalier : seul son dos est plein
        assertTrue(MurRegles.facePleine(ESCALIER_DOS_OUEST, Face.W), "dos de l'escalier");
        assertFalse(MurRegles.facePleine(ESCALIER_DOS_OUEST, Face.E), "devant de l'escalier : la moitié basse seulement");
        assertFalse(MurRegles.facePleine(ESCALIER_DOS_OUEST, Face.N), "flanc de l'escalier");
        assertFalse(MurRegles.facePleine(ESCALIER_DOS_OUEST, Face.S), "flanc de l'escalier");
        // un bloc en retrait de la face (15/16) ne la touche pas
        List<double[]> retrait = List.of(new double[] {0, 0, 0, 0.9375, 1, 1});
        assertFalse(MurRegles.facePleine(retrait, Face.E));
        assertTrue(MurRegles.facePleine(retrait, Face.W));
        // une fente d'un seizième suffit à ne plus être pleine
        List<double[]> fente = List.of(new double[] {0, 0, 0, 1, 1, 0.5}, new double[] {0, 0, 0.5625, 1, 1, 1});
        assertFalse(MurRegles.facePleine(fente, Face.E));
        // une boîte mal formée est ignorée
        assertFalse(MurRegles.facePleine(List.of(new double[] {0, 0, 0}), Face.E));
    }

    /* ================================================================ paquets refusés */

    @Test
    void unPaquetRefuseCompteUneFoisMemeAvecDeuxRaisons() {
        CompteurRefus c = new CompteurRefus();
        // Paper 26.2 : écart de position refusé, puis traversée de bloc pour le même paquet
        c.refuse(1000, 12.5, 70.0, -3.25, Raison.de("MOVED_WRONGLY"));
        c.refuse(1000, 12.5, 70.0, -3.25, Raison.de("CLIPPED_INTO_BLOCK"));
        assertEquals(1, c.paquets());
        assertEquals(1, c.raison(Raison.ECART));
        assertEquals(1, c.raison(Raison.BLOC));
        assertEquals(0, c.blocSeul(), "la traversée qui suit un écart n'est pas un jugement de plus");
        // paquet suivant, même position visée, un tick plus tard : un autre paquet
        c.refuse(1001, 12.5, 70.0, -3.25, Raison.ECART);
        c.refuse(1001, 12.5, 70.0, -3.25, Raison.BLOC);
        assertEquals(2, c.paquets());
        assertEquals(2, c.raison(Raison.ECART));
        assertEquals(2, c.raison(Raison.BLOC));
        assertEquals(0, c.blocSeul());
    }

    @Test
    void traverseeDeBlocSeule() {
        CompteurRefus c = new CompteurRefus();
        c.refuse(50, 1, 2, 3, Raison.BLOC);
        assertEquals(1, c.paquets());
        assertEquals(1, c.blocSeul());
        // le filet a accepté l'écart : le serveur juge alors la collision seule, et c'est elle qui refuse le paquet
        c.refuse(51, 1.2, 2, 3, Raison.BLOC);
        assertEquals(2, c.paquets());
        assertEquals(2, c.blocSeul());
        assertEquals(0, c.raison(Raison.ECART));
        // un écart refusé puis sa traversée : toujours deux traversées seules
        c.refuse(52, 1.4, 2, 3, Raison.ECART);
        c.refuse(52, 1.4, 2, 3, Raison.BLOC);
        assertEquals(3, c.paquets());
        assertEquals(2, c.blocSeul());
        assertEquals(3, c.raison(Raison.BLOC));
    }

    @Test
    void deuxPaquetsNeSontJamaisConfondus() {
        CompteurRefus c = new CompteurRefus();
        c.refuse(7, 1, 2, 3, Raison.ECART);
        c.refuse(7, 1, 2, 3.5, Raison.BLOC);       // même tick, autre position visée
        assertEquals(2, c.paquets());
        assertEquals(1, c.blocSeul());
        c.refuse(8, 1, 2, 3.5, Raison.VITESSE);    // même position, autre tick
        c.refuse(9, 4, 5, 6, Raison.CHUNK);
        assertEquals(4, c.paquets());
        assertEquals(1, c.raison(Raison.VITESSE));
        assertEquals(1, c.raison(Raison.CHUNK));
        // premier refus au tick 0, position 0 : il compte (rien n'a encore été vu)
        CompteurRefus zero = new CompteurRefus();
        zero.refuse(0, 0, 0, 0, Raison.BLOC);
        assertEquals(1, zero.paquets());
    }

    @Test
    void quatreRaisonsEtPasUneDePlus() {
        assertEquals(Raison.ECART, Raison.de("MOVED_WRONGLY"));
        assertEquals(Raison.BLOC, Raison.de("CLIPPED_INTO_BLOCK"));
        assertEquals(Raison.VITESSE, Raison.de("MOVED_TOO_QUICKLY"));
        assertEquals(Raison.CHUNK, Raison.de("MOVED_INTO_UNLOADED_CHUNK"));
        assertNull(Raison.de("AUTRE_CHOSE"));
        assertNull(Raison.de(null));
        assertEquals(4, Raison.values().length);
        // une raison inconnue du serveur ne compte rien et ne casse rien
        CompteurRefus c = new CompteurRefus();
        c.refuse(1, 0, 0, 0, Raison.de("AUTRE_CHOSE"));
        assertEquals(0, c.paquets());
    }

    /* ================================================================ cadence */

    @Test
    void cadenceDesMessages() {
        Cadence c = new Cadence(3, 1000);
        assertTrue(c.passe(0));
        assertTrue(c.passe(10));
        assertTrue(c.passe(20));
        assertFalse(c.passe(30), "quatrième message de la seconde");
        assertFalse(c.passe(999));
        assertTrue(c.passe(1000), "le premier est sorti de la fenêtre");
        assertFalse(c.passe(1005));
        assertTrue(c.passe(1010));
    }

    /* ================================================================ bilan */

    @Test
    void durees() {
        assertEquals("0 s", MurRegles.duree(0));
        assertEquals("0 s", MurRegles.duree(-5));
        assertEquals("47 s", MurRegles.duree(47_900));
        assertEquals("1 min 00 s", MurRegles.duree(60_000));
        assertEquals("2 min 04 s", MurRegles.duree(124_000));
        assertEquals("trop tôt pour le dire", MurRegles.parMinute(3, 9_999));
        assertEquals("1,0", MurRegles.parMinute(2, 120_000));
        assertEquals("0,0", MurRegles.parMinute(0, 60_000));
        assertEquals("4,5", MurRegles.parMinute(3, 40_000));
    }

    @Test
    void tempsAuMurEtFenetreDeSortie() {
        Compteurs c = new Compteurs();
        assertEquals(0, c.tempsMurMs(5_000));
        c.debutMarche(10_000);
        assertEquals(1, c.accroches);
        assertEquals(4_000, c.tempsMurMs(14_000), "marche en cours comprise");
        c.finMarche(70_000, 2_000);
        assertEquals(60_000, c.tempsMurMs(90_000));
        // au mur, puis pendant la sortie (le cube redevient debout), puis hors du mur
        assertEquals(c.refusMur, c.refus(true, 2_500));
        assertEquals(c.refusMur, c.refus(false, 2_000 + MurRegles.SORTIE_TICKS));
        assertEquals(c.refusHorsMur, c.refus(false, 2_001 + MurRegles.SORTIE_TICKS));
        // jamais monté : tout refus est hors du mur
        Compteurs neuf = new Compteurs();
        assertEquals(neuf.refusHorsMur, neuf.refus(false, 0));
        // remise à zéro en pleine marche : le temps repart de maintenant
        neuf.reprendreMarche(100_000);
        assertEquals(0, neuf.accroches);
        assertEquals(30_000, neuf.tempsMurMs(130_000));
    }

    @Test
    void bilanLisible() {
        Compteurs c = new Compteurs();
        c.debutMarche(0);
        c.finMarche(124_000, 100);
        c.accrochesRefusees = 1;
        c.dernierRefus = "Pas de mur plein derrière toi.";
        c.refusMur.refuse(10, 1, 2, 3, Raison.ECART);
        c.refusMur.refuse(10, 1, 2, 3, Raison.BLOC);
        c.refusHorsMur.refuse(900, 1, 2, 3, Raison.VITESSE);
        c.filet = 4;
        c.corrections = 2;
        c.coupsMelee = 27;
        c.projectiles = 1;
        c.degats = 5;
        c.lachersServeur = 1;
        c.dernierLacher = "Plus de mur plein derrière toi.";
        List<String> l = c.lignes(true, false, Face.E, 200_000);
        assertTrue(l.contains("Permis : allumé"), l.toString());
        assertTrue(l.contains("Filet : éteint"));
        assertTrue(l.contains("Au mur en ce moment : oui, face E"));
        assertTrue(l.contains("Accroches acceptées : 1"));
        assertTrue(l.contains("Accroches refusées : 1"));
        assertTrue(l.contains("Dernier refus : Pas de mur plein derrière toi."));
        assertTrue(l.contains("Temps passé au mur : 2 min 04 s"));
        assertTrue(l.contains("Expulsions du serveur pendant le mur : 0"));
        assertTrue(l.contains("Lâchers décidés par le serveur : 1"));
        assertTrue(l.contains("Dernier lâcher du serveur : Plus de mur plein derrière toi."));
        assertTrue(l.contains("Paquets de déplacement refusés au mur : 1"), "un paquet, deux raisons");
        assertTrue(l.contains("  raison « écart de position » : 1"));
        assertTrue(l.contains("  raison « traversée de bloc » : 1"));
        assertTrue(l.contains("  raison « trop rapide » : 0"));
        assertTrue(l.contains("  raison « zone non chargée » : 0"));
        assertTrue(l.contains("  traversée de bloc seule, sans écart : 0"));
        assertTrue(l.contains("Paquets acceptés par le filet : 4"));
        assertTrue(l.contains("Paquets refusés hors du mur, pour comparer : 1"));
        assertTrue(l.contains("Corrections de position vues par le mod : 2"));
        assertTrue(l.contains("Corrections par minute de mur : 1,0"));
        assertTrue(l.contains("Coups au corps à corps comptés par le serveur : 27"));
        assertTrue(l.contains("Essai 5, part sur 30 clics : 90 %"));
        assertTrue(l.contains("Clics reçus hors du mur, non comptés à l'essai 5 : 0"));
        assertTrue(l.contains("Projectiles qui ont touché : 1"));
        assertTrue(l.contains("Dégâts subis au mur, techniques comprises : 5"));
        assertTrue(l.contains("Pose de nage reposée par le serveur : 0"));
        assertTrue(l.contains("Ticks avec le vol autorisé au mur : 0"));
        for (String ligne : l) {
            assertNotNull(ligne);
            assertTrue(ligne.length() <= 64, "ligne trop longue pour le tchat : " + ligne);
            assertFalse(ligne.contains("\u2014"), "pas de tiret cadratin : " + ligne);
        }
        // sans rien : pas de ligne « dernier… », et le joueur n'est pas au mur
        List<String> vide = new Compteurs().lignes(false, true, null, 0);
        assertTrue(vide.contains("Permis : éteint"));
        assertTrue(vide.contains("Filet : allumé"));
        assertTrue(vide.contains("Au mur en ce moment : non"));
        assertTrue(vide.contains("Corrections par minute de mur : trop tôt pour le dire"));
        assertTrue(vide.stream().noneMatch(s -> s.startsWith("Derni")), "ni refus, ni expulsion, ni lâcher à citer");
        assertTrue(vide.stream().noneMatch(s -> s.contains("marche en cours")), "hors du mur : pas de mention");
        // marche en cours : le compte du mod n'arrive qu'au lâcher, le bilan le dit et ne calcule pas de taux
        Compteurs enCours = new Compteurs();
        enCours.debutMarche(0);
        List<String> m = enCours.lignes(true, false, Face.E, 120_000);
        assertTrue(m.contains("  (marche en cours non comptée, lâche le mur pour l'avoir)"), m.toString());
        assertTrue(m.contains("Corrections par minute de mur : trop tôt pour le dire"));
    }
}
