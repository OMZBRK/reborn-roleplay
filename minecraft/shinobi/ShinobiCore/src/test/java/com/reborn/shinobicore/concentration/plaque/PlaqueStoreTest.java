package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.outils.Narrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code plaques.yml} : aller-retour complet, écriture atomique, la plus récente gagne, lisibilité. */
class PlaqueStoreTest {

    @TempDir Path tmp;

    private static PlaqueStore store(File f) { return new PlaqueStore(Logger.getAnonymousLogger(), f); }

    @Test
    void allerRetour_toutesLesAncres() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        PlaqueStore s = store(f);
        UUID staff = UUID.randomUUID(), meuble = UUID.randomUUID();

        Plaque a = new Plaque(s.nouvelId());
        a.titre = "Stèle du premier gardien";
        a.segs = List.of(new Narrations.Segment("Ici repose ", ""), new Narrations.Segment("le premier gardien", "or"),
                new Narrations.Segment(".\nSilence.", ""));
        a.ancre = Ancre.bloc("world", 11, 64, -30, 3, 2, 1);
        a.portee = 8;
        a.rev = 3;
        a.creePar = staff;
        a.creePseudo = "Omar";
        a.creeLe = 1_762_376_580_000L;
        a.lectures = 37;
        s.plaques.put(a.id, a);

        Plaque b = new Plaque(s.nouvelId());
        b.segs = List.of(Narrations.Segment.blanc("Cette lanterne ne s'éteint jamais."));
        b.ancre = Ancre.entite("world", meuble, 40.5, 70, 8.5, 0.6, 1.8);
        b.temporaire = true;
        b.expire = 1_900_000_000_000L;
        b.supprimee = new Plaque.Suppression(1_762_400_000_000L, staff, "Omar", "doublon");
        s.plaques.put(b.id, b);

        Plaque c = new Plaque(s.nouvelId());
        c.segs = List.of(new Narrations.Segment("Signé d'une ombre", "ombre"));
        c.ancre = Ancre.objet();
        c.actif = false;
        s.plaques.put(c.id, c);

        s.ecrire(s.serialiser(), 1);
        assertTrue(f.exists());
        assertFalse(tmp.resolve("plaques.yml.tmp").toFile().exists(), "le fichier temporaire ne doit pas rester");

        PlaqueStore r = store(f);
        r.charger();
        assertEquals(3, r.plaques.size());
        assertEquals(4, r.prochain);
        Plaque a2 = r.plaques.get("P1");
        assertEquals("Stèle du premier gardien", a2.titre);
        assertEquals(a.segs, a2.segs);
        assertEquals(a.ancre, a2.ancre);
        assertEquals(8, a2.portee);
        assertEquals(3, a2.rev);
        assertEquals(staff, a2.creePar);
        assertEquals(37, a2.lectures);
        assertNull(a2.supprimee);
        assertEquals("Ici repose <or>le premier gardien</>.\nSilence.", a2.balise());

        Plaque b2 = r.plaques.get("P2");
        assertEquals(Ancre.Type.ENTITE, b2.ancre.type());
        assertEquals(meuble, b2.ancre.entite());
        assertEquals(40.5, b2.ancre.ex(), 1e-9);
        assertTrue(b2.temporaire);
        assertNotNull(b2.supprimee);
        assertEquals("doublon", b2.supprimee.motif());
        assertTrue(b2.dansCorbeille());

