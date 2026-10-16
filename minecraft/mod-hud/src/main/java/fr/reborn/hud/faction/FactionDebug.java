package fr.reborn.hud.faction;

import com.google.gson.JsonObject;
import fr.reborn.hud.interaction.InteractionItem;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Banc de captures du volet Faction (maquettes v3 01 à 05 + invitation + Poste de garde), <b>inactif en production</b> :
 * ne fait rien sauf si {@code REBORN_FACTION_DEBUG} est posé (client de dev, monde solo). Le serveur est simulé par
 * {@link FactionDemo} (format exact du canal {@code reborn:faction}) ; têtes RP prêtées par {@link RebornSkins}.
 * {@code =1} : tous les cas en 1920×1080 à l'échelle GUI 2 ; {@code =<cas>,<cas>} : ces cas. Captures
 * {@code run/screenshots/faction_<nn>_<cas>.png}, puis le jeu se ferme.
 */
public final class FactionDebug {

    static final String[] CAS = {"lambda_village", "lambda_section", "chef_section", "inviter", "grades", "gestion",
            "creer_section", "anbu_grade", "anbu_kage", "annonces", "annonce_form", "exclure", "tenue", "charges",
            "invitation_hud", "menu_r", "staff_liste", "staff_fiche", "staff_creer", "echelle3_village", "petit_chef"};

    private record Shot(String nom, String cas, int fw, int fh, int echelle) { }

    private static final int STEP = 34;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int t = -1, echelleAvant = -1;
    private static boolean pret;

    private FactionDebug() {}

    static void init() {
        String v = System.getenv("REBORN_FACTION_DEBUG");
        if (v == null || v.isBlank()) return;
        List<String> only = "1".equals(v.trim()) ? List.of() : List.of(v.trim().split(","));
        for (int i = 0; i < CAS.length; i++) {
            String c = CAS[i];
            if (!only.isEmpty() && !only.contains(c)) continue;
            int fw = 1920, fh = 1080, e = 2;
            if (c.startsWith("echelle3")) e = 3;
            if (c.startsWith("petit")) { fw = 1366; fh = 768; }
            SHOTS.add(new Shot(String.format("faction_%02d_%s", i + 1, c), c, fw, fh, e));
        }
        ClientTickEvents.END_CLIENT_TICK.register(FactionDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 1000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
            echelleAvant = mc.options.guiScale().get();
            FactionClient.demo = true;
            skins(mc);
            pret = true;
        }
        if (t < 160 || !pret) return;
        int i = (t - 160) / STEP, k = (t - 160) % STEP;
        if (i >= SHOTS.size()) {
            if (k == 2) {
                mc.setScreenAndShow(null);
                InteractionMode.INSTANCE.debugClose();
                fr.reborn.hud.staff.GardeFactionsDebug.fermer();
                FactionClient.demo = false;
                FactionClient.effacer();
                RebornSkins.clear(mc.player.getUUID());
                fr.reborn.hud.runtime.RebornSession.debugForceRp = false;
                if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
            }
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        if (k == 0) {
            mc.setScreenAndShow(null);
            InteractionMode.INSTANCE.debugClose();
            FactionClient.effacer();
            FactionClient.effacerToasts();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) mc.getWindow().setWindowed(s.fw(), s.fh());
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 4) { mc.gui.hud.getChat().clearMessages(false); setup(mc, s.cas()); }
        if (k == 12) suite(mc, s.cas());
        if (k == STEP - 3) Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static FactionScreen ouvrir(Minecraft mc, String qui, String tab) {
        FactionDemo.qui = qui;
        FactionDemo.handle(json("open"));
        if (!(mc.gui.screen() instanceof FactionScreen f)) return null;
        f.tab = tab;
        return f;
    }

    private static JsonObject json(String a) { JsonObject o = new JsonObject(); o.addProperty("a", a); return o; }

    private static void setup(Minecraft mc, String cas) {
        switch (cas) {
            case "lambda_village", "echelle3_village" -> ouvrir(mc, "ren", "village");
            case "lambda_section" -> ouvrir(mc, "ren", "section");
            case "chef_section", "petit_chef" -> ouvrir(mc, "aki", "section");
            case "inviter" -> { FactionScreen f = ouvrir(mc, "aki", "section"); if (f != null) f.ouvrirSous("inviter"); }
            case "grades" -> { FactionScreen f = ouvrir(mc, "aki", "section"); if (f != null) f.ouvrirSous("grades"); }
            case "tenue" -> { FactionScreen f = ouvrir(mc, "aki", "section"); if (f != null) f.ouvrirSous("tenue"); }
            case "gestion" -> ouvrir(mc, "daisuke", "gestion");
            case "creer_section" -> { FactionScreen f = ouvrir(mc, "daisuke", "gestion"); if (f != null) f.ouvrirSous("creer"); }
            case "charges" -> { FactionScreen f = ouvrir(mc, "daisuke", "gestion"); if (f != null) f.ouvrirSous("charges"); }
            case "anbu_grade" -> ouvrir(mc, "hiroshi", "sections");
            case "anbu_kage" -> ouvrir(mc, "daisuke", "sections");
            case "annonces" -> ouvrir(mc, "ren", "annonces");
            case "annonce_form" -> ouvrir(mc, "daisuke", "annonces");
            case "exclure" -> ouvrir(mc, "aki", "section");
            case "invitation_hud" -> FactionClient.recevoir("{\"t\":\"invit\",\"de\":\"Aki Uchiha\",\"section\":\"Police\",\"insigne\":\"police\",\"s\":10}");
            case "menu_r" -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                JsonObject e = new JsonObject();
                e.addProperty("t", "etat");
                e.addProperty("section", "Police");
                e.addProperty("insigne", "police");
                e.addProperty("inviter", true);
                FactionClient.debugEtat(e);
            }
            case "staff_liste" -> fr.reborn.hud.staff.GardeFactionsDebug.ouvrir("liste");
            case "staff_fiche" -> fr.reborn.hud.staff.GardeFactionsDebug.ouvrir("fiche");
            case "staff_creer" -> fr.reborn.hud.staff.GardeFactionsDebug.ouvrir("creer");
            default -> { }
        }
    }

