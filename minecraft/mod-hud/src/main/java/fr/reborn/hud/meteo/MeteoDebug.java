package fr.reborn.hud.meteo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel de la météo, <b>inactif en production</b> : ne fait rien sauf si la variable d'environnement
 * {@code REBORN_METEO_DEBUG=1} est définie (client de dev, {@code gradlew runClient}). Dans un monde solo, au matin
 * face au soleil levant : pluie (jour, éclair, nuit), sable aux trois degrés, brume légère puis à 100 %, avec des captures dans
 * run/screenshots/, puis ferme le jeu.
 *
 * <p>{@code REBORN_METEO_DEBUG=tempetes} (15/10) : blizzard et tempête de sable — au sol près du point d'apparition, des
 * mannequins à 4, 8, 12 et 18 blocs pour juger la visibilité : temps clair, entrée dans le blizzard (images de GIF
 * {@code metgif_*}), 75 % (≈ 12 blocs), 100 % (≈ 6 blocs), 3ᵉ personne, nuit, sortie ; même chose pour la tempête de
 * sable ({@code meteo_t*.png}). Remet position, heure et caméra puis ferme le jeu.
 */
public final class MeteoDebug {

    private static int ticks = -1;

    private MeteoDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_METEO_DEBUG");
        if ("tempetes".equals(v)) { ClientTickEvents.END_CLIENT_TICK.register(MeteoDebug::tempetes); return; }
        if (!"1".equals(v)) return;
        ClientTickEvents.END_CLIENT_TICK.register(MeteoDebug::tick);
    }

    private static double ox, oy, oz;
    private static volatile int solX, solY = 100, solZ;
    private static boolean pose;
    private static int t0;
    private static float oyaw, opitch;
    private static net.minecraft.client.CameraType vueAvant;
    private static fr.reborn.hud.camera.RebornCamera.Mode camAvant;

    /** Banc des tempêtes (blizzard, tempête de sable). */
    private static void tempetes(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        var o = mc.options;
        if (ticks > 30) mc.gui.hud.getChat().clearMessages(false);
        // le sol est cherché par le serveur intégré (génération de chunks) : la suite part du moment où l'on s'y pose
        if (ticks > 20 && !pose && ticks % 20 == 0 && (solX != 0 || ticks > 1600)) {
            c.sendCommand("tp @s " + solX + ".5 " + solY + " " + solZ + ".5 180 4");
            pose = true;
            t0 = ticks;
        }
        int q = ticks <= 20 ? ticks : pose ? ticks - t0 + 100 : -1;
        switch (q) {
            case 20 -> {
                ox = mc.player.getX(); oy = mc.player.getY(); oz = mc.player.getZ();
                oyaw = mc.player.getYRot(); opitch = mc.player.getXRot();
                vueAvant = o.getCameraType();
                camAvant = fr.reborn.hud.camera.RebornCamera.INSTANCE.mode();
                fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(fr.reborn.hud.camera.RebornCamera.Mode.VANILLA);
                o.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                for (String r : new String[]{"sendCommandFeedback", "send_command_feedback"}) c.sendCommand("gamerule " + r + " false");
                c.sendCommand("time set 5000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode creative");
                // sol calculé par le serveur intégré (il génère les chunks), près du point d'apparition
                var srv = mc.getSingleplayerServer();
                int x = net.minecraft.util.Mth.floor(ox) + 40, z = net.minecraft.util.Mth.floor(oz) + 40;
                if (srv != null) srv.execute(() -> {
                    var sl = srv.overworld();
                    var H = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING;
                    var HN = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
                    // un sol dégagé (ni arbre ni eau) avec 20 blocs libres vers le nord (regard)
                    for (int k = 0; k < 81; k++) {
                        int px = x + (k % 9) * 16 - 64, pz = z + (k / 9) * 16 - 64;
                        sl.getChunk(px >> 4, pz >> 4);
                        sl.getChunk(px >> 4, (pz - 20) >> 4);
                        int h = sl.getHeight(H, px, pz);
                        if (h != sl.getHeight(HN, px, pz) || h < sl.getSeaLevel() + 1
                                || !sl.getFluidState(new net.minecraft.core.BlockPos(px, h - 1, pz)).isEmpty()) continue;
                        boolean ok = true;
                        for (int d = 4; d <= 20 && ok; d += 4) {
                            int hd = sl.getHeight(H, px, pz - d);
                            ok = hd == sl.getHeight(HN, px, pz - d) && Math.abs(hd - h) <= 3
                                    && sl.getFluidState(new net.minecraft.core.BlockPos(px, hd - 1, pz - d)).isEmpty();
                        }
                        if (ok) { solX = px; solY = h; solZ = pz; return; }
                    }
                    sl.getChunk(x >> 4, z >> 4);
                    solY = Math.max(sl.getSeaLevel() + 2, sl.getHeight(HN, x, z));
                    solX = x; solZ = z;
                });
            }
            case 175 -> {
                c.sendCommand("kill @e[tag=meteo_test]");
                for (int d : new int[]{4, 8, 12, 18}) {
                    c.sendCommand("summon mannequin ^" + (d % 8 == 0 ? 1 : -1) + " ^1 ^" + d
                            + " {Rotation:[0f,0f],Tags:[\"meteo_test\"]}");
                }
            }
            case 230 -> shot(mc, "meteo_t00_clair");
            case 240 -> MeteoClient.update(MeteoClient.BLIZZARD, 0.75f);
            case 340 -> shot(mc, "meteo_t01_blizzard_75");
            case 345 -> MeteoClient.update(MeteoClient.BLIZZARD, 1f);
            case 420 -> shot(mc, "meteo_t02_blizzard_100");
            case 425 -> o.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            case 440 -> shot(mc, "meteo_t03_blizzard_3e");
            case 445 -> { o.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); c.sendCommand("time set 16000"); }
            case 470 -> shot(mc, "meteo_t04_blizzard_nuit");
            case 475 -> { c.sendCommand("time set 5000"); MeteoClient.update(MeteoClient.BLIZZARD, 0.4f); }
            case 560 -> shot(mc, "meteo_t05_blizzard_40");
            case 565 -> MeteoClient.update(MeteoClient.CLAIR, 0f);
            case 640 -> MeteoClient.update(MeteoClient.TEMPETE, 0.75f);
            case 740 -> shot(mc, "meteo_t11_tempete_75");
            case 745 -> MeteoClient.update(MeteoClient.TEMPETE, 1f);
            case 820 -> shot(mc, "meteo_t12_tempete_100");
            case 825 -> o.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            case 840 -> shot(mc, "meteo_t13_tempete_3e");
            case 845 -> { o.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); c.sendCommand("time set 13500"); }
            case 870 -> shot(mc, "meteo_t14_tempete_soir");
            case 875 -> { c.sendCommand("time set 5000"); MeteoClient.update(MeteoClient.CLAIR, 0f); }
            case 950 -> {
                c.sendCommand("kill @e[tag=meteo_test]");
                if (camAvant != null) fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(camAvant);
                if (vueAvant != null) o.setCameraType(vueAvant);
                c.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", ox, oy, oz, oyaw, opitch));
                for (String r : new String[]{"sendCommandFeedback", "send_command_feedback"}) c.sendCommand("gamerule " + r + " true");
            }
            case 990 -> mc.stop();
            default -> { }
        }
        // images de GIF : entrée et sortie de chaque tempête
        if ((q >= 236 && q < 330 && q % 3 == 0) || (q >= 563 && q < 630 && q % 3 == 0)
                || (q >= 636 && q < 730 && q % 3 == 0) || (q >= 873 && q < 945 && q % 3 == 0)) {
            Screenshot.grab(mc.gameDirectory, String.format(java.util.Locale.ROOT, "metgif_%04d.png", q),
                    mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 1500");
                c.sendCommand("weather clear");
                c.sendCommand("tp @s ~ ~ ~ -90 -8");                 // face à l'est (soleil du matin)
                c.sendCommand("kill @e[tag=meteo_test]");
            }
            case 60 -> c.sendCommand("summon mannequin ^ ^ ^4 {NoGravity:1b,Rotation:[90f,0f],Tags:[\"meteo_test\"]}");
            case 240 -> shot(mc);                                     // référence : temps clair
            case 250 -> MeteoClient.update(MeteoClient.PLUIE, 0.85f);
            case 340 -> shot(mc);
            case 354 -> MeteoClient.eclair();
            case 355 -> shot(mc);
            case 357 -> c.sendCommand("time set 18000");           // pluie de nuit
            case 367 -> shot(mc);
            case 368 -> c.sendCommand("time set 1500");
            case 370 -> MeteoClient.update(MeteoClient.SABLE, 0.25f);     // petit vent
            case 470 -> shot(mc);
            case 480 -> MeteoClient.update(MeteoClient.SABLE, 0.55f);     // vent moyen
            case 540 -> shot(mc);
            case 550 -> MeteoClient.update(MeteoClient.SABLE, 0.9f);      // grosse tempête
            case 610 -> shot(mc);
            case 611 -> mc.player.setYRot(mc.player.getYRot() + 30f);  // tête tournée : le sable doit suivre le décor
            case 614 -> shot(mc);
            case 620 -> MeteoClient.update(MeteoClient.BRUME, 0.4f);
            case 720 -> shot(mc);
            case 730 -> MeteoClient.update(MeteoClient.BRUME, 1.0f);
            case 790 -> shot(mc);
            case 800 -> MeteoClient.update(MeteoClient.CLAIR, 0f);
            case 805 -> c.sendCommand("kill @e[tag=meteo_test]");
            case 850 -> mc.stop();
            default -> { }
        }
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }

    private static void shot(Minecraft mc, String nom) {
        Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
    }
}
