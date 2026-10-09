package fr.reborn.hud.ko;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel du KO, <b>inactif en production</b> : ne fait rien sauf si la variable d'environnement
 * {@code REBORN_KO_DEBUG=1} est définie (client de dev, {@code gradlew runClient}). Dans un monde solo, simule les
 * paquets du serveur (à terre, KO, épuisement, hôpital, ATA, repos, rétabli, défaite) et prend une capture de
 * chaque étape dans run/screenshots/, puis ferme le jeu.
 */
public final class KoDebug {

    private static int ticks = -1;

    private KoDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_KO_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(KoDebug::tick);
    }

    public static KoPayload ko(int phase, int cause, int left, int total, boolean hosp) {
        return new KoPayload((byte) phase, (byte) cause, (short) left, (short) total, hosp,
                (byte) 0, 0f, (short) 0, (short) 0, false, false);
    }

    public static KoPayload ata(int level, float rest, int min, int req, boolean resting, boolean fear) {
        return new KoPayload((byte) 0, (byte) 0, (short) 0, (short) 0, false,
                (byte) level, rest, (short) min, (short) req, resting, fear);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 13000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("tp @s ~ ~ ~ -90 5");
                c.sendCommand("kill @e[tag=ko_test]");
            }
            case 60 -> c.sendCommand("summon mannequin ^1 ^ ^4 {NoGravity:1b,Rotation:[120f,0f],Tags:[\"ko_test\"]}");
            case 100 -> { KoClient.event(KoClient.EV_A_TERRE); KoClient.update(ko(1, 0, 38, 45, false)); }
            case 112 -> shot(mc);                                   // carte « À terre »
            case 190 -> shot(mc);                                   // à terre : plaque + vignette
            case 200 -> { KoClient.event(KoClient.EV_KO); KoClient.update(ko(2, 0, 170, 300, true)); }
            case 212 -> shot(mc);                                   // carte « K.O. »
            case 290 -> shot(mc);                                   // inconscient + /hopital
            case 300 -> KoClient.update(ko(2, 1, 95, 180, false));
            case 380 -> shot(mc);                                   // épuisement de chakra (bleu)
            case 390 -> { KoClient.event(KoClient.EV_HOPITAL); KoClient.update(ata(2, 0.05f, 1, 20, false, true)); }
            case 402 -> shot(mc);                                   // carte « Hôpital »
            case 490 -> shot(mc);                                   // ATA pleine + peur
            case 500 -> KoClient.update(ata(2, 0.6f, 12, 20, true, true));
            case 560 -> shot(mc);                                   // repos en cours
            case 570 -> { KoClient.event(KoClient.EV_RETABLI); KoClient.update(ata(0, 0f, 0, 0, false, true)); }
            case 582 -> shot(mc);                                   // carte « Rétabli » + peur résiduelle
            case 640 -> { KoClient.event(KoClient.EV_DEFAITE); KoClient.update(null); }
            case 652 -> shot(mc);                                   // carte « Défaite »
            case 700 -> c.sendCommand("kill @e[tag=ko_test]");
            case 720 -> mc.stop();
            default -> { }
        }
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