        Plaque c2 = r.plaques.get("P3");
        assertEquals(Ancre.Type.OBJET, c2.ancre.type());
        assertFalse(c2.actif);
        assertEquals("ombre", c2.segs.get(0).couleur());
    }

    @Test
    void ecritureAncienne_sauteeApresUneRecente() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        PlaqueStore s = store(f);
        s.ecrire("prochain: 9\n", 5);
        s.ecrire("prochain: 2\n", 4);          // sérialisée avant, arrivée après : sautée
        assertEquals("prochain: 9\n", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void lisible_selonEtat() {
        Plaque p = new Plaque("P1");
        p.ancre = Ancre.bloc("world", 0, 0, 0, 1, 1, 1);
        assertFalse(p.lisible(1000), "sans texte, rien à lire");
        p.segs = List.of(Narrations.Segment.blanc("x"));
        assertTrue(p.lisible(1000));
        p.expire = 900;
        assertFalse(p.lisible(1000), "expirée");
        p.expire = 0;
        p.actif = false;
        assertFalse(p.lisible(1000), "désactivée");
        p.actif = true;
        p.supprimee = new Plaque.Suppression(1, null, "x", "motif");
        assertFalse(p.lisible(1000), "à la corbeille");
    }

    @Test
    void plaqueMalFormee_rejetee_ecrituresBloquees() {
        PlaqueStore s = store(tmp.resolve("x.yml").toFile());
        boolean ok = s.charger("prochain: 3\nplaques:\n  P1:\n    segs: [[\"a\", \"\"]]\n  P2:\n    segs: [[\"b\", \"\"]]\n"
                + "    ancre: { type: bloc, monde: world, x: 1, y: 2, z: 3, boite: [1, 1, 1] }\n");
        assertFalse(ok, "une plaque rejetée : le fichier n'est pas compris en entier");
        assertTrue(s.bloque(), "écritures bloquées : P1 ne doit pas disparaître à la première sauvegarde");
        assertEquals(1, s.plaques.size());
        assertTrue(s.plaques.containsKey("P2"), "les plaques valides se lisent quand même");
        assertEquals("P3", s.nouvelId());
    }

    @Test
    void lisibleMaisAbime_jamaisReecrit() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        String bon = "    ancre: { type: bloc, monde: world, x: 1, y: 2, z: 3, boite: [1, 1, 1] }\n";
        // type inconnu, UUID d'entité invalide : YAML valide, plaques perdues si on réécrivait
        String abime = "prochain: 4\nplaques:\n  P1:\n    segs: [[\"a\", \"\"]]\n" + bon
                + "  P2:\n    segs: [[\"b\", \"\"]]\n    ancre: { type: blok, monde: world, x: 1, y: 2, z: 3 }\n"
                + "  P3:\n    segs: [[\"c\", \"\"]]\n    ancre: { type: entite, monde: world, entite: pas-un-uuid, x: 1, y: 2, z: 3 }\n";
        Files.writeString(f.toPath(), abime, StandardCharsets.UTF_8);
        PlaqueStore s = store(f);
        s.charger();
        assertTrue(s.bloque());
        assertEquals(1, s.plaques.size());
        s.ecrire(s.serialiser(), 1);
        assertEquals(abime, Files.readString(f.toPath(), StandardCharsets.UTF_8), "jamais réécrit sans P2 ni P3");
        File[] cote = tmp.toFile().listFiles((d, n) -> n.startsWith("plaques.yml.corrompu-"));
        assertNotNull(cote);
        assertEquals(1, cote.length);
    }

    @Test
    void sectionMalIndentee_ouCleInconnue_bloque() {
        PlaqueStore s = store(tmp.resolve("x.yml").toFile());
        // « plaques » devenue une valeur, les plaques remontées à la racine
        assertFalse(s.charger("prochain: 4\nplaques: 3\nP1:\n  segs: [[\"a\", \"\"]]\n"));
        assertTrue(s.bloque());
        assertTrue(s.charger("prochain: 2\n# rien encore\n"), "fichier sans plaque : rien à protéger");
        assertFalse(s.bloque());
    }

    @Test
    void rechargementRate_gardeLaMemoire() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        PlaqueStore s = store(f);
        Plaque a = new Plaque(s.nouvelId());
        a.segs = List.of(Narrations.Segment.blanc("x"));
        a.ancre = Ancre.bloc("world", 1, 2, 3, 1, 1, 1, "north");
        s.plaques.put(a.id, a);
        s.ecrire(s.serialiser(), 1);
        // retouche à la main ratée, puis /sc reload
        Files.writeString(f.toPath(), "prochain: 2\nplaques:\n  P1:\n    titre: a: b\n", StandardCharsets.UTF_8);
        s.recharger(s.serialiser(), 2);
        assertTrue(s.bloque());
        assertEquals(1, s.plaques.size(), "l'état valide en mémoire est gardé");
        assertEquals("north", s.plaques.get("P1").ancre.face());
    }

    @Test
    void rechargement_ecritureDeFondPerimee_sautee() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        PlaqueStore s = store(f);
        Plaque a = new Plaque(s.nouvelId());
        a.segs = List.of(Narrations.Segment.blanc("x"));
        a.ancre = Ancre.bloc("world", 1, 2, 3, 1, 1, 1);
        s.plaques.put(a.id, a);
        s.ecrire(s.serialiser(), 1);                 // S0 : P1
        Plaque b = new Plaque(s.nouvelId());          // P2 créée…
        b.segs = List.of(Narrations.Segment.blanc("y"));
        b.ancre = Ancre.bloc("world", 4, 5, 6, 1, 1, 1);
        s.plaques.put(b.id, b);
        String s1 = s.serialiser();                   // …sérialisée (tâche de fond pas encore écrite)
        s.recharger(s.serialiser(), 3);               // /sc reload avant l'écriture
        assertEquals(2, s.plaques.size(), "la plaque toute neuve n'est pas perdue");
        s.ecrire(s1, 2);                              // l'écriture de fond arrive après : sautée
        assertEquals("P3", s.nouvelId(), "l'identifiant P2 n'est jamais redonné");
        PlaqueStore r = store(f);
        r.charger();
        assertEquals(2, r.plaques.size());
    }

    @Test
    void rechargement_retoucheAMainGagne() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        PlaqueStore s = store(f);
        Plaque a = new Plaque(s.nouvelId());
        a.segs = List.of(Narrations.Segment.blanc("x"));
        a.ancre = Ancre.bloc("world", 1, 2, 3, 1, 1, 1);
        s.plaques.put(a.id, a);
        s.ecrire(s.serialiser(), 1);
        String avant = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String main = avant.replace("- x", "- corrigé à la main");
        org.junit.jupiter.api.Assertions.assertNotEquals(avant, main, "la retouche change bien le fichier");
        Files.writeString(f.toPath(), main, StandardCharsets.UTF_8);
        a.lectures = 5;                               // un changement en mémoire (compteur)
        s.recharger(s.serialiser(), 2);
        assertFalse(s.bloque());
        assertEquals("corrigé à la main", s.plaques.get("P1").segs.get(0).texte(), "la retouche est relue, pas écrasée");
        assertEquals(main, Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }


    @Test
    void fichierInvalide_misDeCote_ecrituresBloquees() throws Exception {
        File f = tmp.resolve("plaques.yml").toFile();
        // un « : » non quoté après une retouche à la main (comme pour ShinobiCore)
        String abime = "prochain: 5\nplaques:\n  P1:\n    titre: Stèle: le gardien\n      segs: [[\"a\"\n";
        Files.writeString(f.toPath(), abime, StandardCharsets.UTF_8);
        PlaqueStore s = store(f);
        s.charger();
        assertTrue(s.bloque(), "un YAML invalide bloque les écritures");
        assertTrue(s.plaques.isEmpty());
        s.ecrire("prochain: 1\n", 1);
        assertEquals(abime, Files.readString(f.toPath(), StandardCharsets.UTF_8), "le fichier abîmé n'est jamais écrasé");
        File[] cote = tmp.toFile().listFiles((d, n) -> n.startsWith("plaques.yml.corrompu-"));
        assertNotNull(cote);
        assertEquals(1, cote.length, "une copie est mise de côté");
        // corrigé à la main puis rechargé : les écritures reprennent, le compteur ne redonne pas un id déjà vu
        Files.writeString(f.toPath(), "prochain: 1\nplaques:\n  P7:\n    segs: [[\"b\", \"\"]]\n"
                + "    ancre: { type: bloc, monde: world, x: 1, y: 2, z: 3, boite: [1, 1, 1] }\n", StandardCharsets.UTF_8);
        s.charger();
        assertFalse(s.bloque());
        assertEquals("P8", s.nouvelId());
    }
}
