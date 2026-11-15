package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.technique.Mudra;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code mudras.yml} : aller-retour, interrupteur, écriture atomique, la plus récente gagne, fichier abîmé jamais écrasé. */
class PorteStoreTest {

    @TempDir Path tmp;

    private static PorteStore store(File f) { return new PorteStore(Logger.getAnonymousLogger(), f); }

    private static PorteScellee porte(PorteStore s, String nom, int x, Mudra... signes) {
        PorteScellee d = new PorteScellee(s.nouvelId());
        d.nom = nom;
        d.monde = "world";
        d.boite = new MudraRegles.Boite(x, 64, -30, 3, 4, 1);
        d.signes = List.of(signes);
        d.leve = 4;
        s.portes.put(d.id, d);
        return d;
    }

    private static final String PORTE = String.join("\n",
            "    nom: Porte",
            "    monde: world",
            "    x: 1",
            "    y: 64",
            "    z: 3",
            "    boite: [3, 4, 1]",
            "    signes: [TIGER, SNAKE]",
            "");

    @Test
    void allerRetour_complet() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        UUID staff = UUID.randomUUID();
        s.actif = true;

        PorteScellee a = porte(s, "Porte du temple: l'entrée", 12, Mudra.TIGER, Mudra.SNAKE, Mudra.DOG);
        a.leve = 3;
        a.creeLe = 1_763_100_000_000L;
        a.creePar = staff;
        a.creePseudo = "Omar";
        a.changer(staff, "Omar", 1_763_100_500_000L);

        PorteScellee b = porte(s, "Crypte", 40, Mudra.RAT, Mudra.RAT);
        b.ouverte = true;
        List<String> blocs = new ArrayList<>();
        for (int i = 0; i < b.boite.volume(); i++)
            blocs.add(i % 2 == 0 ? "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]" : "minecraft:air");
        b.blocs = List.copyOf(blocs);

        assertTrue(s.ecrire(s.serialiser(), 1));
        assertTrue(f.exists());
        assertFalse(tmp.resolve("mudras.yml.tmp").toFile().exists(), "le fichier temporaire ne doit pas rester");

        PorteStore r = store(f);
        r.charger();
        assertFalse(r.bloque());
        assertTrue(r.actif(), "l'interrupteur est gardé");
        assertEquals(2, r.portes.size());
        assertEquals(3, r.prochain);

