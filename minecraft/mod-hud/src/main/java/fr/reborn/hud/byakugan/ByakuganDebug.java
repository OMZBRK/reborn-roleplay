package fr.reborn.hud.byakugan;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel du Byakugan, <b>inactif en production</b> : ne fait rien sauf si la variable d'environnement
 * {@code REBORN_BYAKUGAN_DEBUG=1} est définie (client de dev lancé avec {@code gradlew runClient}).
 * Dans un monde solo avec les commandes autorisées : fait apparaître un mannequin (modèle joueur) et un zombie devant
 * la caméra, active le Byakugan, prend des captures (activation puis vision établie) et ferme le jeu.
 */
public final class ByakuganDebug {

    private static int ticks = -1;

    private ByakuganDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_BYAKUGAN_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ByakuganDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set day");
                c.sendCommand("weather clear");
                c.sendCommand("difficulty easy");
                c.sendCommand("tp @s ~ ~ ~ 0 0");
                // ménage : entités de test (et celles des premiers essais, sans tag)
                c.sendCommand("kill @e[tag=byak_test]");
                c.sendCommand("kill @e[type=mannequin,distance=..20]");
                c.sendCommand("kill @e[type=cow,distance=..20,nbt={NoAI:1b}]");
                c.sendCommand("kill @e[type=zombie,distance=..20,nbt={NoAI:1b}]");
            }
            case 50 -> {
                c.sendCommand("summon mannequin ^ ^0.2 ^3.4 {NoGravity:1b,Rotation:[180f,0f],Tags:[\"byak_test\"]}");
                c.sendCommand("summon husk ^-2.2 ^0.2 ^5.5 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Rotation:[160f,0f],Tags:[\"byak_test\"]}");
                c.sendCommand("summon cow ^2.6 ^0.2 ^6.5 {NoAI:1b,NoGravity:1b,Tags:[\"byak_test\"]}");
            }
            case 260 -> ByakuganClient.update(true, 40f);   // le chat s'est effacé
            case 265 -> shot(mc);       // activation : flash + lignes radiales
            case 320 -> shot(mc);       // vision établie, de face
            case 330 -> c.sendCommand("tp @s ~ ~ ~ 18 4");
            case 360 -> shot(mc);       // autre angle (flux animé)
            case 370 -> c.sendCommand("kill @e[tag=byak_test]");
            case 380 -> mc.stop();
            default -> { }
        }
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
