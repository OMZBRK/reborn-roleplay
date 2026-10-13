package fr.reborn.hud.audit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Banc de vues panoramiques, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_PANORAMA} est posé
 * (client de dev, monde solo). Format : {@code nom:x,y,z,yaw,pitch[,heure[,nuages]];nom2:…} — pour chaque vue, passe en
 * spectateur, téléporte, masque le HUD, attend que les sections visibles soient compilées, puis capture
 * {@code run/screenshots/panorama_<nom>.png}. Le jeu se ferme ensuite (réglages vidéo restaurés).
 * Options : {@code REBORN_PANORAMA_RD} (distance de rendu, défaut 32), {@code REBORN_PANORAMA_FOV} (défaut 70),
 * {@code REBORN_PANORAMA_WAIT} (ticks minimum d'attente par vue, défaut 200).
 */
public final class PanoramaBench {

    private record View(String name, double x, double y, double z, float yaw, float pitch, long time, boolean clouds) {}

    private static final List<View> VIEWS = new ArrayList<>();
    private static int t = -1, index = 0, local = 0, prevRd, prevSim, prevFov;
    private static int rd = 32, fov = 70, minWait = 200, lastLoaded = -1, stableSince = 0;
    private static net.minecraft.client.CloudStatus prevClouds;

    private PanoramaBench() {}

    public static void init() {
        String spec = System.getenv("REBORN_PANORAMA");
        if (spec == null || spec.isBlank()) return;
        for (String part : spec.split(";")) {
            String[] nv = part.trim().split(":", 2);
            if (nv.length != 2) continue;
            String[] p = nv[1].split(",");
            long time = p.length > 5 ? Long.parseLong(p[5].trim()) : 11200L;
            VIEWS.add(new View(nv[0].trim(), d(p[0]), d(p[1]), d(p[2]), (float) d(p[3]), (float) d(p[4]), time,
                p.length > 6 && "nuages".equals(p[6].trim())));
        }
        rd = intEnv("REBORN_PANORAMA_RD", 32);
        fov = intEnv("REBORN_PANORAMA_FOV", 70);
        minWait = intEnv("REBORN_PANORAMA_WAIT", 200);
        if (!VIEWS.isEmpty()) ClientTickEvents.END_CLIENT_TICK.register(PanoramaBench::tick);
    }

    private static double d(String s) { return Double.parseDouble(s.trim()); }

    private static int intEnv(String k, int def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static void hideHud(Minecraft mc, boolean hidden) {
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(hidden);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 30) {
            prevRd = mc.options.renderDistance().get();
            prevSim = mc.options.simulationDistance().get();
            prevFov = mc.options.fov().get();
            mc.options.renderDistance().set(rd);
            mc.options.simulationDistance().set(Math.min(rd, 12));
            mc.options.fov().set(fov);
            mc.options.bobView().set(false);
            mc.options.broadcastOptions();   // le serveur borne la vue à la distance annoncée par le client
            prevClouds = mc.options.cloudStatus().get();
            if (!"1".equals(System.getenv("REBORN_PANORAMA_NUAGES"))) mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            // sans cette règle, un spectateur ne pose aucun ticket de chargement : seuls les chunks déjà chargés s'affichent
            cmd(mc, "gamerule spectators_generate_chunks true");
            cmd(mc, "gamemode creative");   // repasse par créatif : la règle n'est relue qu'au changement de mode
            cmd(mc, "gamemode spectator");
            cmd(mc, "weather clear 1000000");
            cmd(mc, "gamerule advance_time false");
            cmd(mc, "gamerule advance_weather false");
            mc.gui.hud.getChat().clearMessages(false);
            if (mc.gui.screen() != null) mc.setScreenAndShow(null);
            hideHud(mc, true);
            return;
        }
        if (t < 40) return;
        if (index >= VIEWS.size()) {
            if (local++ == 10) {
                mc.options.renderDistance().set(prevRd);
                mc.options.simulationDistance().set(prevSim);
                mc.options.fov().set(prevFov);
                mc.options.cloudStatus().set(prevClouds);
                mc.stop();
            }
            return;
        }
        View v = VIEWS.get(index);
        if (local == 0) {
            cmd(mc, "time set " + v.time);
            mc.options.cloudStatus().set(v.clouds || "1".equals(System.getenv("REBORN_PANORAMA_NUAGES"))
                ? net.minecraft.client.CloudStatus.FANCY : net.minecraft.client.CloudStatus.OFF);
            cmd(mc, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.2f %.2f", v.x, v.y, v.z, v.yaw, v.pitch));
            hideHud(mc, true);
        }
        local++;
        // Garde la caméra fixe (le spectateur ne dérive pas, mais un clic de souris le pourrait)
        mc.player.setYRot(v.yaw);
        mc.player.setXRot(v.pitch);
        // Attente : le nombre de chunks chargés doit cesser de croître (≈ 10 s) et toutes les sections être compilées
        int loaded = mc.level.getChunkSource().getLoadedChunksCount();
        if (loaded != lastLoaded) { lastLoaded = loaded; stableSince = local; }
        if (local % 100 == 0) System.out.println("[panorama] " + v.name + " : " + loaded + " chunks, t=" + local);
        boolean ready = local > minWait && local - stableSince > 200 && mc.levelRenderer.hasRenderedAllSections();
        if (ready || local > 6000) {
            if (mc.gui.screen() != null) mc.setScreenAndShow(null);
            hideHud(mc, true);
            String file = "panorama_" + v.name + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
            System.out.println("[panorama] " + file + " (attente " + local + " ticks, sections prêtes=" + ready + ")");
            index++;
            local = 0;
            lastLoaded = -1;
            stableSince = 0;
        }
    }
}
