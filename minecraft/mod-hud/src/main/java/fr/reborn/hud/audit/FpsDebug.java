package fr.reborn.hud.audit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Banc de FPS, <b>inactif en production</b> ({@code REBORN_FPS_DEBUG=1}, monde solo de dev) : sans limite d'images ni
 * VSync, à la distance de rendu {@code REBORN_FPS_RD} (12 par défaut), le joueur regarde dans quatre directions depuis
 * le point d'apparition (ou les vues {@code REBORN_FPS_POS="nom:x y z lacet tangage;…"}, jamais notées dans le dépôt) ;
 * à chaque vue : attente des chunks, puis 8 s de mesure avec le HUD, 8 s sans HUD. Écrit les FPS (moyenne, min, max) et
 * les entités rendues par type dans {@code run/fps_banc.txt}, une capture par vue, rend les réglages et ferme le jeu.
 */
public final class FpsDebug {

    private record Vue(String nom, String tp) {}
    private record Pas(int attente, Runnable action) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static final List<Integer> mesures = new ArrayList<>();
    private static final StringBuilder rapport = new StringBuilder();
    private static int t = -1, prochain, idx;
    private static boolean mesure;
    private static int limiteAvant = -1, rdAvant = -1;
    private static boolean vsyncAvant;
    private static String mode = "survival";
    /** {@code REBORN_FPS_DEBUG=hud} : coût de chacun de nos éléments du HUD, l'un après l'autre, sur une seule vue. */
    private static boolean parElement;
    /** Éléments coupés le temps d'une mesure (enveloppés une fois au début). */
    private static final java.util.Set<String> COUPES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final List<String> ELEMENTS = List.of("cecite", "duel", "interaction", "vitals", "tablist-hold",
        "head-bubbles", "nameplates", "combat", "combat-endurance", "cooldowns", "byakugan_activation", "ko", "volonte",
        "escouade", "nourriture", "fouille", "ramasser", "notifs", "concentration", "allure", "boussole",
        "communication_bande", "faction_invit", "chargement_fondu", "effets", "capture-preview", "staff-effets",
        "staff-plaques");

    private FpsDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_FPS_DEBUG");
        if (!"1".equals(v) && !"hud".equals(v)) return;
        parElement = "hud".equals(v);
        ClientTickEvents.END_CLIENT_TICK.register(FpsDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 40) preparer(mc);
        if (mesure && t % 20 == 0) mesures.add(mc.getFps());
        if (t > 40 && t >= prochain && idx < PAS.size()) {
            Pas p = PAS.get(idx++);
            p.action().run();
            prochain = t + p.attente();
        }
        if (t > 40 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
    }

    private static void pas(int attente, Runnable r) { PAS.add(new Pas(attente, r)); }

    private static void preparer(Minecraft mc) {
        var o = mc.options;
        limiteAvant = o.framerateLimit().get();
        vsyncAvant = o.enableVsync().get();
        rdAvant = o.renderDistance().get();
        if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
        int rd = 12;
        try { rd = Integer.parseInt(System.getenv().getOrDefault("REBORN_FPS_RD", "12").trim()); } catch (NumberFormatException ignored) { }
        o.framerateLimit().set(260);   // 260 = illimité
        o.enableVsync().set(false);
        o.renderDistance().set(rd);
        try { o.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED); } catch (RuntimeException ignored) { }
        var c = mc.player.connection;
        c.sendCommand("gamemode creative");
        c.sendCommand("time set 6000");
        c.sendCommand("weather clear");
        rapport.append("Banc FPS reborn-hud · distance de rendu ").append(rd).append(" · ")
            .append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
            .append(" · réglages avant : limite ").append(limiteAvant).append(", vsync ").append(vsyncAvant)
            .append(", distance ").append(rdAvant).append('\n');

