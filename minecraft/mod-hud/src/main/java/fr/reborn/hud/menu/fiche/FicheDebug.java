package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc de captures de la fiche, <b>inactif en production</b>.
 * <ul>
 *   <li>{@code REBORN_FICHE_DEBUG=1} : injecte Ren Hyūga au format v2 complet (StatsData + FicheData), vitals de
 *   démonstration, puis capture fiche_1_identite … fiche_6_identite_jeu, fiche_7_ancien_serveur (JSON sans les clés
 *   v2), et les quatre onglets aux échelles 3 et 4 (identiques grâce à MenuScale).</li>
 *   <li>{@code REBORN_FICHE_DEBUG=tailles} : mêmes cas dans des fenêtres 1706×960 (853×480), 1280×720 et 1366×768
 *   (683×384, compact).</li>
 *   <li>{@code REBORN_FICHE_DEBUG=pid} : la pastille Player ID « N° 00042 » (repos, retour « Copié », échelle 3,
 *   onglet Attributs, 1366×768 compact).</li>
 * </ul>
 * Captures dans run/screenshots/, puis le jeu se ferme. Un seul client à la fois.
 */
public final class FicheDebug {

    private FicheDebug() {}

    private record Shot(String nom, String cas, int echelle, int fw, int fh, boolean ancien) {}

    private static final String[] CAS = {"identite", "attributs", "attributs_repartition", "techniques", "etat", "identite_jeu"};
    private static final int STEP = 30;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int t = -1;
    private static boolean tailles;

    public static void init() {
        String v = System.getenv("REBORN_FICHE_DEBUG");
        if (v == null || v.isBlank()) return;
        tailles = "tailles".equalsIgnoreCase(v.trim());
        if ("pid".equalsIgnoreCase(v.trim())) {
            SHOTS.add(new Shot("fiche_pid_1_identite", "identite", 2, 0, 0, false));
            SHOTS.add(new Shot("fiche_pid_2_copie", "identite_copie", 2, 0, 0, false));
            SHOTS.add(new Shot("fiche_pid_3_s3", "identite", 3, 0, 0, false));
            SHOTS.add(new Shot("fiche_pid_4_attributs", "attributs", 2, 0, 0, false));
            SHOTS.add(new Shot("fiche_pid_5_1366x768", "identite", 2, 1366, 768, false));
        } else if (tailles) {
            int[][] fen = {{1706, 960}, {1280, 720}, {1366, 768}};
            for (int[] f : fen) {
                for (int i = 0; i < CAS.length; i++) {
                    SHOTS.add(new Shot("fiche_" + f[0] + "x" + f[1] + "_" + (i + 1) + "_" + CAS[i], CAS[i], 2, f[0], f[1], false));
                }
            }
        } else {
            for (int i = 0; i < CAS.length; i++) SHOTS.add(new Shot("fiche_" + (i + 1) + "_" + CAS[i], CAS[i], 2, 0, 0, false));
            SHOTS.add(new Shot("fiche_7_ancien_serveur", "identite", 2, 0, 0, true));
            SHOTS.add(new Shot("fiche_7b_ancien_serveur_techniques", "techniques", 2, 0, 0, true));
            SHOTS.add(new Shot("fiche_7c_ancien_serveur_etat", "etat", 2, 0, 0, true));
            for (int s : new int[]{3, 4}) {
                SHOTS.add(new Shot("fiche_s" + s + "_1_identite", "identite", s, 0, 0, false));
                SHOTS.add(new Shot("fiche_s" + s + "_2_attributs", "attributs", s, 0, 0, false));
                SHOTS.add(new Shot("fiche_s" + s + "_3_techniques", "techniques", s, 0, 0, false));
                SHOTS.add(new Shot("fiche_s" + s + "_4_etat", "etat", s, 0, 0, false));
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(FicheDebug::tick);
    }

    private static void injecter(boolean ancien) {
        String json = ancien ? FicheData.demoAncienServeur() : FicheData.DEMO_JSON;
        StatsData.update(json);
        FicheData.update(json);
        VitalsFeed.update(620, 1000, 850, 1000);
        CombatState.INSTANCE.onStamina(25f, 100f, System.currentTimeMillis());
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 13000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (t == 30) injecter(false);
        if (t < 40) return;
        int i = (t - 40) / STEP, k = (t - 40) % STEP;
        if (i >= SHOTS.size()) {
            if (k == 2) mc.setScreenAndShow(null);
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        if (k == 0) {
            mc.setScreenAndShow(null);
            if (s.fw() > 0 && (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh())) {
                mc.getWindow().setWindowed(s.fw(), s.fh());
            }
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
            injecter(s.ancien());
            Repartition.clearSession();
        }
        if (k == 4) {
            FicheScreen sc = new FicheScreen(FicheScreen.IDENTITE);
            mc.setScreenAndShow(sc);
            sc.debugPasserIntro();
        }
        if (k == 6 && mc.gui.screen() instanceof FicheScreen sc) sc.debugCas(s.cas().replace("_copie", ""));
        if (k == 20 && s.cas().endsWith("_copie") && mc.gui.screen() instanceof FicheScreen sc) sc.debugPidCopie();
        if (k == STEP - 3) {
            Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        }
    }
}
