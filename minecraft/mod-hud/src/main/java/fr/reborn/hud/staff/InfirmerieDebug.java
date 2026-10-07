package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel de l'Infirmerie, <b>inactif en production</b> : ne fait rien sauf si
 * {@code REBORN_INFIRMERIE_DEBUG=1} (client de dev). Injecte un état d'exemple (blessés, zones et lits autour de
 * Konoha), ouvre chaque onglet, prend une capture dans run/screenshots/, puis ferme le jeu.
 */
public final class InfirmerieDebug {

    private static int ticks = -1;

    private InfirmerieDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_INFIRMERIE_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(InfirmerieDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        switch (ticks) {
            case 40 -> mc.player.connection.sendCommand("time set 13000");
            case 60 -> {
                StaffClient.debugReceive(snapshot(mc.player.getUUID().toString()));
                mc.setScreenAndShow(new InfirmerieScreen());
            }
            case 95 -> shot(mc);                                                     // Blessés, page 1
            case 100 -> with(mc, s -> s.debugPage(1));
            case 125 -> shot(mc);                                                    // Blessés, page 2
            case 130 -> with(mc, s -> { s.debugTab("zones"); });
            case 132 -> with(mc, s -> s.debugSelect("dojo"));
            case 150 -> shot(mc);                                                    // Zones, dojo sélectionné
            case 155 -> with(mc, s -> s.debugDraft(15905, 9690, 15930, 9705));
            case 170 -> shot(mc);                                                    // Nouvelle zone
            case 175 -> with(mc, s -> s.debugTab("hopitaux"));
            case 195 -> shot(mc);                                                    // Hôpitaux
            case 200 -> with(mc, s -> s.debugTab("reglages"));
            case 220 -> shot(mc);                                                    // Réglages
            case 240 -> mc.stop();
            default -> { }
        }
    }

    private interface Act { void run(InfirmerieScreen s); }

    private static void with(Minecraft mc, Act a) {
        if (mc.gui.screen() instanceof InfirmerieScreen s) a.run(s);
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }

    private static String snapshot(String me) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "ko_snap");
        o.addProperty("grade", 4);
        o.addProperty("gradeName", "Owner");
        JsonArray inj = new JsonArray();
        String[][] rows = {
                {"Kazuki Uchiha", "Raiden_FR", "down", "À terre", "32", "45", "s", "Par Daisuke|marché"},
                {"Ren Hyūga", "Mika_", "ko", "Inconscient", "161", "300", "s", "Porté par Aya|/hopital proposé"},
                {"Kenta Aburame", "kenta.a", "chakra", "Épuisé", "80", "180", "s", "Chakra vide|terrain sud"},
                {"Sora Nara", "sora", "down", "À terre", "12", "45", "s", "Par Kazuki|forêt est"},
                {"Mei Akimichi", "mei.a", "ko", "Inconscient", "40", "300", "s", "route du nord"},
                {"Daisuke Sarutobi", "Dais", "ata", "ATA pleine", "3", "20", "repos", "Repos 3/20 min|hôpital"},
                {"Hana Inuzuka", "hana_rp", "light", "ATA allégée", "8", "10", "repos", "Repos 8/10 min · en cours|ichiraku"}};
        for (String[] r : rows) {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", me);
            j.addProperty("perso", r[0]);
            j.addProperty("name", r[1]);
            j.addProperty("state", r[2]);
            j.addProperty("label", r[3]);
            j.addProperty("left", Integer.parseInt(r[4]));
            j.addProperty("total", Integer.parseInt(r[5]));
            j.addProperty("unit", r[6]);
            JsonArray meta = new JsonArray();
            for (String m : r[7].split("\\|")) meta.add(m);
            j.add("meta", meta);
            inj.add(j);
        }
        o.add("injured", inj);
        JsonArray zones = new JsonArray();
        zones.add(zone("dojo", "entrainement", "", 15860, 9660, 15884, 9678, new double[0][]));
        zones.add(zone("terrain-3", "entrainement", "", 15950, 9630, 15990, 9660, new double[0][]));
        zones.add(zone("ichiraku", "repos", "", 15898, 9722, 15910, 9730, new double[0][]));
        zones.add(zone("onsen", "repos", "", 15940, 9700, 15960, 9714, new double[0][]));
        zones.add(zone("hopital-konoha", "hopital", "konoha", 15850, 9740, 15880, 9760,
                new double[][]{{15854, 9744, 1}, {15858, 9744, 0}, {15862, 9744, 0}, {15854, 9752, 1}, {15858, 9752, 0}, {15862, 9752, 0}, {15870, 9748, 0}}));
        o.add("zones", zones);
        JsonArray maps = new JsonArray();
        JsonObject m = new JsonObject();
        m.addProperty("id", "konoha"); m.addProperty("title", "Konoha"); m.addProperty("world", "world");
        maps.add(m);
        o.add("maps", maps);
        JsonObject meo = new JsonObject();
        meo.addProperty("world", "world"); meo.addProperty("x", 15900); meo.addProperty("y", 66); meo.addProperty("z", 9700);
        o.add("me", meo);
        JsonObject set = new JsonObject();
        int[] vals = {45, 300, 120, 30, 20, 10, 20, 4, 15, 60, 30};
        String[] keys = {"terre", "inco", "hopital", "plancher", "pleine", "allegee", "peur", "chuchot", "paume", "rang1", "paumeAta"};
        for (int i = 0; i < keys.length; i++) set.addProperty(keys[i], vals[i]);
        o.add("settings", set);
        o.add("defaults", set.deepCopy());
        return o.toString();
    }

    private static JsonObject zone(String id, String kind, String village, int x1, int z1, int x2, int z2, double[][] beds) {
        JsonObject z = new JsonObject();
        z.addProperty("id", id); z.addProperty("kind", kind); z.addProperty("village", village); z.addProperty("world", "world");
        z.addProperty("x1", x1); z.addProperty("z1", z1); z.addProperty("x2", x2); z.addProperty("z2", z2);
        z.addProperty("y0", 62); z.addProperty("y1", 80);
        JsonArray b = new JsonArray();
        for (double[] bd : beds) {
            JsonObject j = new JsonObject();
            j.addProperty("x", bd[0]); j.addProperty("y", 66); j.addProperty("z", bd[1]);
            j.addProperty("who", bd[2] > 0 ? "Ryo Hatake" : "");
            b.add(j);
        }
        z.add("beds", b);
        return z;
    }
}
