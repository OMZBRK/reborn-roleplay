package fr.reborn.hud.eau;

import fr.reborn.hud.allure.Allure;
import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Banc de la marche sur l'eau, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_EAU_DEBUG=1}
 * (client de dev, solo, sans plugin : le permis est forcé, les allures sont simulées). Cherche une mer ouverte autour
 * du point d'apparition (serveur intégré), y pose le joueur, puis : à l'arrêt, marche, trot, sprint (3ᵉ personne de
 * dos), de face, à la 1ʳᵉ personne (regard vers le bas), saut et retombée, s'enfoncer (accroupi) et remonter, et un « autre joueur »
 * (mannequin) qui traverse — captures {@code run/screenshots/eau_*.png} et images de GIF {@code eaugif_*}. Remet tout
 * en place (position, mode de jeu, heure, météo, caméra) et ferme le jeu.
 */
final class EauDebug {

    private static int t = -1, r = -1;
    private static boolean pauseAvant = true;
    private static volatile BlockPos mer;
    private static volatile boolean recherche;
    private static double ox, oy, oz;
    private static float oyaw, opitch;
    private static String omode = "survival";
    private static RebornCamera.Mode camAvant;
    private static CameraType vueAvant;
    private static int mannequin = -1;
    private static double mx, mz, msurf;
    private static int essais, attente;

    private EauDebug() {}