        List<Vue> vues = new ArrayList<>();
        String env = System.getenv("REBORN_FPS_POS");
        if (env != null && !env.isBlank()) {
            for (String v : env.split(";")) {
                String[] kv = v.split(":", 2);
                if (kv.length == 2) vues.add(new Vue(kv[0].trim(), kv[1].trim()));
            }
        } else {
            for (int lacet : new int[] {0, 90, 180, 270}) vues.add(new Vue("apparition_" + lacet, "~ ~ ~ " + lacet + " 5"));
        }
        if (parElement) {
            fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
            for (String id : ELEMENTS) {
                try {
                    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", id),
                        orig -> (g, dt) -> { if (!COUPES.contains(id)) orig.extractRenderState(g, dt); });
                } catch (RuntimeException ignored) { }
            }
            Vue v = vues.get(0);
            pas(160, () -> { vol(mc); c.sendCommand("tp @s " + v.tp()); });
            pas(140, () -> { mesures.clear(); mesure = true; });
            pas(2, () -> { mesure = false; ligne(mc, "HUD complet", "RP"); hud(mc, true); mesures.clear(); mesure = true; });
            pas(140, () -> { });
            pas(2, () -> { mesure = false; ligne(mc, "HUD masqué", "tout"); hud(mc, false); COUPES.addAll(ELEMENTS); mesures.clear(); mesure = true; });
            pas(140, () -> { });
            pas(2, () -> { mesure = false; ligne(mc, "vanilla seul", "Reborn coupé"); });
            List<String> ordre = new ArrayList<>(ELEMENTS);
            if ("inverse".equals(System.getenv("REBORN_FPS_ORDRE"))) java.util.Collections.reverse(ordre);
            for (String id : ordre) {
                pas(2, () -> { COUPES.remove(id); mesures.clear(); mesure = true; });
                pas(120, () -> { });
                pas(2, () -> { mesure = false; ligne(mc, "+ " + id, "seul"); COUPES.add(id); });
            }
            pas(2, () -> { COUPES.clear(); fr.reborn.hud.runtime.RebornSession.debugForceRp = false; });
        }
        else for (Vue v : vues) {
            pas(160, () -> {
                vol(mc);
                c.sendCommand("tp @s " + v.tp());
            });
            pas(20, () -> Screenshot.grab(mc.gameDirectory, "fps_" + v.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { }));
            pas(160, () -> { mesures.clear(); mesure = true; });
            pas(2, () -> { mesure = false; ligne(mc, v.nom(), "avec HUD"); hud(mc, true); mesures.clear(); mesure = true; });
            pas(160, () -> { });
            pas(2, () -> { mesure = false; ligne(mc, v.nom(), "sans HUD"); hud(mc, false); entites(mc); });
        }
        pas(10, () -> {
            o.framerateLimit().set(limiteAvant);
            o.enableVsync().set(vsyncAvant);
            o.renderDistance().set(rdAvant);
            c.sendCommand("gamemode " + mode);
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("fps_banc.txt"), rapport.toString(), StandardCharsets.UTF_8);
            } catch (IOException ignored) { }
            System.out.println("[fps-banc]\n" + rapport);
        });
        pas(10, mc::stop);
    }

    private static void ligne(Minecraft mc, String vue, String cas) {
        List<Integer> m = new ArrayList<>(mesures);
        if (!m.isEmpty()) m.remove(0);   // la première seconde mélange deux états
        double moy = m.stream().mapToInt(Integer::intValue).average().orElse(0);
        int min = m.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = m.stream().mapToInt(Integer::intValue).max().orElse(0);
        rapport.append(String.format(Locale.ROOT, "%-24s %-12s moyenne %6.1f  min %4d  max %4d%n", vue, cas, moy, min, max));
    }

    private static void entites(Minecraft mc) {
        Map<String, Integer> n = new HashMap<>();
        int total = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceTo(mc.player) > mc.options.renderDistance().get() * 16) continue;
            n.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1, Integer::sum);
            total++;
        }
        StringBuilder b = new StringBuilder("   entités dans la distance de rendu : " + total);
        n.entrySet().stream().sorted((a, z) -> z.getValue() - a.getValue()).limit(6)
            .forEach(e -> b.append(" · ").append(e.getKey()).append(' ').append(e.getValue()));
        rapport.append(b).append('\n');
    }

    private static void hud(Minecraft mc, boolean cache) {
        ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(cache);
    }

    private static void vol(Minecraft mc) {
        var ab = mc.player.getAbilities();
        ab.flying = ab.mayfly;
        mc.player.onUpdateAbilities();
    }

}
