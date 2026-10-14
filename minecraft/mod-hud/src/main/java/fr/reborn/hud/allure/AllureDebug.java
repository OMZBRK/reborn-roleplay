package fr.reborn.hud.allure;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Banc des allures, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_ALLURE_DEBUG=1} (client de dev,
 * solo, sans plugin : l'endurance est simulée par {@link Allure}). Pose une piste dans le ciel au-dessus du spawn, fait
 * marcher, trotter puis sprinter le joueur (touches simulées), mesure les vitesses, capture chaque allure, la fin du
 * sprint, l'essoufflement et la récupération dans {@code run/screenshots/allure_*.png}, nettoie la piste et ferme le jeu.
 */
public final class AllureDebug {

    /** Départ de la piste : au-dessus du point d'apparition (chunks déjà chargés, pas d'écran de chargement). */
    private static int X, Y, Z;
    private static int ticks = -1;
    private static double sx, sz;
    private static int sDebut;
    private static RebornCamera.Mode modeAvant;
    private static final StringBuilder MESURES = new StringBuilder();

    private AllureDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_ALLURE_DEBUG"))) return;
        Allure.INSTANCE.debug = true;
        ClientTickEvents.END_CLIENT_TICK.register(AllureDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        Allure a = Allure.INSTANCE;
        if (ticks > 85) mc.gui.hud.getChat().clearMessages(false);   // retours de commandes hors des captures
        boolean avance = (ticks >= 100 && ticks < 172) || (ticks >= 180 && ticks < 252) || (ticks >= 260 && ticks < 420);
        mc.options.keyUp.setDown(avance);
        // touche de sprint : maintenue (trot) ; puis appui, relâche, second appui maintenu (sprint)
        mc.options.keySprint.setDown((ticks >= 180 && ticks < 252) || (ticks >= 260 && ticks < 262)
            || (ticks >= 264 && ticks < 400));
        if (ticks < 40 && ticks % 5 == 0 && mc.player.isDeadOrDying()) {   // sauvegarde laissée morte : on réapparaît
            mc.player.respawn();
            mc.setScreenAndShow(null);
        }
        switch (ticks) {
            case 20 -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                fr.reborn.hud.runtime.VitalsFeed.update(100, 100, 80, 100);
                c.sendCommand("gamerule sendCommandFeedback false");
                c.sendCommand("gamerule send_command_feedback false");
                c.sendCommand("time set 1000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode creative");
                c.sendCommand("effect clear @s");
                gamerulesChute(c, false);
            }
            case 40 -> {
                X = (int) Math.floor(mc.player.getX());
                Y = (int) Math.floor(mc.player.getY()) + 30;
                Z = (int) Math.floor(mc.player.getZ()) - 30;
                c.sendCommand("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 4) + " " + (Y - 1) + " " + (Z + 70)
                    + " minecraft:polished_andesite");
                c.sendCommand("fill " + (X - 4) + " " + Y + " " + (Z - 4) + " " + (X + 4) + " " + (Y + 4) + " " + (Z + 70)
                    + " minecraft:air");
            }
            case 90 -> {
                depart(c);
                c.sendCommand("gamemode survival");
                // caméra Minecraft de face (le temps du banc, rien n'est enregistré dans les préférences)
                modeAvant = RebornCamera.INSTANCE.mode();
                RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.gui.hud.getChat().clearMessages(false);
            }
            case 130, 210, 285 -> debutMesure(mc);
            case 150 -> { finMesure(mc, "marche"); shot(mc, "allure_01_marche"); }
            case 175, 255 -> depart(c);
            case 230 -> { finMesure(mc, "trot"); shot(mc, "allure_02_trot"); }
            case 263 -> a.debugDoubleAppui = true;          // second appui : sprint
            case 305 -> { finMesure(mc, "sprint"); shot(mc, "allure_03_sprint"); }
            case 312 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 318 -> shot(mc, "allure_03b_sprint_dos");
            case 320 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 335 -> shot(mc, "allure_04_sprint_fin");
            case 378 -> shot(mc, "allure_05_essouffle");
            case 440 -> shot(mc, "allure_06_recuperation");
            case 450 -> {
                if (modeAvant != null) RebornCamera.INSTANCE.setMode(modeAvant);
                c.sendCommand("gamemode creative");
                c.sendCommand("tp @s " + X + " " + (Y - 30) + " " + (Z + 30));
                gamerulesChute(c, true);
                c.sendCommand("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 4) + " " + (Y - 1) + " " + (Z + 70)
                    + " minecraft:air");
                try {
                    Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve("allure_vitesses.txt");
                    Files.writeString(out, MESURES.toString(), StandardCharsets.UTF_8);
                } catch (Exception ignored) { }
            }
            case 470 -> mc.stop();
            default -> { }
        }
    }

    /** Dégâts de chute coupés le temps du banc (les deux orthographes de la règle selon la version). */
    private static void gamerulesChute(net.minecraft.client.multiplayer.ClientPacketListener c, boolean on) {
        c.sendCommand("gamerule fallDamage " + on);
        c.sendCommand("gamerule fall_damage " + on);
    }

    private static void depart(net.minecraft.client.multiplayer.ClientPacketListener c) {
        c.sendCommand("tp @s " + X + " " + Y + " " + Z + " 0 12");
    }

    private static void debutMesure(Minecraft mc) {
        sx = mc.player.getX();
        sz = mc.player.getZ();
        sDebut = ticks;
    }

    private static void finMesure(Minecraft mc, String nom) {
        double d = Math.hypot(mc.player.getX() - sx, mc.player.getZ() - sz);
        double s = (ticks - sDebut) / 20.0;
        MESURES.append(String.format(Locale.ROOT, "%-7s %.2f blocs/s  (attribut %.4f, allure %s, endurance %.0f %%)%n",
            nom, d / s, mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
            Allure.INSTANCE.etat(), Allure.INSTANCE.endurance() * 100));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
