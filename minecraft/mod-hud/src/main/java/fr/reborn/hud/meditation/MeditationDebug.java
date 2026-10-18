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
 * pente en escalier avec une dalle), assoit le joueur sur un support invisible (comme {@code MeditationManager}) et un
 * mannequin assis à côté (un « autre joueur » dans le monde, pas une entité factice d'écran), allume leurs halos
 * localement, puis : apparition, herbe de dos / de face, film de 8 s (240 images à 30 i/s, horloge de l'effet imposée,
 * caméra qui tourne lentement), pierre, pente, nuit, disparition, et une rangée de mannequins aux teintes des natures (jour, nuit). Captures {@code run/screenshots/med_*.png} et
 * {@code medfilm_*.png} prises en fin de rendu du monde (sans HUD). Remet tout en place et ferme le jeu ; le coût du
 * rendu est écrit dans le journal ({@code [med-banc]}).
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
    private static int imageFilm;
    private static final int IMAGES_FILM = 240;

    /** Teintes de {@code meditation.halo.natures} (config ShinobiCore) : katon, suiton, futon, doton, raiton. */
    private static final int[] NATURES = {0xFFA36C, 0x7DB8FF, 0xC8F5E0, 0xE0BE82, 0xFFF08A};

    private MeditationDebug() {}

    static void init() {
        if (!"1".equals(System.getenv("REBORN_MEDITATION_DEBUG"))) return;
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
        sequence(mc, c);
        tenirAngles(mc);
        if (mc.gui.screen() != null) mc.setScreenAndShow(null);
    }

    private static void tenirAngles(Minecraft mc) {
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static void sequence(Minecraft mc, ClientPacketListener c) {
        BlockPos b = base;
        var o = mc.options;
        // le mannequin assis : halo allumé dès qu'il est vu
        if (r > 5 && r % 10 == 0) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Mannequin && e.distanceTo(mc.player) < 40) {
                    int rgb = MeditationClient.VERT;
                    if (e.getZ() < b.getZ() - 3) {                      // la rangée des natures
                        int k = Math.max(0, Math.min(4, (int) Math.floor((e.getX() - (b.getX() - 6)) / 3)));
                        rgb = NATURES[k];
                    }
                    MeditationClient.definir(e.getId(), rgb, true, MeditationClient.temps());
                }
            }
        }
        switch (r) {
            case 1 -> {
                camAvant = RebornCamera.INSTANCE.mode();
                vueAvant = o.getCameraType();
                RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
                o.setCameraType(CameraType.THIRD_PERSON_BACK);
                yaw = 0f; pitch = 28f;
                asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                c.sendCommand(String.format(Locale.ROOT,
                        "summon armor_stand %d.5 %d %d.5 {Marker:1b,Invisible:1b,NoGravity:1b,Tags:[\"med_test\"],"
                                + "Passengers:[{id:\"minecraft:mannequin\",Tags:[\"med_test\"],Rotation:[200f,0f]}]}",
                        b.getX() + 3, b.getY(), b.getZ() + 2));
            }
            case 30 -> MeditationClient.definir(mc.player.getId(), MeditationClient.VERT, true, MeditationClient.temps());
            case 38 -> photo = "med_00_apparition";
            case 90 -> photo = "med_01_herbe_dos";
            case 95 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); pitch = -32f; }
            case 110 -> photo = "med_02_herbe_face";
            case 115 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 30f; yaw = -25f; }
            case 125 -> {                                               // film de 8 s
                film = true;
                imageFilm = 0;
                MeditationClient.horlogeFilm = MeditationClient.temps();
            }
            case 130 -> { yaw = 0f; pitch = 30f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 12.5); }
            case 175 -> photo = "med_03_pierre_dos";
            case 180 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); pitch = -32f; }
            case 195 -> photo = "med_03b_pierre_face";
            case 200 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0f; pitch = 30f;
                asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 24.5); }
            case 245 -> photo = "med_04_pente_dos";
            case 250 -> { yaw = 135f; pitch = 26f; }
            case 265 -> photo = "med_04b_pente_cote";
            case 270 -> { yaw = 0f; pitch = 28f; asseoir(c, b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                c.sendCommand("time set 18000"); }
            case 320 -> photo = "med_05_nuit_dos";
            case 325 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); pitch = -30f; }
            case 340 -> photo = "med_05b_nuit_face";
            case 345 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 28f; c.sendCommand("time set 6000"); }
            case 380 -> MeditationClient.definir(mc.player.getId(), 0, false, MeditationClient.temps());
            case 390 -> photo = "med_06_disparition";
            case 395 -> {                                               // teintes par nature (config par-nature)
                for (int k = 0; k < 5; k++) {
                    c.sendCommand(String.format(Locale.ROOT,
                            "summon armor_stand %d.5 %d %d.5 {Marker:1b,Invisible:1b,NoGravity:1b,Tags:[\"med_test\"],"
                                    + "Passengers:[{id:\"minecraft:mannequin\",Tags:[\"med_test\"],Rotation:[0f,0f]}]}",
                            b.getX() - 6 + 3 * k, b.getY(), b.getZ() - 6));
                }
            }
            case 400 -> { yaw = 180f; pitch = 26f; }
            case 440 -> photo = "med_07_natures";
            case 445 -> c.sendCommand("time set 18000");
            case 480 -> photo = "med_07b_natures_nuit";
            case 485 -> c.sendCommand("time set 6000");
            case 490 -> fin(mc);
            case 520 -> mc.stop();
            default -> { }
        }
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
