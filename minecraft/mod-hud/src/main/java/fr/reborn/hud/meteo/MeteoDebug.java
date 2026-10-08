package fr.reborn.hud.meteo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel de la météo, <b>inactif en production</b> : ne fait rien sauf si la variable d'environnement
 * {@code REBORN_METEO_DEBUG=1} est définie (client de dev, {@code gradlew runClient}). Dans un monde solo, au matin
 * face au soleil levant : pluie (jour, éclair, nuit), sable aux trois degrés, brume légère puis à 100 %, avec des captures dans
 * run/screenshots/, puis ferme le jeu.
 */
public final class MeteoDebug {

    private static int ticks = -1;

    private MeteoDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_METEO_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(MeteoDebug::tick);
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
}
