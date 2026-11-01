package fr.reborn.hud.anchor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc de l'ancre de tête, <b>inactif en production</b> : seulement avec {@code REBORN_TETE_ANCRE_DEBUG=banc} (client de
 * dev, monde solo). Quatre mannequins (debout, accroupi, à terre « ramper », allongé sans lit, ce dernier soulevé de
 * 0,45 bloc pour sortir de la couche de neige), croix de {@link TeteAncre#debugCroix} : vue de face, vue épaule (soi
 * compris), autre angle, vue plongeante, puis en 1re personne un mur de pierre devant le mannequin debout (croix pâle :
 * masqué) et une vitre devant l'accroupi (croix vive : le verre ne masque pas). Le monde n'est pas modifié : les murs
 * commencent un bloc au-dessus du sol et sont retirés. Captures {@code run/screenshots/ancre_tete_*.png}, puis le jeu
 * se ferme. Prouve aussi que les mixins de la capture s'appliquent au lancement.
 */
final class TeteAncreBanc {

    private static int ticks = -1;

    private TeteAncreBanc() {}

    static void init() {
        if (!"banc".equals(System.getenv("REBORN_TETE_ANCRE_DEBUG"))) return;
        TeteAncre.debugCroix = true;
        ClientTickEvents.END_CLIENT_TICK.register(TeteAncreBanc::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        if (ticks > 45 && ticks % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode creative");
                c.sendCommand("execute positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ -90 8");
                c.sendCommand("execute as @e[tag=tete_test] run tp @s ~ -300 ~");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 42 -> {   // en vol (créatif) : aucune chute pendant le banc
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }
            case 60 -> {
                c.sendCommand("execute rotated ~ 0 run summon mannequin ^-3 ^ ^5 {Rotation:[90f,0f],Tags:[\"tete_test\"],hide_description:1b}");
                c.sendCommand("execute rotated ~ 0 run summon mannequin ^-1 ^ ^5 {Rotation:[90f,0f],pose:\"crouching\",Tags:[\"tete_test\"],hide_description:1b}");
                c.sendCommand("execute rotated ~ 0 run summon mannequin ^1 ^ ^5 {Rotation:[90f,0f],pose:\"swimming\",Tags:[\"tete_test\"],hide_description:1b}");
                c.sendCommand("execute rotated ~ 0 run summon mannequin ^3.5 ^0.45 ^5 {Rotation:[90f,0f],pose:\"sleeping\",NoGravity:1b,Tags:[\"tete_test\"],hide_description:1b}");
            }
            case 110 -> photo(mc, "ancre_tete_01_face");
            case 112 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 150 -> photo(mc, "ancre_tete_02_epaule");
            case 152 -> c.sendCommand("tp @s ~ ~ ~ -55 22");
            case 180 -> photo(mc, "ancre_tete_03_angle");
            case 182 -> c.sendCommand("tp @s ~ ~ ~ -90 40");   // la caméra épaule passe au-dessus
            case 210 -> photo(mc, "ancre_tete_04_plongee");
            case 212 -> {
                c.sendCommand("tp @s ~ ~ ~ -90 8");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                c.sendCommand("execute rotated ~ 0 run fill ^-2 ^1 ^4 ^-3 ^2 ^4 minecraft:stone");
                c.sendCommand("execute rotated ~ 0 run fill ^0 ^1 ^4 ^-1 ^2 ^4 minecraft:glass");
            }
            case 250 -> photo(mc, "ancre_tete_05_pierre_verre");
            case 252 -> {
                c.sendCommand("execute rotated ~ 0 run fill ^0 ^1 ^4 ^-3 ^2 ^4 minecraft:air");
                c.sendCommand("execute as @e[tag=tete_test] run tp @s ~ -300 ~");
                mc.player.getAbilities().flying = false;
                mc.player.onUpdateAbilities();
                TeteAncre.debugCroix = false;
            }
            case 270 -> mc.stop();
            default -> { }
        }
    }

    private static void photo(Minecraft mc, String nom) {
        Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
