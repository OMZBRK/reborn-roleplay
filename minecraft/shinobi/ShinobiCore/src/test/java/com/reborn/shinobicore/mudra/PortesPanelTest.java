package com.reborn.shinobicore.mudra;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.technique.Mudra;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outil « Portes » du Poste de garde (16/11) : ce qui part vers le mod selon le grade (la suite jamais vers un Helper) et
 * la lecture des demandes venues d'un client qui peut être modifié.
 */
class PortesPanelTest {

    private static PorteScellee porte() {
        PorteScellee d = new PorteScellee("D3");
        d.nom = "Archives du clan";
        d.monde = "world";
        d.boite = new MudraRegles.Boite(10, 65, 57, 3, 2, 1);
        d.signes = List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG);
        d.leve = 2;
        d.creeLe = 1_763_228_400_000L;
        d.creePar = new UUID(0, 1);
        d.creePseudo = "Kaede";
        return d;
    }

    private static JsonObject lire(String json) { return JsonParser.parseString(json).getAsJsonObject(); }

    /* ================================================================ la suite selon le grade */

    @Test
    void porte_leModerateurRecoitLaSuite() {
        JsonObject x = PortesPanel.porteJson(porte(), true, MudraRegles.Etat.FERMEE, -1, 6.24, new JsonArray());
        assertEquals("[2,5,10]", x.get("signes").toString(), "ordinaux de la barre, dans l'ordre");
        assertEquals(3, x.get("n").getAsInt());
    }

    @Test
    void porte_leHelperNeRecoitQueLeNombreDeSignes() {
        PorteScellee d = porte();
        JsonObject x = PortesPanel.porteJson(d, false, MudraRegles.Etat.FERMEE, -1, 6.24, new JsonArray());
        assertFalse(x.has("signes"), "la clé n'existe pas : rien à lire dans le JSON d'un Helper");
        assertEquals(3, x.get("n").getAsInt());
        // le texte entier ne porte ni la suite ni un nom de signe
        String brut = x.toString();
        assertFalse(brut.contains("signes"));
        for (Mudra m : Mudra.values()) {
            assertFalse(brut.contains(m.name()), m.name());
            assertFalse(brut.contains(m.display()), m.display());
        }
        // tout le reste est le même que pour un modérateur
        JsonObject modo = PortesPanel.porteJson(d, true, MudraRegles.Etat.FERMEE, -1, 6.24, new JsonArray());
        modo.remove("signes");
        assertEquals(modo, x);
    }

    @Test
    void liste_droitsSelonLeGrade() {
        JsonObject helper = PortesPanel.listeJson(true, StaffGrades.HELPER, false, 64, null, new JsonArray());
        assertEquals("portes", helper.get("t").getAsString());
        assertTrue(helper.get("actif").getAsBoolean());
        assertFalse(helper.get("peutGerer").getAsBoolean());
        assertFalse(helper.get("peutSysteme").getAsBoolean());
        assertFalse(helper.get("bloque").getAsBoolean());
        assertEquals(64, helper.get("r").getAsInt());
        assertFalse(helper.has("dernier"), "aucune porte touchée : pas de clé");
        assertTrue(helper.get("l").isJsonArray());
        JsonObject modo = PortesPanel.listeJson(false, StaffGrades.MODO, true, 24, "D3", new JsonArray());
        assertFalse(modo.get("actif").getAsBoolean());
        assertTrue(modo.get("peutGerer").getAsBoolean());
        assertFalse(modo.get("peutSysteme").getAsBoolean(), "l'interrupteur reste aux admins");
        assertTrue(modo.get("bloque").getAsBoolean());
        assertEquals("D3", modo.get("dernier").getAsString());
        assertTrue(PortesPanel.listeJson(true, StaffGrades.ADMIN, false, 64, null, new JsonArray()).get("peutSysteme").getAsBoolean());
        assertTrue(PortesPanel.listeJson(true, StaffGrades.OWNER, false, 64, null, new JsonArray()).get("peutGerer").getAsBoolean());
    }

    /* ================================================================ une porte */

    @Test
    void porte_champsDuCahier() {
        JsonArray b = new JsonArray();
        b.add(PortesPanel.blocageJson("00000000-0000-0000-0000-000000000007", "Ren Hyuga", 79_001));
        JsonObject x = PortesPanel.porteJson(porte(), true, MudraRegles.Etat.FERMEE, 5, 6.24, b);
        assertEquals("D3", x.get("id").getAsString());
        assertEquals("Archives du clan", x.get("nom").getAsString());
        assertEquals("world", x.get("monde").getAsString());
        assertEquals(List.of(10, 65, 57, 3, 2, 1), List.of(x.get("x").getAsInt(), x.get("y").getAsInt(), x.get("z").getAsInt(),
                x.get("dx").getAsInt(), x.get("dy").getAsInt(), x.get("dz").getAsInt()));
        assertEquals(2, x.get("leve").getAsInt());
        assertEquals("fermee", x.get("etat").getAsString());
        assertEquals(-1, x.get("reste").getAsInt(), "une porte fermée n'a pas de redescente");
        assertEquals(6.2, x.get("dist").getAsDouble(), "une décimale");
        assertEquals("Kaede", x.getAsJsonObject("cree").get("par").getAsString());
        assertEquals(1_763_228_400_000L, x.getAsJsonObject("cree").get("le").getAsLong());
        assertEquals("", x.getAsJsonObject("modif").get("par").getAsString(), "jamais retouchée : présent, vide");
        assertEquals(0, x.getAsJsonObject("modif").get("le").getAsLong());
        JsonObject bl = x.getAsJsonArray("blocages").get(0).getAsJsonObject();
        assertEquals("00000000-0000-0000-0000-000000000007", bl.get("u").getAsString());
        assertEquals("Ren Hyuga", bl.get("nom").getAsString());
        assertEquals(80, bl.get("reste").getAsInt(), "secondes arrondies au-dessus");
    }

    @Test
    void porte_etatsResteEtDistance() {
        PorteScellee d = porte();
        d.changer(new UUID(0, 2), "Omar", 1_763_287_920_000L);
        JsonObject x = PortesPanel.porteJson(d, false, MudraRegles.Etat.LEVEE, 5, Double.POSITIVE_INFINITY, new JsonArray());
        assertEquals("levee", x.get("etat").getAsString());
        assertEquals(5, x.get("reste").getAsInt());
        assertEquals(-1, x.get("dist").getAsDouble(), "autre monde");
        assertEquals("Omar", x.getAsJsonObject("modif").get("par").getAsString());
        assertEquals(1_763_287_920_000L, x.getAsJsonObject("modif").get("le").getAsLong());
        assertEquals(-1, PortesPanel.porteJson(d, false, MudraRegles.Etat.LEVEE, -1, 0, new JsonArray()).get("reste").getAsInt(),
                "levée, passage occupé : échéance inconnue");
        assertEquals("leve", PortesPanel.porteJson(d, false, MudraRegles.Etat.LEVE, 3, 0, new JsonArray()).get("etat").getAsString());
        assertEquals(-1, PortesPanel.porteJson(d, false, MudraRegles.Etat.LEVE, 3, 0, new JsonArray()).get("reste").getAsInt(),
                "le reste ne vaut que pour une porte levée");
        assertEquals("redescend", PortesPanel.porteJson(d, false, MudraRegles.Etat.REDESCEND, -1, 0, new JsonArray()).get("etat").getAsString());
        assertEquals("remettre", PortesPanel.porteJson(d, false, MudraRegles.Etat.REMETTRE, -1, 0, new JsonArray()).get("etat").getAsString());
        assertEquals(0.0, PortesPanel.porteJson(d, false, MudraRegles.Etat.FERMEE, -1, 0.04, new JsonArray()).get("dist").getAsDouble());
    }

    @Test
    void blocage_auMoinsUneSeconde() {
        assertEquals(1, PortesPanel.blocageJson("u", "x", 1).get("reste").getAsInt());
        assertEquals(1, PortesPanel.blocageJson("u", "x", 1000).get("reste").getAsInt());
        assertEquals(2, PortesPanel.blocageJson("u", "x", 1001).get("reste").getAsInt());
        assertEquals(120, PortesPanel.blocageJson("u", "x", 120_000).get("reste").getAsInt());
    }

    /* ================================================================ lecture des demandes */

    @Test
    void boite_sixEntiers() {
        assertEquals(new MudraRegles.Boite(10, 65, 57, 3, 2, 1),
                PortesPanel.boite(lire("{\"boite\":{\"x\":10,\"y\":65,\"z\":57,\"dx\":3,\"dy\":2,\"dz\":1}}")));
        assertEquals(new MudraRegles.Boite(-12, -60, -3, 1, 1, 1),
                PortesPanel.boite(lire("{\"boite\":{\"x\":-12,\"y\":-60.0,\"z\":-3,\"dx\":1,\"dy\":1,\"dz\":1}}")), "négatifs, 60.0 = 60");
        assertNull(PortesPanel.boite(lire("{}")), "absente");
        assertNull(PortesPanel.boite(lire("{\"boite\":[10,65,57,3,2,1]}")), "un tableau n'est pas une boîte");
        assertNull(PortesPanel.boite(lire("{\"boite\":\"10 65 57\"}")));
        assertNull(PortesPanel.boite(lire("{\"boite\":{\"x\":10,\"y\":65,\"z\":57,\"dx\":3,\"dy\":2}}")), "un côté manque");
        assertNull(PortesPanel.boite(lire("{\"boite\":{\"x\":10.5,\"y\":65,\"z\":57,\"dx\":3,\"dy\":2,\"dz\":1}}")), "demi-bloc");
        assertNull(PortesPanel.boite(lire("{\"boite\":{\"x\":\"10\",\"y\":65,\"z\":57,\"dx\":3,\"dy\":2,\"dz\":1}}")), "texte");
        assertNull(PortesPanel.boite(lire("{\"boite\":{\"x\":1e300,\"y\":65,\"z\":57,\"dx\":3,\"dy\":2,\"dz\":1}}")), "démesuré");
        assertNull(PortesPanel.boite(lire("{\"boite\":{\"x\":null,\"y\":65,\"z\":57,\"dx\":3,\"dy\":2,\"dz\":1}}")));
        // les bornes de taille ne se jugent pas ici : la boîte se lit, MudraRegles.validerBoite la refusera
        MudraRegles.Boite grande = PortesPanel.boite(lire("{\"boite\":{\"x\":0,\"y\":0,\"z\":0,\"dx\":900,\"dy\":-2,\"dz\":0}}"));
        assertEquals(900, grande.dx());
        assertEquals("Boîte vide.", MudraRegles.validerBoite(grande.dx(), grande.dy(), grande.dz()));
    }

    @Test
    void suite_ordinauxDeLaGrille() {
        MudraRegles.Suite s = PortesPanel.suite(lire("{\"signes\":[2,5,10]}"));
        assertTrue(s.ok());
        assertEquals(List.of(Mudra.TIGER, Mudra.SNAKE, Mudra.DOG), s.signes());
        assertEquals("Il faut au moins 2 signes.", PortesPanel.suite(lire("{}")).erreur());
        assertEquals("Il faut au moins 2 signes.", PortesPanel.suite(lire("{\"signes\":\"2,5,10\"}")).erreur(), "pas un tableau");
        assertEquals("Il faut au moins 2 signes.", PortesPanel.suite(lire("{\"signes\":[4]}")).erreur());
        assertEquals("Signe inconnu.", PortesPanel.suite(lire("{\"signes\":[2,\"tigre\"]}")).erreur(), "des ordinaux, pas des noms");
        assertEquals("Signe inconnu.", PortesPanel.suite(lire("{\"signes\":[2,12]}")).erreur());
        assertEquals("Signe inconnu.", PortesPanel.suite(lire("{\"signes\":[2,2.5]}")).erreur());
        assertEquals("Signe inconnu.", PortesPanel.suite(lire("{\"signes\":[2,null,3]}")).erreur());
        assertEquals("Pas plus de 12 signes.", PortesPanel.suite(lire("{\"signes\":[0,1,2,3,4,5,6,7,8,9,10,11,0]}")).erreur());
        StringBuilder long_ = new StringBuilder("{\"signes\":[");
        for (int i = 0; i < 5000; i++) long_.append(i == 0 ? "" : ",").append(i % 12);
        assertEquals("Pas plus de 12 signes.", PortesPanel.suite(lire(long_.append("]}").toString())).erreur(), "tableau géant");
    }

    @Test
    void entier_exactSeulement() {
        assertEquals(3, PortesPanel.entier(lire("{\"v\":3}").get("v")));
        assertEquals(3, PortesPanel.entier(lire("{\"v\":3.0}").get("v")));
        assertEquals(-7, PortesPanel.entier(lire("{\"v\":-7}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":3.5}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":\"3\"}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":true}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":null}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":[3]}").get("v")));
        assertNull(PortesPanel.entier(lire("{\"v\":4000000000}").get("v")), "hors d'un int");
        assertNull(PortesPanel.entier(null));
    }

    @Test
    void id_borne() {
        assertEquals("D3", PortesPanel.id(lire("{\"id\":\" D3 \"}")));
        assertEquals("", PortesPanel.id(lire("{}")));
        assertEquals("", PortesPanel.id(lire("{\"id\":{\"x\":1}}")), "pas un texte");
        assertEquals(8, PortesPanel.id(lire("{\"id\":\"" + "D".repeat(4000) + "\"}")).length(), "jamais redit en entier dans un toast");
    }
}
