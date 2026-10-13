package fr.reborn.hud.audit;

import fr.reborn.hud.staff.StaffClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Aperçu avant publication, <b>inactif en production</b> ({@code REBORN_FEATURE_PREVIEW=1}, monde solo de dev) :
 * rejoue les messages serveur du lot en cours et capture chaque visuel dans run/screenshots/preview_*.png.
 * Lot actuel : rouleaux SNR pour les parchemins (en main, 3e personne, au sol, barre d'action).
 */
public final class FeaturePreview {

    private static int t = -1;

    private FeaturePreview() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_FEATURE_PREVIEW"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(FeaturePreview::tick);
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static final String RANKS = "dcbas";

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        switch (t) {
            case 30 -> {
                cmd(mc, "time set 6000");
                cmd(mc, "weather clear");
                cmd(mc, "gamemode creative");
                cmd(mc, "clear @s");
                cmd(mc, "tp @s ~ ~ ~ -90 30");
                for (int k = 0; k < 5; k++) {
                    cmd(mc, "item replace entity @s hotbar." + k + " with paper[item_model=\"reborn:parchemin_" + RANKS.charAt(k) + "\"]");
                }
                mc.gui.hud.getChat().clearMessages(false);
            }
            // Rouleaux SNR : en main (1re personne), un par rang
            case 50 -> mc.player.getInventory().setSelectedSlot(0);
            case 60 -> shot(mc, "preview_01_main_rang_d");
            case 62 -> mc.player.getInventory().setSelectedSlot(2);
            case 72 -> shot(mc, "preview_02_main_rang_b");
            case 74 -> mc.player.getInventory().setSelectedSlot(4);
            case 84 -> shot(mc, "preview_03_main_rang_s");
            // 3e personne de face, rang S puis rang A en main
            case 86 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            case 100 -> shot(mc, "preview_04_tiers_rang_s");
            case 102 -> mc.player.getInventory().setSelectedSlot(3);
            case 112 -> shot(mc, "preview_05_tiers_rang_a");
            // Au sol, les cinq rangs alignés
            case 114 -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                cmd(mc, "tp @s ~ ~ ~ -90 45");
                for (int k = 0; k < 5; k++) {
                    cmd(mc, "summon item ~2.2 ~0.1 ~" + (k - 2) * 0.7 + " {NoGravity:1b,PickupDelay:32767,Age:-32768,Motion:[0d,0d,0d],"
                            + "Item:{id:\"paper\",count:1,components:{\"minecraft:item_model\":\"reborn:parchemin_" + RANKS.charAt(k) + "\"}}}");
                }
            }
            case 140 -> shot(mc, "preview_06_sol_cinq_rangs");
            case 150 -> { cmd(mc, "kill @e[type=item]"); mc.stop(); }
            default -> { }
        }
    }

    private static void inject(String json) {
        try {
            var m = StaffClient.class.getDeclaredMethod("receive", String.class);
            m.setAccessible(true);
            m.invoke(null, json);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static final String SNAP = """
            {"t":"snap","grade":2,"gradeName":"Modérateur","staffOnline":3,
             "alerts":[
              {"id":"kill-7","kind":"mort","uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","rank":"Genin","village":"konoha","online":true,
               "motif":"Mise à mort RP demandée par Kazuki Uchiha","ctx":["world 640 70 -120","valider ou refuser"],"score":0},
              {"id":"aide-2","kind":"aide","uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","rank":"Chūnin","online":true,
               "motif":"Appel à l'aide · à terre","ctx":["world 700 66 -180","il y a 2 min"],"score":0}],
             "players":[],"chat":[
              {"time":"21:02","grade":0,"name":"Système","text":"Mort RP à trancher : Ren Hyūga (demandée par Raiden_FR).","sys":true}],
             "journal":[]}
            """;
}
