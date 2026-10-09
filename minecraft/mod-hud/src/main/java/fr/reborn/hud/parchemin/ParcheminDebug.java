package fr.reborn.hud.parchemin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;


/**
 * Banc d'essai des parchemins, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_PARCHEMIN_DEBUG=1}
 * (client de dev). Ouvre la bibliothèque, la lecture d'un rouleau (rang C en cours, rang S en fin de parcours) et le
 * réglage staff, capture chaque écran dans run/screenshots/, journalise la simulation des tirages, puis ferme le jeu.
 */
public final class ParcheminDebug {

    private static int ticks = -1;

    private ParcheminDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_PARCHEMIN_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ParcheminDebug::tick);
    }

    public static Technique find(String id) {
        for (Technique t : Technique.DEMO) if (t.id().equals(id)) return t;
        return Technique.DEMO.get(0);
    }

    private static final int[] SCALES = {2, 3, 4};
    private static final int STEP = 95;

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        if (ticks == 30) { mc.player.connection.sendCommand("time set 6000"); mc.gui.hud.getChat().clearMessages(false); }
        if (ticks < 40) return;
        int phase = (ticks - 40) / STEP, t = (ticks - 40) % STEP;
        if (phase >= SCALES.length) { if (t == 5) mc.stop(); return; }
        switch (t) {
            case 0 -> { mc.options.guiScale().set(SCALES[phase]); mc.resizeGui(); }
            case 5 -> ParcheminClient.debugReceive(libJson().toString());
            case 8 -> with(mc, BibliothequeScreen.class, s -> s.debugHover = s.firstFilled());
            case 20 -> shot(mc);                                                          // bibliothèque + fiche
            case 25 -> {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("t", "read");
                o.add("tech", tech(find("katon_gokakyu")));
                o.addProperty("done", 1);
                o.addProperty("nextIn", "maintenant");
                o.addProperty("master", true);
                ParcheminClient.debugReceive(o.toString());
            }
            case 45 -> shot(mc);                                                          // lecture
            case 50 -> ParcheminClient.debugReceive(cfgJson().toString());
            case 60 -> shot(mc);                                                          // réglages
            case 65 -> with(mc, BibliothequeStaffScreen.class, s -> { s.setPage(1); s.setFilter(3); });
            case 75 -> shot(mc);                                                          // techniques
            case 80 -> with(mc, BibliothequeStaffScreen.class, s -> s.setPage(2));
            case 90 -> shot(mc);                                                          // simulation
            default -> { }
        }
    }

    public static com.google.gson.JsonObject tech(Technique t) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("id", t.id());
        o.addProperty("name", t.name());
        o.addProperty("branch", t.branch());
        o.addProperty("nature", t.nature());
        o.addProperty("rank", String.valueOf(t.rank()));
        o.addProperty("desc", t.desc());
        com.google.gson.JsonArray sg = new com.google.gson.JsonArray();
        t.signs().forEach(sg::add);
        o.add("signs", sg);
        return o;
    }

    /** Ce qu'envoie ShinobiAbilities à l'ouverture d'une bibliothèque. */
    public static com.google.gson.JsonObject libJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib");
        o.addProperty("id", "academie");
        o.addProperty("name", "Bibliothèque de l'Académie");
        o.addProperty("count", 6);
        o.addProperty("refreshIn", "2 h 41");
        com.google.gson.JsonArray cells = new com.google.gson.JsonArray();
        String[] ids = {"kawarimi", null, "katon_gokakyu", "konoha_senpu", "raiton_chidori", "tsubame_gaeshi", null, null, null};
        for (String id : ids) cells.add(id == null ? com.google.gson.JsonNull.INSTANCE : tech(find(id)));
        o.add("cells", cells);
        o.addProperty("weight", 7.2);
        o.addProperty("maxWeight", 12);
        o.addProperty("staff", true);
        return o;
    }

    /** Ce qu'envoie ShinobiAbilities pour /bibliotheque regler. */
    public static com.google.gson.JsonObject cfgJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib_cfg");
        o.addProperty("id", "academie");
        o.addProperty("name", "Bibliothèque de l'Académie");
        o.addProperty("count", 6);
        o.addProperty("delayH", 3);
        o.addProperty("model", 0);
        com.google.gson.JsonArray r = new com.google.gson.JsonArray();
        for (double d : Tirage.PUBLIQUE) r.add(d);
        o.add("byRank", r);
        com.google.gson.JsonObject ov = new com.google.gson.JsonObject();
        ov.addProperty("raiton_chidori", 1.0);
        o.add("overrides", ov);
        com.google.gson.JsonArray techs = new com.google.gson.JsonArray();
        for (Technique t : Technique.DEMO) {
            com.google.gson.JsonObject j = tech(t);
            if (t.rank() == 'S') j.addProperty("slots", "1/2");
            techs.add(j);
        }
        o.add("techs", techs);
        return o;
    }

    private interface Act<T> { void run(T s); }

    private static <T> void with(Minecraft mc, Class<T> c, Act<T> a) {
        if (c.isInstance(mc.gui.screen())) a.run(c.cast(mc.gui.screen()));
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