        PorteScellee a2 = r.portes.get("D1");
        assertEquals("Porte du temple: l'entrée", a2.nom, "un « : » dans le nom est quoté à l'écriture");
        assertEquals("world", a2.monde);
        assertEquals(a.boite, a2.boite);
        assertEquals(List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG), a2.signes);
        assertEquals(3, a2.leve);
        assertEquals(staff, a2.creePar);
        assertEquals("Omar", a2.creePseudo);
        assertEquals(1_763_100_000_000L, a2.creeLe);
        assertEquals(1_763_100_500_000L, a2.modifLe);
        assertEquals(staff, a2.modifPar);
        assertFalse(a2.ouverte);
        assertTrue(a2.blocs.isEmpty(), "porte fermée : aucun bloc noté");

        PorteScellee b2 = r.portes.get("D2");
        assertTrue(b2.ouverte);
        assertEquals(blocs, b2.blocs, "les blocs d'une porte ouverte se relisent tels quels, dans l'ordre");
        assertEquals(List.of(Mudra.RAT, Mudra.RAT), b2.signes, "un signe peut se répéter");
        assertNull(b2.creePar);
        assertEquals(0, b2.modifLe);
    }

    @Test
    void interrupteur_livreEteint() {
        PorteStore s = store(tmp.resolve("absent.yml").toFile());
        s.charger();
        assertFalse(s.actif(), "fichier absent : éteint");
        assertFalse(s.bloque());
        assertTrue(s.charger("prochain: 2\n"), "clé absente : éteint");
        assertFalse(s.actif());
        assertTrue(s.charger("actif: true\nprochain: 2\n"));
        assertTrue(s.actif());
        assertTrue(s.charger("actif: false\n"));
        assertFalse(s.actif());
        // « oui », 1… : jamais allumé par accident, et le fichier n'est pas réécrit
        assertFalse(s.charger("actif: oui\nprochain: 2\n"));
        assertFalse(s.actif());
        assertTrue(s.bloque());
    }

    @Test
    void ecritureAncienne_sauteeApresUneRecente() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        assertTrue(s.ecrire("prochain: 9\n", 5));
        assertTrue(s.ecrire("prochain: 2\n", 4));          // sérialisée avant, arrivée après : sautée
        assertEquals("prochain: 9\n", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Le disque refuse l'écriture : {@code ecrire} rend faux (le service annule alors la création, la retouche ou le
     * retrait et le dit au staff), et cette écriture ratée ne compte pas comme faite : la même, rejouée, part.
     */
    @Test
    void ecritureImpossible_rendFauxEtNeComptePasCommeFaite() throws Exception {
        Path obstacle = tmp.resolve("dossier");
        Files.writeString(obstacle, "un fichier là où il faudrait un dossier");
        File f = obstacle.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        porte(s, "Porte", 12, Mudra.TIGER, Mudra.SNAKE);
        String yaml = s.serialiser();
        assertFalse(s.ecrire(yaml, 1), "écriture impossible : faux, jamais un succès annoncé");
        assertFalse(s.bloque(), "une panne d'écriture ne bloque pas le fichier : la suivante peut réussir");
        assertFalse(f.exists());
        Files.delete(obstacle);
        assertTrue(s.ecrire(yaml, 1), "même numéro : l'écriture ratée n'a pas été comptée comme faite");
        assertEquals(yaml, Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void lectureALaMain_nomsFrancaisEtLeveeParDefaut() {
        PorteStore s = store(tmp.resolve("m.yml").toFile());
        assertTrue(s.charger(String.join("\n", "actif: true", "prochain: 1", "portes:", "  D4:",
                "    nom: Porte_basse", "    monde: world", "    x: 1", "    y: 64", "    z: 3", "    boite: [2, 3, 1]",
                "    signes: [tigre, Lièvre, boar]", "")));
        PorteScellee d = s.portes.get("D4");
        assertEquals(List.of(Mudra.TIGER, Mudra.HARE, Mudra.BOAR), d.signes);
        assertEquals(3, d.leve, "levée absente : toute la hauteur de la dalle");
        assertEquals("Porte basse", d.nom);
        assertEquals("D5", s.nouvelId(), "le compteur repart au-dessus de tout identifiant vu");
        // levée hors bornes : ramenée à la hauteur
        assertTrue(s.charger(String.join("\n", "portes:", "  D1:", PORTE + "    leve: 40", "")));
        assertEquals(4, s.portes.get("D1").leve);
    }

    @Test
    void porteMalFormee_rejetee_ecrituresBloquees() {
        String[] mauvaises = {
                "    nom: a\n    x: 1\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER, SNAKE]\n",                      // monde absent
                "    monde: world\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER, SNAKE]\n",                        // x absent
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    signes: [TIGER, SNAKE]\n",                                    // boîte absente
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [10, 2, 1]\n    signes: [TIGER, SNAKE]\n",             // côté de 10
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [4, 2, 4]\n    signes: [TIGER, SNAKE]\n",              // épaisseur 4
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER]\n",                     // un seul signe
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER, LICORNE]\n",            // signe inconnu
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER, SNAKE]\n    ouverte: true\n", // ouverte sans blocs
                "    monde: world\n    x: 1\n    y: 2\n    z: 3\n    boite: [1, 2, 1]\n    signes: [TIGER, SNAKE]\n    ouverte: true\n"
                        + "    blocs: [\"minecraft:stone\"]\n",                                                                      // 1 bloc pour 2
        };
        for (String m : mauvaises) {
            PorteStore s = store(tmp.resolve("x.yml").toFile());
            boolean ok = s.charger("prochain: 3\nportes:\n  D1:\n" + m + "  D2:\n" + PORTE);
            assertFalse(ok, "une porte rejetée : le fichier n'est pas compris en entier\n" + m);
            assertTrue(s.bloque(), "écritures bloquées : D1 ne doit pas disparaître à la première sauvegarde");
            assertEquals(1, s.portes.size());
            assertTrue(s.portes.containsKey("D2"), "les portes valides se lisent quand même");
            assertEquals("D3", s.nouvelId());
        }
    }

    @Test
    void dallesQuiSeRecoupent_rejetee() {
        PorteStore s = store(tmp.resolve("x.yml").toFile());
        assertFalse(s.charger("portes:\n  D1:\n" + PORTE + "  D2:\n" + PORTE.replace("x: 1", "x: 3")),
                "deux dalles qui se recoupent : la seconde est rejetée");
        assertTrue(s.bloque());
        assertEquals(1, s.portes.size());
        assertTrue(s.charger("portes:\n  D1:\n" + PORTE + "  D2:\n" + PORTE.replace("x: 1", "x: 4")), "côte à côte : acceptées");
        assertTrue(s.charger("portes:\n  D1:\n" + PORTE + "  D2:\n" + PORTE.replace("monde: world", "monde: nether")),
                "même place dans un autre monde : acceptées");
    }

    @Test
    void lisibleMaisAbime_jamaisReecrit() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        String abime = "actif: true\nprochain: 4\nportes:\n  D1:\n" + PORTE + "  D2:\n    monde: world\n    signes: [TIGER]\n";
        Files.writeString(f.toPath(), abime, StandardCharsets.UTF_8);
        PorteStore s = store(f);
        s.charger();
        assertTrue(s.bloque());
        assertEquals(1, s.portes.size());
        assertTrue(s.actif(), "ce qui est lisible se lit, l'interrupteur compris");
        assertFalse(s.ecrire(s.serialiser(), 1), "écriture refusée : la levée d'une porte ne partirait pas");
        assertEquals(abime, Files.readString(f.toPath(), StandardCharsets.UTF_8), "jamais réécrit sans D2");
        File[] cote = tmp.toFile().listFiles((d, n) -> n.startsWith("mudras.yml.corrompu-"));
        assertNotNull(cote);
        assertEquals(1, cote.length);
    }

    @Test
    void sectionMalIndentee_ouCleInconnue_bloque() {
        PorteStore s = store(tmp.resolve("x.yml").toFile());
        // « portes » devenue une valeur, les portes remontées à la racine
        assertFalse(s.charger("prochain: 4\nportes: 3\nD1:\n  monde: world\n"));
        assertTrue(s.bloque());
        assertTrue(s.charger("actif: false\nprochain: 2\n# rien encore\n"), "fichier sans porte : rien à protéger");
        assertFalse(s.bloque());
    }

    @Test
    void fichierInvalide_misDeCote_ecrituresBloquees() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        // un « : » non quoté après une retouche à la main
        String abime = "prochain: 5\nportes:\n  D1:\n    nom: Porte: le temple\n      monde: [world\n";
        Files.writeString(f.toPath(), abime, StandardCharsets.UTF_8);
        PorteStore s = store(f);
        s.charger();
        assertTrue(s.bloque(), "un YAML invalide bloque les écritures");
        assertTrue(s.portes.isEmpty());
        assertFalse(s.actif());
        assertFalse(s.ecrire("prochain: 1\n", 1));
        assertEquals(abime, Files.readString(f.toPath(), StandardCharsets.UTF_8), "le fichier abîmé n'est jamais écrasé");
        File[] cote = tmp.toFile().listFiles((d, n) -> n.startsWith("mudras.yml.corrompu-"));
        assertNotNull(cote);
        assertEquals(1, cote.length, "une copie est mise de côté");
        // corrigé à la main puis rechargé : les écritures reprennent, le compteur ne redonne pas un id déjà vu
        Files.writeString(f.toPath(), "prochain: 1\nportes:\n  D7:\n" + PORTE, StandardCharsets.UTF_8);
        s.charger();
        assertFalse(s.bloque());
        assertEquals("D8", s.nouvelId());
    }

    @Test
    void rechargementRate_gardeLaMemoire() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        s.actif = true;
        porte(s, "Porte", 1, Mudra.TIGER, Mudra.SNAKE);
        assertTrue(s.ecrire(s.serialiser(), 1));
        // retouche à la main ratée, puis /sc reload
        Files.writeString(f.toPath(), "prochain: 2\nportes:\n  D1:\n    nom: a: b\n", StandardCharsets.UTF_8);
        s.recharger(s.serialiser(), 2);
        assertTrue(s.bloque());
        assertEquals(1, s.portes.size(), "l'état valide en mémoire est gardé");
        assertEquals(List.of(Mudra.TIGER, Mudra.SNAKE), s.portes.get("D1").signes);
        assertTrue(s.actif(), "l'interrupteur aussi");
    }

    @Test
    void rechargement_ecritureDeFondPerimee_sautee() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        porte(s, "Une", 1, Mudra.TIGER, Mudra.SNAKE);
        assertTrue(s.ecrire(s.serialiser(), 1));
        porte(s, "Deux", 20, Mudra.DOG, Mudra.BOAR);       // D2 créée…
        String s1 = s.serialiser();                         // …sérialisée, pas encore écrite
        s.recharger(s.serialiser(), 3);                     // /sc reload avant l'écriture
        assertEquals(2, s.portes.size(), "la porte toute neuve n'est pas perdue");
        s.ecrire(s1, 2);                                    // l'écriture arrive après : sautée
        assertEquals("D3", s.nouvelId(), "l'identifiant D2 n'est jamais redonné");
        PorteStore r = store(f);
        r.charger();
        assertEquals(2, r.portes.size());
    }

    @Test
    void rechargement_retoucheAMainGagne() throws Exception {
        File f = tmp.resolve("mudras.yml").toFile();
        PorteStore s = store(f);
        PorteScellee a = porte(s, "Avant", 1, Mudra.TIGER, Mudra.SNAKE);
        assertTrue(s.ecrire(s.serialiser(), 1));
        String avant = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String main = avant.replace("nom: Avant", "nom: Corrigée à la main").replace("actif: false", "actif: true");
        org.junit.jupiter.api.Assertions.assertNotEquals(avant, main, "la retouche change bien le fichier");
        Files.writeString(f.toPath(), main, StandardCharsets.UTF_8);
        a.leve = 2;                                         // un changement en mémoire, non écrit
        s.recharger(s.serialiser(), 2);
        assertFalse(s.bloque());
        assertEquals("Corrigée à la main", s.portes.get("D1").nom, "la retouche est relue, pas écrasée");
        assertTrue(s.actif(), "l'interrupteur retouché à la main est relu");
        assertEquals(main, Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void compteur_neRedescendJamais() {
        PorteStore s = store(tmp.resolve("x.yml").toFile());
        // prochain abîmé (1) alors que DZ existe : le prochain identifiant passe au-dessus
        assertTrue(s.charger("prochain: 1\nportes:\n  DZ:\n" + PORTE));
        assertEquals("D10", s.nouvelId());
        assertEquals("D11", s.nouvelId());
        s.portes.clear();
        assertEquals("D12", s.nouvelId(), "une porte retirée ne rend pas son identifiant");
    }
}
