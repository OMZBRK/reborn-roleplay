package fr.reborn.hud.meditation;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Banc du halo de méditation, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_MEDITATION_DEBUG=1}
 * (client de dev, solo, sans plugin). Construit à 600 blocs du point de départ trois sols d'essai (herbe, pierre,
 * pente en escalier avec une dalle) en jungle clairsemée (l'herbe vive de la vidéo de référence), assoit le joueur sur
 * un support invisible (comme {@code MeditationManager}) et un mannequin assis plus loin (un « autre joueur », aura
 * violette), allume leurs halos localement, puis capture aux angles de la vidéo (dessus ¾, dos ¾, face) : herbe,
 * film de 8 s (240 images à 30 i/s, horloge de l'effet imposée, caméra qui tourne), pierre, nuit, trois couleurs
 * d'aura (rouge, bleu, or ; sur pierre, de nuit), pente, disparition, et l'écran de choix de la couleur. Captures
 * {@code run/screenshots/med2_*.png} et {@code medfilm_*.png} prises en fin de rendu du monde (sans HUD). Remet tout en
 * place et ferme le jeu ; le coût du rendu est écrit dans le journal ({@code [med-banc]}).
 */
final class MeditationDebug {

    private static int t = -1, r = -1;
    private static boolean pauseAvant = true;
    private static double ox, oy, oz;
    private static float oyaw, opitch;
    private static String omode = "survival";
    private static RebornCamera.Mode camAvant;
    private static CameraType vueAvant;
    private static volatile BlockPos base;
    private static boolean demande;
    private static float yaw, pitch = 28f;

    private static String photo;
    private static boolean film;
    /** {@code REBORN_MEDITATION_DEBUG=pose} : essais de pose {@code run/med_essai_<k>.json}, aux 4 angles calés de la vidéo. */
    private static boolean pose;
    private static final boolean FILM_POSE = "1".equals(System.getenv("REBORN_MEDITATION_FILM"));
    /** Essais de pose : {lacet, tangage} des vues face, dessus ¾, dos ¾, profil gauche — calées sur la vidéo. */
    private static final float[][] VUES = {{5f, -10f}, {47.5f, -45f}, {15.75f, 35f}, {-70f, -26.25f}};
    private static int filmPoseDebut = -1;
    private static int imageFilm;
    private static final int IMAGES_FILM = 240;

    /** Couleurs d'aura essayées (rouge, bleu, or) et celle du mannequin voisin (violet). */
    private static final int[] COULEURS = {0xFF4B4B, 0x4FA8FF, 0xFFC94A};
    private static final int AUTRE = 0xB57CFF;

    private MeditationDebug() {}

    static void init() {
        String v = System.getenv("REBORN_MEDITATION_DEBUG");
        if (v == null || !(v.equals("1") || v.equals("pose"))) return;
        pose = v.equals("pose");
        MeditationClient.debug = true;
        ClientTickEvents.END_CLIENT_TICK.register(MeditationDebug::tick);
        LevelRenderEvents.END_MAIN.register(ctx -> finImage());
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 30) mc.gui.hud.getChat().clearMessages(false);
        if (t < 40 && t % 5 == 0 && mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreenAndShow(null); }
        if (t == 20) {
            pauseAvant = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            ox = mc.player.getX(); oy = mc.player.getY(); oz = mc.player.getZ();
            oyaw = mc.player.getYRot(); opitch = mc.player.getXRot();
            if (mc.gameMode != null && mc.gameMode.getPlayerMode() != null) omode = mc.gameMode.getPlayerMode().getName();
            regles(c, false);
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            c.sendCommand("gamemode creative");
            construire(mc);
        }
        if (t > 20 && r == -1 && base != null && !demande) {
            BlockPos b = base;
            c.sendCommand(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 28", b.getX(), b.getY() + 2, b.getZ()));
            demande = true;
            r = -120;                                                    // attente des chunks
        }
        if (r < -1) { r++; if (r == -2) r = 0; return; }
        if (r < 0) return;
        if (film) {                                                     // le film avance image par image (fin du rendu)
            tenirAngles(mc);
            return;
        }
        r++;
        if (pose && r > 40) essaisPose(mc);
        else sequence(mc, c);
        tenirAngles(mc);
        if (mc.gui.screen() != null && !(r >= 600 && r < 625)) mc.setScreenAndShow(null);   // sauf l'écran de choix
    }

    private static void tenirAngles(Minecraft mc) {
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
        // essais de pose : le corps reste tourné vers le sud, seule la caméra tourne (angles de la vidéo)
        if (pose) { mc.player.setYBodyRot(0f); mc.player.yBodyRotO = 0f; }
    }

    private static void sequence(Minecraft mc, ClientPacketListener c) {
        BlockPos b = base;
        var o = mc.options;
        // le mannequin assis (un « autre joueur ») : halo violet allumé dès qu'il est vu
        if (r > 5 && r % 10 == 0) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Mannequin && e.distanceTo(mc.player) < 40) {
                    MeditationClient.definir(e.getId(), AUTRE, true, MeditationClient.temps());
                }
            }
        }
        switch (r) {
            case 1 -> {
                camAvant = RebornCamera.INSTANCE.mode();
                vueAvant = o.getCameraType();
                RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
                o.setCameraType(CameraType.THIRD_PERSON_BACK);
                yaw = -20f; pitch = 55f;
                // l'herbe vive de la vidéo (jungle clairsemée) sur tout le banc
                c.sendCommand(String.format(Locale.ROOT, "fillbiome %d %d %d %d %d %d minecraft:sparse_jungle",
                        b.getX() - 12, b.getY() - 4, b.getZ() - 10, b.getX() + 12, b.getY() + 4, b.getZ() + 30));
                asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                c.sendCommand(String.format(Locale.ROOT,
                        "summon armor_stand %d.5 %d %d.5 {Marker:1b,Invisible:1b,NoGravity:1b,Tags:[\"med_test\"],"
                                + "Passengers:[{id:\"minecraft:mannequin\",Tags:[\"med_test\"],Rotation:[200f,0f]}]}",
                        b.getX() + 4, b.getY(), b.getZ() + 4));
            }
            case 30 -> MeditationClient.definir(mc.player.getId(), Aura.DEFAUT, true, MeditationClient.temps());
            case 36 -> photo = "med2_00_apparition";
            // mêmes angles que la vidéo : dessus ¾, dos ¾, face
            case 76 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = 30f; pitch = -55f; }
            case 80 -> photo = "med2_01_herbe_dessus";
            case 85 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 30f; pitch = 40f; }
            case 100 -> photo = "med2_02_herbe_dos";
            case 105 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = 15f; pitch = -14f; }
            case 120 -> photo = "med2_03_herbe_face";
            case 125 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 40f; yaw = -40f; }
            case 130 -> {                                               // film de 8 s
                film = true;
                imageFilm = 0;
                MeditationClient.horlogeFilm = MeditationClient.temps();
            }
            case 135 -> { yaw = -20f; pitch = 55f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 12.5); }
            case 175 -> photo = "med2_04_pierre_dessus";
            case 180 -> { yaw = -25f; pitch = 35f; }
            case 195 -> photo = "med2_05_pierre_dos";
            case 200 -> { asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5); c.sendCommand("time set 18000"); }
            case 245 -> photo = "med2_06_nuit_dos";
            case 250 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = 15f; pitch = -14f; }
            case 265 -> photo = "med2_07_nuit_face";
            case 270 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = -25f; pitch = 35f; c.sendCommand("time set 6000"); }
            // couleurs d'aura choisies
            case 280 -> MeditationClient.definir(mc.player.getId(), COULEURS[0], true, MeditationClient.temps());
            case 300 -> photo = "med2_08_aura_rouge";
            case 305 -> MeditationClient.definir(mc.player.getId(), COULEURS[1], true, MeditationClient.temps());
            case 325 -> photo = "med2_09_aura_bleue";
            case 330 -> MeditationClient.definir(mc.player.getId(), COULEURS[2], true, MeditationClient.temps());
            case 350 -> photo = "med2_10_aura_or";
            case 355 -> { yaw = -20f; pitch = 55f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 12.5); }
            case 395 -> photo = "med2_11_aura_or_pierre";
            case 400 -> { MeditationClient.definir(mc.player.getId(), COULEURS[1], true, MeditationClient.temps());
                asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5); c.sendCommand("time set 18000"); }
            case 440 -> photo = "med2_12_aura_bleue_nuit";
            case 445 -> { c.sendCommand("time set 6000"); MeditationClient.definir(mc.player.getId(), Aura.DEFAUT, true, MeditationClient.temps());
                yaw = 0f; pitch = 30f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 24.5); }
            case 485 -> photo = "med2_13_pente";
            case 490 -> { yaw = -25f; pitch = 35f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5); }
            case 530 -> MeditationClient.definir(mc.player.getId(), 0, false, MeditationClient.temps());
            case 540 -> photo = "med2_14_disparition";
            // l'écran de choix (Réglages › Interface › Méditation), monde visible derrière
            case 560 -> { MeditationClient.definir(mc.player.getId(), Aura.DEFAUT, true, MeditationClient.temps());
                yaw = -25f; pitch = 35f; }
            case 562 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = -60f; pitch = -35f; }
            case 590 -> photo = "med2_16_profil";
            case 594 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = -25f; pitch = 35f; }
            case 600 -> mc.setScreenAndShow(new fr.reborn.hud.menu.character.perso.PersoAura(null));
            case 620 -> Screenshot.grab(mc.gameDirectory, "med2_15_ecran_aura.png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
            case 625 -> mc.setScreenAndShow(null);
            case 630 -> fin(mc);
            case 660 -> mc.stop();
            default -> { }
        }
    }

    /**
     * Essais de pose : chaque {@code run/med_essai_<k>.json} joué sur le joueur et le mannequin, aux 4 angles de la vidéo
     * (face, dessus ¾, dos ¾, profil).
     */
    private static void essaisPose(Minecraft mc) {
        var o = mc.options;
        int k = (r - 41) / 80, q = (r - 41) % 80;
        java.nio.file.Path f = mc.gameDirectory.toPath().resolve("med_essai_" + k + ".json");
        if (!java.nio.file.Files.exists(f)) {
            if (FILM_POSE && k > 0) { filmPose(mc); return; }
            if (q == 0) fin(mc);
            if (q == 20) mc.stop();
            return;
        }
        // les quatre angles de la vidéo, CALÉS dans Blender (tools/emotes/specs/meditation_vues_video.json : angle
        // autour du joueur θ, 0 = face, 90 = sa gauche ; hauteur h) — corps tourné vers le sud (lacet 0) :
        // vue de face → caméra FRONT, lacet = -θ, tangage = -h ; vue de dos → caméra BACK, lacet = 180 - θ, tangage = h
        switch (q) {
            case 0 -> {
                MeditationAnimation.essai = MeditationAnimation.lireFichier(f);
                MeditationAnimation.rejouer();
                o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = VUES[0][0]; pitch = VUES[0][1];
            }
            case 25 -> { photo = "medpose_" + k + "_face"; journalAngles(mc, "face"); }
            case 27 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = VUES[1][0]; pitch = VUES[1][1]; }
            case 40 -> { photo = "medpose_" + k + "_dessus"; journalAngles(mc, "dessus"); }
            case 42 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = VUES[2][0]; pitch = VUES[2][1]; }
            case 57 -> { photo = "medpose_" + k + "_dos"; journalAngles(mc, "dos"); }
            case 59 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = VUES[3][0]; pitch = VUES[3][1]; }
            case 76 -> { photo = "medpose_" + k + "_cote"; journalAngles(mc, "cote"); }
            default -> { }
        }
    }

    /**
     * {@code REBORN_MEDITATION_FILM=1} (avec {@code =pose}) : après les angles, film de 8 s de la première pose
     * ({@code med_essai_0.json}) — le monde passe à 3 ticks/s (une capture 1080p coûte plus qu'un tick à 20/s) et une
     * capture est prise à chaque tick ({@code medposefilm_000..159} → MP4 à 20 i/s = temps réel de l'animation ;
     * l'horloge du halo avance de 1/20 s par capture) : 4 s de dos puis 4 s de face, la caméra glissant de ¾ en ¾.
     */
    private static void filmPose(Minecraft mc) {
        var o = mc.options;
        var c = mc.player.connection;
        if (filmPoseDebut < 0) {
            filmPoseDebut = r;
            MeditationAnimation.essai = MeditationAnimation.lireFichier(mc.gameDirectory.toPath().resolve("med_essai_0.json"));
            MeditationAnimation.rejouer();
            o.setCameraType(CameraType.THIRD_PERSON_BACK);
            c.sendCommand("tick rate 3");
        }
        int i = r - filmPoseDebut;
        if (i >= 30 && i < 30 + 160) {
            if (i == 30) MeditationClient.horlogeFilm = MeditationClient.temps();
            else MeditationClient.horlogeFilm += 1.0 / 20.0;
            // 4 s de dos (¾ droit -> ¾ gauche), puis 4 s de face (¾ droit -> ¾ gauche) : le regard reste à moins de
            // 45° du corps (au-delà, Minecraft fait tourner le corps avec la tête et la vue ne tourne plus)
            int f = i - 30;
            double u = (f % 80) / 79.0;
            if (f < 80) { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = (float) (-40.0 + 80.0 * u); pitch = 32f; }
            else { o.setCameraType(CameraType.THIRD_PERSON_FRONT); yaw = (float) (40.0 - 80.0 * u); pitch = -18f; }
            photo = String.format(Locale.ROOT, "medposefilm_%03d", f);
        }
        if (i == 190) { MeditationClient.horlogeFilm = Double.NaN; c.sendCommand("tick rate 20"); }
        if (i == 200) fin(mc);
        if (i == 220) mc.stop();
    }

    private static void journalAngles(Minecraft mc, String vue) {
        var cam = mc.gameRenderer.mainCamera();
        org.slf4j.LoggerFactory.getLogger("reborn-hud").info(String.format(Locale.ROOT,
                "[med-pose] %s : regard %.1f/%.1f, corps %.1f, caméra %.1f/%.1f à %.2f bloc(s) de l'œil",
                vue, mc.player.getYRot(), mc.player.getXRot(), mc.player.yBodyRot, cam.yRot(), cam.xRot(),
                cam.position().distanceTo(mc.player.getEyePosition())));
    }

    /** Assoit le joueur sur un support invisible posé à (x, y, z) — comme {@code MeditationManager}. */
    private static void asseoir(ClientPacketListener c, double x, double y, double z) {
        c.sendCommand("ride @s dismount");
        c.sendCommand("kill @e[tag=med_seat]");
        c.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", x, y + 0.2, z));
        c.sendCommand(String.format(Locale.ROOT,
                "summon armor_stand %.2f %.2f %.2f {Marker:1b,Invisible:1b,NoGravity:1b,Tags:[\"med_test\",\"med_seat\"]}", x, y, z));
        c.sendCommand("ride @s mount @e[tag=med_seat,limit=1,sort=nearest]");
    }

    /** Fin du rendu du monde : capture demandée, ou image du film (et l'horloge de l'effet avance de 1/30 s). */
    private static void finImage() {
        Minecraft mc = Minecraft.getInstance();
        if (photo != null) {
            Screenshot.grab(mc.gameDirectory, photo + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
            photo = null;
        }
        if (!film) return;
        Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "medfilm_%05d.png", imageFilm), mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        imageFilm++;
        MeditationClient.horlogeFilm += 1.0 / 30.0;
        yaw = -25f + 50f * imageFilm / IMAGES_FILM;                    // la caméra tourne lentement autour
        if (mc.player != null) tenirAngles(mc);
        if (imageFilm >= IMAGES_FILM) {
            film = false;
            MeditationClient.horlogeFilm = Double.NaN;
        }
    }

    /**
     * Trois sols d'essai côte à côte (serveur intégré), à 600 blocs à l'est du point de départ : herbe (avec quelques
     * herbes hautes), pierre, et une pente en escalier (un bloc plus bas à l'ouest, un plus haut au sud, une dalle).
     */
    private static void construire(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        BlockPos depart = mc.player.blockPosition();
        srv.execute(() -> {
            ServerLevel sl = srv.overworld();
            int x0 = depart.getX() + 600, z0 = depart.getZ();
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 4; cz++) sl.getChunk((x0 >> 4) + cx, (z0 >> 4) + cz);
            int h = Math.max(sl.getSeaLevel() + 2, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0));
            BlockState air = Blocks.AIR.defaultBlockState(), herbe = Blocks.GRASS_BLOCK.defaultBlockState(),
                    terre = Blocks.DIRT.defaultBlockState();
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -8; dz <= 34; dz++) {
                    int x = x0 + dx, z = z0 + dz;
                    for (int y = h - 4; y <= Math.min(h + 72, sl.getMaxY()); y++) {      // haut : pas de relief flottant (ombres)
                        BlockState s;
                        if (y >= h) s = air;
                        else if (y == h - 1) s = herbe;
                        else s = terre;
                        if (dz >= 7 && dz <= 17 && y == h - 1) {          // pierre (pierre, andésite, pavés en aplats)
                            int k = Math.floorMod(dx * 7 + dz * 13, 9);
                            s = k < 5 ? Blocks.STONE.defaultBlockState() : k < 7 ? Blocks.ANDESITE.defaultBlockState()
                                    : Blocks.COBBLESTONE.defaultBlockState();
                        }
                        if (dz >= 19) {                                   // pente en escalier
                            int sol = dx <= -1 ? h - 2 : dz >= 25 ? h : h - 1;
                            if (dx <= -3) sol = h - 3;
                            s = y > sol ? air : y == sol ? herbe : terre;
                        }
                        sl.setBlock(new BlockPos(x, y, z), s, 2);
                    }
                }
            }
            sl.setBlock(new BlockPos(x0 + 1, h, z0 + 24), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            // quelques herbes hautes autour du premier sol (pas sous le joueur)
            int[][] touffes = {{-3, -2}, {-2, 3}, {2, -3}, {4, 4}, {-4, 1}, {5, -1}, {-1, -4}, {6, 2}, {-5, -3}};
            for (int[] p : touffes) sl.setBlock(new BlockPos(x0 + p[0], h, z0 + p[1]), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            base = new BlockPos(x0, h, z0);
        });
    }

    private static void fin(Minecraft mc) {
        var o = mc.options;
        var c = mc.player.connection;
        c.sendCommand("ride @s dismount");
        c.sendCommand("kill @e[tag=med_test]");
        if (camAvant != null) RebornCamera.INSTANCE.setMode(camAvant);
        if (vueAvant != null) o.setCameraType(vueAvant);
        c.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", ox, oy, oz, oyaw, opitch));
        c.sendCommand("gamemode " + omode);
        regles(c, true);
        MeditationClient.horlogeFilm = Double.NaN;
        MeditationClient.debug = false;
        o.pauseOnLostFocus = pauseAvant;
        if (MeditationRendu.mesures > 0) {
            org.slf4j.LoggerFactory.getLogger("reborn-hud").info(String.format(Locale.ROOT,
                    "[med-banc] rendu du halo : %d images, moyenne %.3f ms, pire %.3f ms, %d quads par image",
                    MeditationRendu.mesures, MeditationRendu.cumul / 1e6 / MeditationRendu.mesures,
                    MeditationRendu.pire / 1e6, MeditationRendu.quads / MeditationRendu.mesures));
        }
    }

    private static void regles(ClientPacketListener c, boolean on) {
        c.sendCommand("gamerule sendCommandFeedback " + on);
        c.sendCommand("gamerule send_command_feedback " + on);
    }
}
