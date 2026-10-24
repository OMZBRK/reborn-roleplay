package fr.reborn.hud.boussole;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.map.MapData;
import fr.reborn.hud.nourriture.FaimCamera;
import fr.reborn.hud.nourriture.NourritureClient;
import fr.reborn.hud.notif.NotifClient;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.ui.HudEditScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.util.Locale;

/**
 * Banc d'essai du point 4 « HUD » du Game Plan (19/10), <b>inactif en production</b> ({@code REBORN_HUD4_DEBUG=1},
 * monde solo de dev) : boussole activée avec le marqueur personnel et des lieux proches, zone d'effets (cinq effets et
 * leurs minuteurs, nom à l'apparition puis compact), faim à 15 % (Affamé) et à 0 % (FAIM : caméra qui tangue, film
 * MP4 de 6 s si {@code REBORN_HUD4_MP4=<fichier.mp4>} — ffmpeg filme la fenêtre « {@code REBORN_HUD4_TITRE} »,
 * « 26.2* Reborn » par défaut), éditeur de HUD montrant les deux nouveaux éléments → {@code run/screenshots/hud4_*.png},
 * puis ferme le jeu. Les réglages des éléments, l'échelle d'interface, le mode de jeu, l'heure et la météo sont rendus à
 * la fin. Point de vue : {@code REBORN_HUD4_POS="x y z lacet tangage"} (jamais de coordonnées dans le dépôt).
 */
public final class HudQuatreDebug {

    private static int t = -1;
    private static HudElementState effAvant, bouAvant;
    private static int echelleAvant = -1;
    private static String mode = "survival";
    private static long heure = -1;
    private static Process film;

    private HudQuatreDebug() {}

