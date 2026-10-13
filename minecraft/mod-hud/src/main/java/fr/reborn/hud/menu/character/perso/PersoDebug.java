package fr.reborn.hud.menu.character.perso;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc de captures de la sélection et de la création, <b>inactif en production</b> ({@code REBORN_PERSO_DEBUG=1},
 * monde solo de dev). Injecte le roster de la maquette (Kazuki Uchiha, Aya Hyūga, Renji mort + une place libre), puis
 * capture la sélection et les huit étapes de la création dans l'état des maquettes (Konoha, Hyūga, Homme, « Ren
 * Hyūga », 16 ans, 172 cm), la variante Suna, les échelles 3 et 4 et une fenêtre 1366×768. Captures dans
 * run/screenshots/perso_*.png, puis le jeu se ferme. {@code REBORN_PERSO_DEBUG=vite} : 1920×1080 seulement.
 */
public final class PersoDebug {

    private PersoDebug() {}

    private record Shot(String nom, int etape, int village, int echelle, int fw, int fh) {}

    private static final int STEP = 44;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int t = -1;

    public static void init() {
        String v = System.getenv("REBORN_PERSO_DEBUG");
        if (v == null || v.isBlank()) return;
        boolean vite = "vite".equalsIgnoreCase(v.trim());
        String[] noms = {"02_creation_village", "03_creation_clan", "04_creation_sexe", "05_creation_nom",
            "06_creation_age", "07_creation_taille", "08_creation_apparence", "09_creation_recapitulatif"};
        SHOTS.add(new Shot("perso_01_selection", -1, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_01b_selection_aya", -2, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_00_intro", -3, 0, 2, 1920, 1080));
        for (int i = 0; i < 8; i++) SHOTS.add(new Shot("perso_" + noms[i], i, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_02b_creation_village_suna", 0, 1, 2, 1920, 1080));
        for (String vv : new String[]{"kiri", "kumo", "iwa", "ame"}) {
            int vi = java.util.Arrays.asList(PersoArt.VILLAGES).indexOf(vv);
            SHOTS.add(new Shot("perso_02c_village_" + vv, 0, vi, 2, 1920, 1080));
        }
        if (!vite) {
            for (int s : new int[]{3, 4}) {
                SHOTS.add(new Shot("perso_s" + s + "_01_selection", -1, 0, s, 1920, 1080));
                SHOTS.add(new Shot("perso_s" + s + "_08_apparence", 6, 0, s, 1920, 1080));
            }
            SHOTS.add(new Shot("perso_1366_01_selection", -1, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_03_clan", 1, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_08_apparence", 6, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_09_recap", 7, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1706_03_clan", 1, 0, 2, 1706, 960));
            SHOTS.add(new Shot("perso_1706_08_apparence", 6, 0, 2, 1706, 960));
        }
        ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 13000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
            roster();
        }
        if (t < 50) return;
        int i = (t - 50) / STEP, k = (t - 50) % STEP;
        if (i >= SHOTS.size()) {
            if (k == 2) mc.setScreenAndShow(null);
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        if (k == 0) {
            mc.setScreenAndShow(null);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) {
                mc.getWindow().setWindowed(s.fw(), s.fh());
            }
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 3) {
            if (s.etape() < 0 && s.etape() > -3) {
                PersoSelectionScreen sc = PersoSelectionScreen.fromRoster(mc);
                mc.setScreenAndShow(sc);
                if (s.etape() == -2) sc.setFocus(1);
            } else {
                PersoCreationScreen sc = new PersoCreationScreen();
                mc.setScreenAndShow(sc);
                if (s.etape() >= 0) {
                    PersoSons.voixCoupee = true;
                    sc.debugEtat(s.village(), s.village() == 0 ? 2 : 0, 0, "Ren Hyūga", 16, 172, 4, 0, 1, 2, 0);
                    sc.debugEtape(s.etape());
                    PersoSons.voixCoupee = false;
                }
            }
        }
        if (k == STEP - 3) Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static String look(boolean female, int skin, String hair, int hairColor, int eye, String outfit) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.slim = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = eye;
        l.outfitId = outfit;
        return l.serialize();
    }

    /** Roster de la maquette, au format exact du serveur (ShinobiCore CharacterSelectManager). */
    private static void roster() {
        JsonObject root = new JsonObject();
        root.addProperty("slotLimit", 4);
        JsonArray arr = new JsonArray();
        arr.add(card("c1", "Kazuki", "Uchiha", 0xFFB1302B, "Konohagakure", "Chūnin", false, 19, 176 / 180.0, "Homme",
            look(false, 3, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha")));
        arr.add(card("c2", "Aya", "Hyuga", 0xFFCFC6E0, "Konohagakure", "Genin", false, 13, 0.98, "Femme",
            look(true, 2, "Cheveux_Femme", 0xFF2A2440, 0xFFE4DDF0, "Complet_Femme1")));
        arr.add(card("c3", "Renji", "Sabaku", 0xFFC97B3C, "Sunagakure", "Tokubetsu Jōnin", true, 24, 1.10, "Homme",
            look(false, 6, "Cheveux_4", 0xFFC23B3B, 0xFF4E8A4A, "Complet_Taijutsuka")));
        root.add("characters", arr);
        CharacterData.update(root.toString());
    }

    private static JsonObject card(String id, String name, String clan, int col, String village, String rank, boolean dead,
                                   int age, double size, String sexe, String appearance) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("clan", clan);
        o.addProperty("clanColor", col);
        o.addProperty("village", village);
        o.addProperty("rank", rank);
        o.addProperty("level", 5);
        o.addProperty("dead", dead);
        o.addProperty("age", age);
        o.addProperty("size", size);
        o.addProperty("sexe", sexe);
        o.addProperty("appearance", appearance);
        return o;
    }
}
