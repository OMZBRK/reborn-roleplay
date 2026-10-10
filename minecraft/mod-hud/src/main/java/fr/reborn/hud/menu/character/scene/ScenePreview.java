package fr.reborn.hud.menu.character.scene;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai, <b>inactif en production</b> ({@code REBORN_SCENE=1}, monde solo de dev) : construit un bureau du
 * Hokage en blocs dans le ciel, y place trois persos et joue la séquence de sélection (ouverture, changements de
 * perso, entrée en jeu) en capturant des images dans run/screenshots/scene_*.png.
 */
public final class ScenePreview {

    private static int t = -1;
    private static int ox, oy, oz;
    private static SceneSelectScreen screen;
    private static SceneJourneyScreen trip;
    private static boolean journey;

    private ScenePreview() {}

    public static void init() {
        String mode = System.getenv("REBORN_SCENE");
        if (mode == null || mode.isBlank()) return;
        journey = mode.equals("journey");
        ClientTickEvents.END_CLIENT_TICK.register(ScenePreview::tick);
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 30) {
            ox = mc.player.getBlockX() - 7;
            oy = mc.player.getBlockY() + 40;
            oz = mc.player.getBlockZ() - 5;
            cmd(mc, "time set 18000");
            cmd(mc, "weather clear");
            // la scène se joue dans le vide sombre : aucun décor à construire
        }
        if (t == 45) cmd(mc, "gamemode spectator");
        if (journey) { journey(mc); return; }
        int s = t - 140;
        if (s == 0) {
            mc.gui.hud.getChat().clearMessages(false);
            demoRoster();
            screen = SceneSelectScreen.fromRoster(mc);
            mc.setScreenAndShow(screen);
        }
        if (screen == null || s < 0) return;
        if (s == 30) shot(mc, "scene_1_ouverture");
        if (s == 75) shot(mc, "scene_2_kazuki");
        if (s == 80) screen.setFocus(1);
        if (s == 84) shot(mc, "scene_3_transition");
        if (s == 110) shot(mc, "scene_4_aya");
        if (s == 114) screen.setFocus(2);
        if (s == 142) shot(mc, "scene_5_renji_mort");
        if (s == 146) screen.setFocus(3);
        if (s == 176) shot(mc, "scene_6_nouveau");
        if (s == 180) screen.enter();
        if (s == 220) shot(mc, "scene_7_vers_creation");
        if (s == 236) mc.stop();
    }

    private static void journey(Minecraft mc) {
        int s = t - 140;
        if (s == 0) {
            mc.gui.hud.getChat().clearMessages(false);
            trip = SceneJourneyScreen.open(mc);
            mc.setScreenAndShow(trip);
        }
        if (trip == null || s < 0) return;
        if (s == 30) shot(mc, "parcours_00_ouverture");
        if (s == 80) shot(mc, "parcours_01_fragments");
        if (s == 140) shot(mc, "parcours_02_appuie");
        if (s == 150) trip.beginScene();
        if (s == 162) shot(mc, "parcours_03_lancement");
        int o = 150;


        if (s == o + 70) shot(mc, "parcours_04_village");
        if (s == o + 74) { trip.setVillage(2); }
        if (s == o + 92) shot(mc, "parcours_05_village_kiri");
        if (s == o + 96) { trip.setVillage(0); trip.setStep(1); }
        if (s == o + 126) shot(mc, "parcours_06_clan");
        if (s == o + 130) trip.setStep(2);
        if (s == o + 160) shot(mc, "parcours_07_sexe");
        if (s == o + 164) { trip.debugName("Kazuki"); trip.setStep(3); }
        if (s == o + 200) shot(mc, "parcours_08_nom");
        if (s == o + 204) { trip.setStep(4); trip.setAge(12); }
        if (s == o + 236) shot(mc, "parcours_09_age");
        if (s == o + 240) { trip.setStep(6); trip.tweak(2, 1); trip.tweak(3, 1); trip.selectLookRow(2); }
        if (s == o + 276) shot(mc, "parcours_10_apparence");
        if (s == o + 280) trip.setStep(7);
        if (s == o + 336) shot(mc, "parcours_11_recap");
        if (s == o + 350) mc.stop();
    }

    /** Toit de Konoha la nuit : tuiles, cerisier, lanternes, bâtiments éclairés au loin. */
    private static List<String> buildRoof() {
        List<String> c = new ArrayList<>();
        int x = ox, y = oy, z = oz;
        c.add(f(x - 30, y - 12, z - 40, x + 30, y + 10, z + 14, "air"));
        // le bâtiment sous le toit
        c.add(f(x - 6, y - 10, z - 6, x + 6, y - 2, z + 6, "white_terracotta"));
        for (int k = -6; k <= 6; k += 4) {
            c.add(f(x + k, y - 10, z + 6, x + k, y - 2, z + 6, "stripped_dark_oak_log"));
            c.add(f(x + k, y - 10, z - 6, x + k, y - 2, z - 6, "stripped_dark_oak_log"));
        }
        // toit plat en bois + débord de tuiles rouges
        c.add(f(x - 6, y - 1, z - 6, x + 6, y - 1, z + 6, "dark_oak_planks"));
        c.add(f(x - 7, y - 2, z - 7, x + 7, y - 2, z + 7, "red_nether_bricks"));
        c.add(f(x - 6, y - 2, z - 6, x + 6, y - 2, z + 6, "white_terracotta"));
        c.add(f(x - 7, y - 1, z - 7, x + 7, y - 1, z - 7, "red_nether_brick_slab"));
        c.add(f(x - 7, y - 1, z + 7, x + 7, y - 1, z + 7, "red_nether_brick_slab"));
        c.add(f(x - 7, y - 1, z - 7, x - 7, y - 1, z + 7, "red_nether_brick_slab"));
        c.add(f(x + 7, y - 1, z - 7, x + 7, y - 1, z + 7, "red_nether_brick_slab"));
        // cerisier derrière le perso
        c.add(f(x + 3, y, z - 3, x + 3, y + 3, z - 3, "cherry_log"));
        c.add(f(x + 1, y + 3, z - 5, x + 5, y + 5, z - 1, "cherry_leaves[persistent=true]"));
        c.add(f(x + 2, y + 6, z - 4, x + 4, y + 6, z - 2, "cherry_leaves[persistent=true]"));
        c.add(f(x - 1, y + 3, z - 4, x, y + 4, z - 2, "cherry_leaves[persistent=true]"));
        // lanternes sur poteaux
        for (int[] l : new int[][]{{-4, -2}, {5, 3}, {-5, 4}}) {
            c.add(f(x + l[0], y, z + l[1], x + l[0], y + 1, z + l[1], "dark_oak_fence"));
            c.add("setblock " + (x + l[0]) + " " + (y + 2) + " " + (z + l[1]) + " lantern");
        }
        // bâtiments éclairés au loin
        int[][] bld = {{-22, -32, 6, 14}, {-12, -36, 5, 9}, {-2, -40, 7, 18}, {10, -34, 5, 11}, {19, -30, 6, 13}};
        for (int[] b : bld) {
            int bx = x + b[0], bz = z + b[1], wdt = b[2], top = y - 10 + b[3];
            c.add(f(bx, y - 12, bz, bx + wdt, top, bz + 3, "spruce_planks"));
            c.add(f(bx - 1, top + 1, bz - 1, bx + wdt + 1, top + 1, bz + 4, "red_nether_brick_slab"));
            for (int wy = y - 9; wy < top - 1; wy += 3) {
                for (int wx = bx + 1; wx < bx + wdt; wx += 2) {
                    c.add("setblock " + wx + " " + wy + " " + (bz + 3) + " shroomlight");
                }
            }
        }
        return c;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static Vec3 at(double x, double y, double z) { return new Vec3(ox + x, oy + y, oz + z); }

    private static SceneCamera.Shot wide() {
        return SceneCamera.Shot.look(at(7.5, 1.9, 10.2), at(7.5, 1.15, 3.0), 62);
    }

    private static String look(boolean female, int skin, String hair, int hairColor, int eye, String outfit) {
        fr.reborn.hud.skin.SkinSpec l = new fr.reborn.hud.skin.SkinSpec();
        l.female = female;
        l.slim = female;
        l.skinColor = fr.reborn.hud.skin.SkinSpec.skinRamp(skin / 10f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = eye;
        l.outfitId = outfit;
        return l.serialize();
    }

    /** Roster de démo, au format exact du serveur (ShinobiCore CharacterSelectManager). */
    private static void demoRoster() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("slotLimit", 4);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add(card("c1", "Kazuki", "Uchiha", 0xFFB1302B, "Konohagakure", "Chūnin", false, 19, 1.02, "Homme",
                look(false, 3, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha")));
        arr.add(card("c2", "Aya", "Hyuga", 0xFFCFC6E0, "Konohagakure", "Genin", false, 13, 0.98, "Femme",
                look(true, 2, "Cheveux_Femme", 0xFF2A2440, 0xFFB8A6D8, "Complet_Femme1")));
        arr.add(card("c3", "Renji", "Sabaku", 0xFFC97B3C, "Sunagakure", "Tokubetsu Jōnin", true, 24, 1.10, "Homme",
                look(false, 6, "Cheveux_4", 0xFFC23B3B, 0xFF4E8A4A, "Complet_Taijutsuka")));
        root.add("characters", arr);
        fr.reborn.hud.menu.character.CharacterData.update(root.toString());
    }

    private static com.google.gson.JsonObject card(String id, String name, String clan, int col, String village,
                                                   String rank, boolean dead, int age, double size, String sexe,
                                                   String appearance) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
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

    /** Bureau du Hokage, la nuit : 15 × 11 blocs, boiseries sombres, bibliothèques, fenêtre, lanternes, bureau. */
    private static List<String> build() {
        List<String> c = new ArrayList<>();
        int x = ox, y = oy, z = oz;
        c.add(f(x - 2, y - 2, z - 2, x + 16, y + 8, z + 12, "air"));
        c.add(f(x - 1, y - 1, z - 1, x + 15, y + 6, z + 11, "spruce_planks hollow"));
        c.add(f(x - 1, y - 1, z - 1, x + 15, y - 1, z + 11, "dark_oak_planks"));
        c.add(f(x - 1, y + 6, z - 1, x + 15, y + 6, z + 11, "dark_oak_planks"));
        // murs latéraux : bibliothèques
        c.add(f(x - 1, y, z, x - 1, y + 3, z + 10, "bookshelf"));
        c.add(f(x + 15, y, z, x + 15, y + 3, z + 10, "bookshelf"));
        // mur du fond : bibliothèques de part et d'autre, fenêtre au centre
        c.add(f(x, y, z - 1, x + 3, y + 3, z - 1, "bookshelf"));
        c.add(f(x + 11, y, z - 1, x + 14, y + 3, z - 1, "bookshelf"));
        c.add(f(x + 5, y + 1, z - 1, x + 9, y + 4, z - 1, "glass_pane"));
        c.add(f(x + 7, y + 1, z - 1, x + 7, y + 4, z - 1, "dark_oak_fence"));
        c.add(f(x + 5, y + 3, z - 1, x + 9, y + 3, z - 1, "dark_oak_fence"));
        // piliers
        for (int k : new int[]{-1, 4, 10, 15}) c.add(f(x + k, y, z - 1, x + k, y + 5, z - 1, "stripped_dark_oak_log"));
        for (int k : new int[]{3, 7, 11}) {
            c.add(f(x - 1, y, z + k, x - 1, y + 5, z + k, "stripped_dark_oak_log"));
            c.add(f(x + 15, y, z + k, x + 15, y + 5, z + k, "stripped_dark_oak_log"));
        }
        // poutres + lanternes suspendues
        for (int k : new int[]{1, 5, 9}) c.add(f(x - 1, y + 5, z + k, x + 15, y + 5, z + k, "dark_oak_log[axis=x]"));
        for (int[] l : new int[][]{{2, 1}, {12, 1}, {3, 5}, {11, 5}, {7, 9}}) {
            c.add("setblock " + (x + l[0]) + " " + (y + 4) + " " + (z + l[1]) + " lantern[hanging=true]");
        }
        // bannières rouges sur les piliers du fond
        c.add("setblock " + (x + 4) + " " + (y + 3) + " " + z + " red_wall_banner[facing=south]");
        c.add("setblock " + (x + 10) + " " + (y + 3) + " " + z + " red_wall_banner[facing=south]");
        // tapis
        c.add(f(x + 2, y, z + 3, x + 12, y, z + 8, "red_carpet"));
        c.add(f(x + 3, y, z + 4, x + 11, y, z + 7, "brown_carpet"));
        // bureau + chaise + objets
        c.add(f(x + 4, y, z + 2, x + 10, y, z + 2, "dark_oak_slab[type=top]"));
        c.add(f(x + 4, y, z + 3, x + 10, y, z + 3, "dark_oak_trapdoor[facing=south,half=top,open=true]"));
        c.add("setblock " + (x + 7) + " " + y + " " + (z + 1) + " dark_oak_stairs[facing=south]");
        c.add("setblock " + (x + 5) + " " + (y + 1) + " " + (z + 2) + " candle[candles=3,lit=true]");
        c.add("setblock " + (x + 9) + " " + (y + 1) + " " + (z + 2) + " decorated_pot");
        c.add("setblock " + (x + 10) + " " + (y + 1) + " " + (z + 2) + " lantern");
        // plantes dans les coins
        c.add("setblock " + x + " " + y + " " + (z + 10) + " potted_fern");
        c.add("setblock " + (x + 14) + " " + y + " " + (z + 10) + " potted_bamboo");
        c.add("setblock " + x + " " + y + " " + (z + 0) + " potted_fern");
        c.add("setblock " + (x + 14) + " " + y + " " + (z + 0) + " potted_fern");
        return c;
    }

    private static String f(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + b;
    }
}