    public static void init() {
        if (System.getenv("REBORN_HUD4_DEBUG") == null) return;
        ClientTickEvents.END_CLIENT_TICK.register(HudQuatreDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t < 20 && mc.player.isDeadOrDying() && t % 5 == 0) { mc.player.respawn(); mc.setScreenAndShow(null); }
        double px = mc.player.getX(), pz = mc.player.getZ();
        // la mise en place à t = 20, puis 80 ticks de chargement du terrain avant la première scène
        int u = t <= 21 ? t : t >= 110 ? t - 80 : -1;
        // cap tenu pendant les scènes de la boussole (le tp ne règle pas toujours la caméra épaule) : regard vers le nord-nord-ouest
        if (u >= 30 && u <= 92) {
            mc.player.setYRot(160f); mc.player.setYHeadRot(160f); mc.player.setXRot(8f);
            fr.reborn.hud.camera.RebornCamera.INSTANCE.initOrientation(160f, 8f);   // caméra épaule (orbite découplée)
        }
        switch (u) {
            case 20 -> {
                RebornSession.debugForceRp = true;
                var cfg = RebornHudClient.config();
                effAvant = cfg.stateOf(HudElement.EFFETS);
                bouAvant = cfg.stateOf(HudElement.BOUSSOLE);
                cfg.setState(HudElement.EFFETS, HudElement.EFFETS.defaultState());
                cfg.setState(HudElement.BOUSSOLE, HudElement.BOUSSOLE.defaultState().withVisible(true));
                echelleAvant = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeGui();
                if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
                var srv = mc.getSingleplayerServer();
                if (srv != null) heure = srv.overworld().getOverworldClockTime();
                c.sendCommand("gamemode creative");
                vol(mc, true);
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                String pos = System.getenv("REBORN_HUD4_POS");
                c.sendCommand(pos != null && !pos.isBlank() ? "tp @s " + pos.trim() : "tp @s ~ ~ ~ 20 8");
                VitalsFeed.update(82, 100, 64, 100);
                VitalsFeed.debugSatiete(0.72f, 0f, 0, 0);
            }
            case 21, 30 -> vol(mc, true);
            // ------------------------------------------------------------ boussole (option activée), marqueur, lieux
            case 40 -> {
                // marqueur à ~340 m un peu à droite de ce que voit la caméra, deux lieux proches (un regardé, un sur le côté)
                double lac = Math.toRadians(lacet(mc));
                double fx = -Math.sin(lac), fz = Math.cos(lac);   // vers où l'on regarde (lacet de la caméra)
                double rx = -fz, rz = fx;                         // à droite
                Reperes.poserMarqueur(px + fx * 300 + rx * 190, pz + fz * 300 + rz * 190, "Mon marqueur");
                MapData.update(String.format(Locale.ROOT, "{\"map\":\"konoha\",\"title\":\"Konoha\",\"here\":true,\"canTp\":false,"
                    + "\"places\":[{\"id\":\"ichiraku\",\"name\":\"Ichiraku\",\"type\":\"LIEU\",\"x\":%d,\"z\":%d},"
                    + "{\"id\":\"porte\",\"name\":\"Grande Porte\",\"type\":\"PORTE\",\"x\":%d,\"z\":%d}]}",
                    Math.round(px + fx * 140 - rx * 6), Math.round(pz + fz * 140 - rz * 6),
                    Math.round(px - fx * 60 - rx * 150), Math.round(pz - fz * 60 - rz * 150)));
            }
            case 60 -> {
                System.out.println("[hud4-banc] lacet joueur " + mc.player.getYRot() + " · vue " + lacet(mc));
                shot(mc, "hud4_01_boussole_marqueur_lieu");
            }
            case 62 -> {
                // le marqueur droit devant : sa distance s'écrit sous le filet (les lieux restent sur le côté)
                double lac = Math.toRadians(lacet(mc));
                double fx = -Math.sin(lac), fz = Math.cos(lac);
                Reperes.poserMarqueur(px + fx * 340 + fz * 12, pz + fz * 340 - fx * 12, "Mon marqueur");
                MapData.clear();
            }
            case 90 -> shot(mc, "hud4_02_boussole_vers_marqueur");
            // ------------------------------------------------------------ zone d'effets : cinq effets
            case 92 -> {
                MapData.clear();
                EtatClient.recevoir("{\"v\":1,\"seq\":10,\"p\":\"LIBRE\",\"c\":{\"vital\":\"VALIDE\",\"ctrl\":\"LIBRE\",\"cadre\":\"MONDE\"},"
                    + "\"cap\":{},\"mods\":[{\"id\":\"ata\",\"n\":1,\"fin\":0}]}");
                NourritureClient.debugModifs("{\"mods\":[{\"id\":\"regeneration\",\"nom\":\"Régénération\",\"ic\":\"regeneration\","
                    + "\"b\":1,\"val\":\"×2\",\"reste\":252,\"duree\":300},{\"id\":\"pilule_soldat\",\"nom\":\"Pilule du soldat\","
                    + "\"ic\":\"pilule\",\"b\":1,\"val\":\"+40 %\",\"reste\":165,\"duree\":180},{\"id\":\"poison\",\"nom\":\"Poison\","
                    + "\"ic\":\"poison\",\"b\":0,\"val\":\"pas de régénération\",\"reste\":84,\"duree\":120},"
                    + "{\"id\":\"meditation\",\"nom\":\"Méditation\",\"ic\":\"meditation\",\"b\":1,\"val\":\"×1,25\",\"reste\":512,\"duree\":600}]}");
            }
            case 112 -> shot(mc, "hud4_03_effets_apparition");
            case 180 -> shot(mc, "hud4_04_effets_minuteurs");
            // ------------------------------------------------------------ prison, menottes, douleur
            case 182 -> {
                NourritureClient.debugModifs("{\"mods\":[]}");
                EtatClient.recevoir("{\"v\":1,\"seq\":11,\"p\":\"PRISON\",\"c\":{\"vital\":\"DOULEUR\",\"ctrl\":\"LIBRE\",\"cadre\":\"PRISON\"},"
                    + "\"cap\":{},\"mods\":[{\"id\":\"prison\",\"n\":1,\"fin\":1745000},{\"id\":\"menottes\",\"n\":1,\"fin\":86000000}]}");
            }
            case 206 -> shot(mc, "hud4_05_prison_menottes_douleur");
            // ------------------------------------------------------------ faim à 15 % : Affamé
            case 208 -> {
                EtatClient.recevoir("{\"v\":1,\"seq\":12,\"p\":\"LIBRE\",\"c\":{\"vital\":\"VALIDE\",\"ctrl\":\"LIBRE\",\"cadre\":\"MONDE\"},"
                    + "\"cap\":{},\"mods\":[{\"id\":\"affame\",\"n\":1,\"fin\":0}]}");
                VitalsFeed.debugSatiete(0.14f, 0f, 0, VitalsFeed.F_AFFAME);
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"palier\",\"ic\":\"affame\",\"gras\":\"Affamé\",\"texte\":\"va manger\"}");
            }
            case 228 -> shot(mc, "hud4_06_faim_15_affame");
            case 290 -> shot(mc, "hud4_07_faim_15_compact");
            // ------------------------------------------------------------ faim à 0 % : FAIM, caméra qui tangue
            case 292 -> {
                EtatClient.recevoir("{\"v\":1,\"seq\":13,\"p\":\"LIBRE\",\"c\":{\"vital\":\"VALIDE\",\"ctrl\":\"LIBRE\",\"cadre\":\"MONDE\"},"
                    + "\"cap\":{\"spr\":0,\"mob\":0},\"mods\":[{\"id\":\"faim\",\"n\":2,\"fin\":0}],"
                    + "\"raison\":{\"spr\":\"Tu as trop faim.\",\"mob\":\"Tu as trop faim.\"}}");
                VitalsFeed.debugSatiete(0f, 0f, 0, VitalsFeed.F_FAIM);
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"refus\",\"ic\":\"faim\",\"gras\":\"Faim\",\"texte\":\"ni sprint ni dash\"}");
            }
            case 336 -> {
                shot(mc, "hud4_08_faim_0");
                filmer(mc);
            }
            case 480 -> arreterFilm();
            // ------------------------------------------------------------ éditeur de HUD : les deux éléments
            case 484 -> {
                EtatClient.clear();
                VitalsFeed.debugSatiete(0.72f, 0f, 0, 0);
                mc.setScreenAndShow(new HudEditScreen(null));
            }
            case 490 -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugSelectionner(HudElement.EFFETS); }
            case 510 -> shot(mc, "hud4_09_editeur_effets");
            case 512 -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugSelectionner(HudElement.BOUSSOLE); }
            case 532 -> shot(mc, "hud4_10_editeur_boussole");
            case 534 -> {
                mc.setScreenAndShow(null);
                var cfg = RebornHudClient.config();
                if (effAvant != null) cfg.setState(HudElement.EFFETS, effAvant);
                if (bouAvant != null) cfg.setState(HudElement.BOUSSOLE, bouAvant);
                cfg.save();
                Reperes.clear();
                NourritureClient.clear();
                VitalsFeed.clear();
                EtatClient.clear();
                FaimCamera.debugFaim = false;
                if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
                if (heure >= 0) c.sendCommand("time set " + heure);
                vol(mc, false);
                RebornSession.debugForceRp = false;
            }
            case 550 -> c.sendCommand("gamemode " + mode);
            case 556 -> mc.stop();
            default -> { }
        }
    }

    /** Film de la caméra qui tangue (ffmpeg, fenêtre du jeu, 6 s à 30 i/s), si REBORN_HUD4_MP4 est donné. */
    private static void filmer(Minecraft mc) {
        String out = System.getenv("REBORN_HUD4_MP4");
        if (out == null || out.isBlank()) return;
        String titre = System.getenv().getOrDefault("REBORN_HUD4_TITRE", "26.2* Reborn");
        try {
            film = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "gdigrab", "-framerate", "30", "-t", "6",
                "-i", "title=" + titre, "-vf", "scale=1280:-2", "-c:v", "libx264", "-pix_fmt", "yuv420p", out)
                .redirectErrorStream(true).redirectOutput(new File(mc.gameDirectory, "hud4_ffmpeg.log")).start();
        } catch (Exception e) {
            film = null;
        }
    }

    private static void arreterFilm() {
        if (film == null) return;
        try {
            film.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        film = null;
    }

    /** Lacet de ce que l'on voit : l'orbite de la caméra épaule si elle est active, sinon le regard du joueur. */
    private static double lacet(Minecraft mc) {
        var rc = fr.reborn.hud.camera.RebornCamera.INSTANCE;
        return rc.isEnabled() ? rc.camYaw() : mc.player.getYRot();
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
