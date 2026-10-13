package fr.reborn.hud.yeux;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai des yeux, <b>inactif en production</b> ({@code REBORN_YEUX_DEBUG=1}, monde solo de dev) : ouvre le
 * prélèvement puis la greffe dans des états représentatifs de chaque phase, la cécité, et capture chaque visuel dans
 * run/screenshots/yeux_*.png, puis ferme le jeu.
 */
public final class YeuxDebug {

    private static int t = -1;

    private YeuxDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_YEUX_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(YeuxDebug::tick);
    }

    private record Shot(String name, ChirurgieScreen.Mode mode, String kind, boolean left, int phase, long ms,
                        int errors, int integrity, double mx, double my) {}

    private static final Shot[] SHOTS = {
            new Shot("yeux_01_prelevement_inciser", ChirurgieScreen.Mode.PRELEVEMENT, "sharingan", true, 0, 1150, 0, 100, 0, 0),
            new Shot("yeux_02_prelevement_detacher", ChirurgieScreen.Mode.PRELEVEMENT, "sharingan", true, 1, 1300, 1, 90, 0, 0),
            new Shot("yeux_03_prelevement_sceller", ChirurgieScreen.Mode.PRELEVEMENT, "sharingan", true, 2, 700, 1, 90, 0, 0),
            new Shot("yeux_04_prelevement_reussi", ChirurgieScreen.Mode.PRELEVEMENT, "sharingan", true, 3, 900, 1, 82, 0, 0),
            new Shot("yeux_05_prelevement_byakugan", ChirurgieScreen.Mode.PRELEVEMENT, "byakugan", false, 0, 400, 0, 100, 0, 0),
            new Shot("yeux_06_greffe_incision", ChirurgieScreen.Mode.GREFFE, "sharingan", false, 0, 3800, 1, 100, -1, -1),
            new Shot("yeux_07_greffe_chakra", ChirurgieScreen.Mode.GREFFE, "sharingan", false, 1, 2100, 1, 100, -2, -2),
            new Shot("yeux_08_greffe_sutures", ChirurgieScreen.Mode.GREFFE, "sharingan", false, 2, 900, 2, 100, 0, 0),
            new Shot("yeux_09_greffe_reussie", ChirurgieScreen.Mode.GREFFE, "sharingan", false, 3, 900, 2, 100, 0, 0),
            new Shot("yeux_10_greffe_echouee", ChirurgieScreen.Mode.GREFFE, "normal", true, 3, 900, 5, 100, 0, 0),
    };

    private static final int STEP = 30;

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 13000");
            mc.player.connection.sendCommand("tp @s ~ ~ ~ -90 5");
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (t < 40) return;
        int i = (t - 40) / STEP, k = (t - 40) % STEP;
        if (i < SHOTS.length) {
            Shot s = SHOTS[i];
            if (k == 0) {
                ChirurgieScreen sc = new ChirurgieScreen(s.mode(), "Itachi Uchiha", s.left(), s.kind(), "");
                mc.setScreenAndShow(sc);
            }
            if (k == 3 && mc.gui.screen() instanceof ChirurgieScreen sc) {
                double mx = s.mx(), my = s.my();
                int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
                if (mx == -1) { mx = w / 2.0 - 30; my = h / 2.0 - 6; }       // près de la pointe du scalpel
                if (mx == -2) { mx = w / 2.0; my = h / 2.0 - 4; }
                sc.debugState(s.phase(), s.ms(), s.errors(), s.integrity(), mx, my);
            }
            if (k == 20) shot(mc, s.name());
            return;
        }
        int j = i - SHOTS.length;
        if (j == 0 && k == 0) { mc.setScreenAndShow(null); CeciteHud.set(true, false, false); }
        if (j == 0 && k == 20) shot(mc, "yeux_11_cecite_gauche");
        if (j == 1 && k == 0) CeciteHud.set(false, true, true);
        if (j == 1 && k == 20) shot(mc, "yeux_12_cecite_droite_bandage");
        var im = fr.reborn.hud.interaction.InteractionMode.INSTANCE;
        if (j == 2 && k == 0) { CeciteHud.set(false, false, false); im.toggle(); }
        if (j == 2 && k == 2) im.debugOpen("Itachi_Uchiha", fr.reborn.hud.interaction.InteractionMenus.forBody("Itachi_Uchiha",
                new fr.reborn.hud.ko.CorpsClient.Body(3, 0)), 3);
        if (j == 2 && k == 20) shot(mc, "yeux_13_menu_corps_prelever");
        if (j == 3 && k == 0) im.debugOpen("Ren_Hyuga", fr.reborn.hud.interaction.InteractionMenus.forBody("Ren_Hyuga",
                new fr.reborn.hud.ko.CorpsClient.Body(2, 0)), 7);
        if (j == 3 && k == 20) shot(mc, "yeux_14_menu_ko_soins");
        if (j == 4 && k == 0) { im.debugClose(); mc.stop(); }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