    private static void suite(Minecraft mc, String cas) {
        FactionScreen f = mc.gui.screen() instanceof FactionScreen x ? x : null;
        switch (cas) {
            case "chef_section", "petit_chef" -> {
                // survol de Kenta (2ᵉ ligne des membres) : « Gérer › »
                if (f != null) f.debugSouris = () -> new int[]{f.ix() + 40, f.vy0() + 2 + 27 + 7 + 11 + 15 + 7};
            }
            case "inviter" -> { if (f != null) f.pages.debugEnvoye("Invitation envoyée à Kazuki Uchiha · 30 s"); }
            case "grades" -> {
                if (f != null) {
                    f.pages.debugGrade("inspecteur");
                    // la fiche de Kenta, ouverte comme au clic sur sa ligne (encart de la maquette 04)
                    f.pages.debugFiche(1, f.vy0() + 60);
                }
            }
            case "creer_section" -> { if (f != null) f.pages.debugCreer("Brigade des archives", "missions", "n", "Nanami Yamanaka · Chūnin", "6"); }
            case "annonce_form" -> {
                if (f != null) f.pages.debugForm("annonce", null, null, "Publier une annonce", null,
                        "Une réunion aura lieu ce soir sur la place centrale à 20 h.");
            }
            case "exclure" -> {
                if (f != null) f.pages.debugForm("exclure", "kenta", "Kenta Inuzuka", "Exclure Kenta Inuzuka",
                        "Il ou elle quitte la section ; une ligne lui est envoyée et tout va au journal de la section.", "Absences répétées au service");
            }
            case "menu_r" -> {
                List<InteractionItem> items = InteractionMenus.forPlayer("Ren_Hyuga");
                InteractionMode.INSTANCE.debugOpen("Ren_Hyuga", items, 0);
            }
            default -> { }
        }
    }

    /* ---------------------------------------------------------------- têtes RP (maquettes) */

    private static void skins(Minecraft mc) {
        Object[][] l = {
                {FactionDemo.AKI, false, 3, "Cheveux_Sasuke", 0xFF1A1414, "Complet_Uchiha"},
                {FactionDemo.KENTA, false, 7, "Cheveux_Base", 0xFF6A3A22, "tenueh3"},
                {FactionDemo.MEI, true, 2, "Cheveux_Femme", 0xFF2A2440, "Complet_Femme1"},
                {FactionDemo.TARO, false, 6, "Cheveux_Base", 0xFF4A3020, "tenueh7"},
                {FactionDemo.RENJI, false, 4, "Cheveux_4", 0xFF3A2A1A, "tenueh12"},
                {FactionDemo.SORA, false, 2, "Cheveux_4", 0xFFD8D8D8, "tenueh5"},
                {FactionDemo.HIROSHI, false, 5, "Cheveux_Base", 0xFF1E1A16, "tenueh9"},
                {FactionDemo.YUI, true, 1, "Cheveux_Femme", 0xFF2A2018, "Complet_Tsunade"},
                {FactionDemo.AYA, true, 3, "Cheveux_Femme", 0xFF5A3A2A, "Complet_Sakura"},
                {FactionDemo.REN, false, 1, "Cheveux_4", 0xFF15121A, "Complet_Taijutsuka"},
                {FactionDemo.KAZUKI, false, 3, "Cheveux_Sasuke", 0xFF1A1414, "Complet_UchihaTraining"},
                {FactionDemo.NANAMI, true, 2, "Cheveux_Femme", 0xFFE2C070, "Complet_Femme1"},
                {FactionDemo.DAISUKE, false, 6, "Cheveux_Base", 0xFF2A1A12, "Complet_Ceremonie"},
                {FactionDemo.HANA, true, 2, "Cheveux_Femme", 0xFF2A2A40, "tenueh2"}};
        for (Object[] o : l) {
            try { RebornSkins.applySpec((UUID) o[0], look((boolean) o[1], (int) o[2], (String) o[3], (int) o[4], (String) o[5])); }
            catch (RuntimeException ignored) { }
        }
        try { RebornSkins.applySpec(mc.player.getUUID(), look(false, 1, "Cheveux_4", 0xFF15121A, "Complet_Taijutsuka")); }
        catch (RuntimeException ignored) { }
    }

    private static SkinSpec look(boolean female, int skin, String hair, int hairColor, String outfit) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.slim = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = 0xFF2A2420;
        l.outfitId = outfit;
        return l;
    }
}
