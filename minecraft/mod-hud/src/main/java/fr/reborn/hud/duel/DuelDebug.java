package fr.reborn.hud.duel;

import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai du duel, <b>inactif en production</b> ({@code REBORN_DUEL_DEBUG=1}, monde solo de dev, jamais de
 * serveur) : rejoue les messages du canal {@code reborn:duel} (invitation, survol, attente, compte à rebours, duel en
 * cours, éloignement, fins, menu d'interaction) et capture chaque visuel dans run/screenshots/duel_*.png, puis ferme
 * le jeu.
 */
public final class DuelDebug {

    private static int t = -1;

    private DuelDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_DUEL_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(DuelDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        var im = InteractionMode.INSTANCE;
        switch (t) {
            case 20 -> {
                c.sendCommand("time set 1000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("tp @s ~ ~ ~ -90 5");
            }
            case 40 -> {
                mc.gui.hud.getChat().clearMessages(false);
                DuelClient.debugReceive("{\"t\":\"invit\",\"de\":\"Kazuki_Uchiha\",\"nom\":\"Kazuki Uchiha\",\"s\":30}");
            }
            case 60 -> shot(mc, "duel_01_invitation");
            case 62 -> { double[] p = DuelHud.debugCentre(0); if (p != null) im.debugCursor(p[0] - 2, p[1]); }
            case 72 -> shot(mc, "duel_02_invitation_survol_clic");
            case 74 -> {
                im.debugClose();
                DuelClient.debugReceive("{\"t\":\"invit_fin\",\"de\":\"Kazuki_Uchiha\"}");
                DuelClient.debugReceive("{\"t\":\"attente\",\"a\":\"Ren_Hyuga\",\"nom\":\"Ren Hyūga\",\"s\":30}");
            }
            case 100 -> shot(mc, "duel_03_attente_reponse");
            case 104 -> {
                DuelClient.debugReceive("{\"t\":\"attente_fin\"}");
                DuelClient.debugReceive("{\"t\":\"debut\",\"adv\":\"Kazuki Uchiha\",\"advId\":\"Kazuki_Uchiha\",\"cr\":3,"
                    + "\"duree\":300,\"tech\":true}");
            }
            case 112 -> shot(mc, "duel_04_compte_3");
            case 132 -> shot(mc, "duel_05_compte_2");
            case 152 -> shot(mc, "duel_06_compte_1");
            case 170 -> shot(mc, "duel_07_combat");
            case 180 -> DuelClient.debugReceive("{\"t\":\"etat\",\"d\":6.2,\"max\":30,\"reste\":296}");
            case 200 -> shot(mc, "duel_08_en_cours");
            case 202 -> {
                DuelClient.debugReceive("{\"t\":\"etat\",\"d\":26.4,\"max\":30,\"reste\":251}");
                double[] p = DuelHud.debugCentre(2);
                if (p != null) im.debugCursor(p[0] - 2, p[1]);
            }
            case 214 -> shot(mc, "duel_09_eloignement_survol_abandon");
            case 216 -> {
                im.debugClose();
                DuelClient.debugReceive("{\"t\":\"fin\",\"issue\":\"victoire\",\"adv\":\"Kazuki Uchiha\",\"raison\":\"à bout de forces\"}");
            }
            case 236 -> shot(mc, "duel_10_fin_victoire");
            case 240 -> DuelClient.debugReceive("{\"t\":\"fin\",\"issue\":\"defaite\",\"adv\":\"Kazuki Uchiha\",\"raison\":\"abandon\"}");
            case 260 -> shot(mc, "duel_11_fin_defaite");
            case 264 -> DuelClient.debugReceive("{\"t\":\"fin\",\"issue\":\"annule\",\"adv\":\"Kazuki Uchiha\","
                + "\"raison\":\"intervention de Raiden_FR\"}");
            case 284 -> shot(mc, "duel_12_fin_annule");
            case 288 -> {
                DuelClient.clear();
                im.toggle();
            }
            case 290 -> im.debugOpen("Kazuki_Uchiha", InteractionMenus.forPlayer("Kazuki_Uchiha"), 8);
            case 300 -> shot(mc, "duel_13_menu_proposer");
            case 302 -> {
                im.debugClose();
                DuelClient.debugReceive("{\"t\":\"invit\",\"de\":\"Kazuki_Uchiha\",\"nom\":\"Kazuki Uchiha\",\"s\":30}");
                im.toggle();
            }
            case 304 -> im.debugOpen("Kazuki_Uchiha", InteractionMenus.forPlayer("Kazuki_Uchiha"), 8);
            case 314 -> shot(mc, "duel_14_menu_invite_recue");
            case 316 -> { im.debugClose(); DuelClient.clear(); }
            case 330 -> mc.stop();
            default -> { }
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