    static void init() {
        if (!"1".equals(System.getenv("REBORN_EAU_DEBUG"))) return;
        MarcheEauClient.debug = true;
        ClientTickEvents.END_CLIENT_TICK.register(EauDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 30) mc.gui.hud.getChat().clearMessages(false);
        if (t < 40 && t % 5 == 0 && mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreenAndShow(null); }
        if (t == 20) {
            pauseAvant = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;                      // en mémoire seulement, le temps du banc
            ox = mc.player.getX(); oy = mc.player.getY(); oz = mc.player.getZ();
            oyaw = mc.player.getYRot(); opitch = mc.player.getXRot();
            if (mc.gameMode != null && mc.gameMode.getPlayerMode() != null) {
                omode = mc.gameMode.getPlayerMode().getName();
            }
            regles(c, false);
            c.sendCommand("time set 5000");
            c.sendCommand("weather clear");
            c.sendCommand("gamemode creative");
            chercherMer(mc);
        }
        if (t > 20 && r == -1 && mer != null && t % 20 == 0) {
            BlockPos p = mer;
            c.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 15", p.getX() + 0.5, p.getY() + 3, p.getZ() + 0.5));
            r = -200;                                                    // attente des chunks
        }
        if (r < -1) {
            r++;
            // on ne lâche le joueur que quand l'eau est arrivée chez le client (mer lointaine : génération lente)
            if (r == -61 && mer != null && !mc.level.getFluidState(mer).is(FluidTags.WATER) && ++attente < 60) r = -100;
            if (r == -60) mc.player.connection.sendCommand("gamemode survival");   // on se pose sur l'eau
            if (r < -60 && r > -190 && mer != null) {                    // tenu en l'air le temps que les chunks arrivent
                mc.player.setPos(mer.getX() + 0.5, mer.getY() + 2.5, mer.getZ() + 0.5);
                mc.player.setDeltaMovement(0, 0, 0);
            }
            if (r == -2) {
                double s = Ondes.surfaceSous(mc.level, mc.player);
                if (Double.isNaN(s)) {                                   // pas debout sur l'eau : on attend encore
                    org.slf4j.LoggerFactory.getLogger("reborn-hud").info(String.format(Locale.ROOT,
                            "[eau-banc] pas sur l'eau : mer=%s joueur=%.2f %.2f %.2f eau=%b vol=%b fluide=%s",
                            mer, mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isInWater(),
                            mc.player.getAbilities().flying,
                            mc.level.getFluidState(BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.07, mc.player.getZ()))));
                    r = -40;
                    if (++essais > 8) fin(mc);
                    return;
                }
                r = 0;
            }
            return;
        }
        if (r < 0) return;
        r++;
        sequence(mc, c);
    }

    private static void sequence(Minecraft mc, net.minecraft.client.multiplayer.ClientPacketListener c) {
        var o = mc.options;
        boolean avance = (r >= 60 && r < 320) || (r >= 340 && r < 470) || (r >= 490 && r < 520);
        o.keyDown.setDown(r >= 524 && r < 562);                    // à reculons : la traînée de flaques devant soi
        o.keyUp.setDown(avance);
        boolean sprint = (r >= 140 && r < 220) || (r >= 224 && r < 226) || (r >= 228 && r < 320)
                || (r >= 400 && r < 402) || (r >= 404 && r < 470);
        o.keySprint.setDown(sprint);
        o.keyJump.setDown((r >= 575 && r < 578) || (r >= 650 && r < 715));
        o.keyShift.setDown(r >= 620 && r < 640);
        if (r >= 600 && r < 720 && r % 10 == 0) mc.player.setYRot(0f);
        switch (r) {
            case 1 -> {
                camAvant = RebornCamera.INSTANCE.mode();
                vueAvant = o.getCameraType();
                RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
                o.setCameraType(CameraType.THIRD_PERSON_BACK);
                Allure.INSTANCE.banc(false);
                mc.player.setYRot(0f);
                mc.player.setXRot(30f);
            }
            case 50 -> shot(mc, "eau_01_immobile_dos");
            case 120 -> shot(mc, "eau_02_marche_dos");
            case 200 -> shot(mc, "eau_03_trot_dos");
            case 227, 403 -> Allure.INSTANCE.banc(true);           // second appui : sprint
            case 270 -> shot(mc, "eau_04_sprint_dos");
            case 330 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(8f); }
            case 380 -> shot(mc, "eau_05_face_marche");
            case 450 -> shot(mc, "eau_06_face_sprint");
            case 474, 561 -> large(mc, c);                          // retour au large (eau profonde)
            case 480 -> { o.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(80f); }   // regard vers le bas (réf. Shinoda)
            case 515 -> shot(mc, "eau_07_premiere_personne");
            case 556 -> shot(mc, "eau_07b_vers_le_bas_traine");
            case 565 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(20f); }
            case 590 -> shot(mc, "eau_08_retombee");
            case 600 -> shot(mc, "eau_08b_retombee");
            case 636 -> shot(mc, "eau_09_s_enfonce");
            case 722 -> shot(mc, "eau_10_remonte");
            case 724 -> large(mc, c);                               // retour au large pour l'« autre joueur »
            case 730 -> invoquer(mc);
            case 735 -> { o.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0f); mc.player.setXRot(22f); }
            case 775 -> shot(mc, "eau_11_autre_joueur");
            case 795 -> shot(mc, "eau_11b_autre_joueur");
            case 815 -> shot(mc, "eau_11c_autre_joueur");
            case 860 -> fin(mc);
            case 900 -> mc.stop();
            default -> { }
        }
        // images de GIF : marche, sprint de dos, 1re personne, autre joueur
        if ((r >= 80 && r < 140 && r % 2 == 0) || (r >= 236 && r < 300 && r % 2 == 0)
                || (r >= 340 && r < 400 && r % 2 == 0) || (r >= 495 && r < 555 && r % 2 == 0)
                || (r >= 572 && r < 610 && r % 2 == 0) || (r >= 760 && r < 840 && r % 2 == 0)) {
            shot(mc, String.format(Locale.ROOT, "eaugif_%04d", r));
        }
        if (mannequin >= 0 && r > 740 && r < 858) deplacerMannequin(mc);
        // angles tenus à chaque tick : la souris (fenêtre au premier plan) ne dérange pas le banc
        if (r < 860) { mc.player.setXRot(r < 330 ? 30f : r < 480 ? 8f : r < 522 ? 80f : r < 565 ? 62f : r < 735 ? 24f : 22f); mc.player.setYRot(0f); }
        if (mc.gui.screen() != null) mc.setScreenAndShow(null);                // aucun écran pendant le banc (Échap sur perte de focus)
    }

    /** Retour au point de départ, au large (eau profonde), face au nord-sud du banc. */
    private static void large(Minecraft mc, net.minecraft.client.multiplayer.ClientPacketListener c) {
        BlockPos p = mer;
        if (p != null) c.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.2f %.1f 0 %.0f", p.getX() + 0.5,
                p.getY() + 1.2, p.getZ() + 0.5, mc.player.getXRot()));
    }

    /** Cherche une mer ouverte (serveur intégré) : biome océan le plus proche, puis un point d'eau libre sur ±24 blocs. */
    private static void chercherMer(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || recherche) return;
        recherche = true;
        BlockPos depart = mc.player.blockPosition();
        srv.execute(() -> {
            ServerLevel sl = srv.overworld();
            var res = sl.findClosestBiome3d(h -> h.is(BiomeTags.IS_OCEAN), depart, 6400, 32, 64);
            if (res == null) return;
            BlockPos b = res.getFirst();
            int mer0 = sl.getSeaLevel() - 1;
            for (int fond : new int[]{10, 3}) {                          // eau profonde d'abord (pas d'algues)
                for (int ring = 0; ring < 12; ring++) {
                    for (int k = 0; k < Math.max(1, ring * 8); k++) {
                        double a = 2 * Math.PI * k / Math.max(1, ring * 8);
                        int x = b.getX() + (int) (Math.cos(a) * ring * 24), z = b.getZ() + (int) (Math.sin(a) * ring * 24);
                        if (libre(sl, x, z, mer0, fond)) { mer = new BlockPos(x, mer0, z); return; }
                    }
                }
            }
            mer = new BlockPos(b.getX(), mer0, b.getZ());
        });
    }

    private static boolean libre(ServerLevel sl, int x, int z, int y, int fond) {
        for (int dx = -24; dx <= 24; dx += 8) {
            for (int dz = -24; dz <= 40; dz += 8) {
                BlockPos p = new BlockPos(x + dx, y, z + dz);
                if (!sl.getFluidState(p).is(FluidTags.WATER) || !sl.getBlockState(p.above()).isAir()) return false;
                if (sl.getHeight(Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz) > y - fond) return false;
            }
        }
        return true;
    }

    /** « Autre joueur » : un mannequin qui traverse l'eau devant la caméra, déplacé par le serveur intégré. */
    private static void invoquer(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        msurf = Ondes.surfaceSous(mc.level, mc.player);
        if (Double.isNaN(msurf)) msurf = Math.floor(mc.player.getY()) - 0.11;
        mx = mc.player.getX() - 5;
        mz = mc.player.getZ() + 7;
        double y = msurf + 0.02;
        mc.player.connection.sendCommand(String.format(Locale.ROOT,
                "summon mannequin %.2f %.3f %.2f {NoGravity:1b,Rotation:[-90f,0f],Tags:[\"eau_test\"]}", mx, y, mz));
        mannequin = 0;
    }

    private static void deplacerMannequin(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        mx += 0.21;                                                   // ~4,2 blocs/s
        double x = mx, y = msurf + 0.02, z = mz;
        srv.execute(() -> {
            Entity e = null;
            for (Entity q : srv.overworld().getAllEntities()) if (q.entityTags().contains("eau_test")) e = q;
            if (e != null) { e.setPos(x, y, z); e.setYRot(-90f); e.setYHeadRot(-90f); }
        });
    }

    private static void fin(Minecraft mc) {
        var o = mc.options;
        o.keyUp.setDown(false);
        o.keySprint.setDown(false);
        o.keyJump.setDown(false);
        o.keyShift.setDown(false);
        o.keyDown.setDown(false);
        var c = mc.player.connection;
        c.sendCommand("kill @e[tag=eau_test]");
        if (camAvant != null) RebornCamera.INSTANCE.setMode(camAvant);
        if (vueAvant != null) o.setCameraType(vueAvant);
        c.sendCommand("gamemode creative");
        c.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", ox, oy, oz, oyaw, opitch));
        c.sendCommand("gamemode " + omode);
        regles(c, true);
        MarcheEauClient.debug = false;
        mc.options.pauseOnLostFocus = pauseAvant;
        if (EauRendu.mesures > 0) {
            org.slf4j.LoggerFactory.getLogger("reborn-hud").info(String.format(Locale.ROOT,
                    "[eau-banc] rendu de l'eau : %d images, moyenne %.3f ms, pire %.3f ms", EauRendu.mesures,
                    EauRendu.cumul / 1e6 / EauRendu.mesures, EauRendu.pire / 1e6));
            org.slf4j.LoggerFactory.getLogger("reborn-hud").info(String.format(Locale.ROOT,
                    "[eau-banc] %d quads de flaques par image en moyenne", EauRendu.quads / EauRendu.mesures));
        }
        if (r < 860) r = 860;
    }

    /** Retours de commandes et dégâts de chute (les deux orthographes de la règle selon la version). */
    private static void regles(net.minecraft.client.multiplayer.ClientPacketListener c, boolean on) {
        c.sendCommand("gamerule sendCommandFeedback " + on);
        c.sendCommand("gamerule send_command_feedback " + on);
        c.sendCommand("gamerule fallDamage " + on);
        c.sendCommand("gamerule fall_damage " + on);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
