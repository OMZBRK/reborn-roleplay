package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.concentration.ConcentrationCanal;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.concentration.Plaques;
import fr.reborn.hud.concentration.Traces;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.settings.CouleurTracesBanc;
import fr.reborn.hud.menu.settings.InterfaceTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Banc de captures des <b>traces de pas</b> (lot 4, étape C, planche c7), <b>inactif en production</b> :
 * {@code REBORN_LOT4_DEBUG=c7} (monde solo de dev, jamais de serveur). Serveur simulé côté client : les pistes partent par le
 * vrai message {@code traces} du canal, avec âges et durées de vie ; hauteur des pas lue dans la vraie collision des blocs.
 * Horloges figées.
 *
 * <p>Scènes (axes de la planche, mx = vers la droite de l'écran) :
 * <ul>
 *   <li><b>pistes</b> : chemin de pierre, dalle, escalier, terrasse (marche fraîche jusqu'à 20 blocs), sprint qui la croise,
 *       accroupi dans l'herbe, vieille marche sur le sable ;</li>
 *   <li><b>âge</b> : vue plongeante, une marche de vie 10 min du plus vieux au plus frais, une piste accroupie de vie 5 min ;
 *       <b>gros plan</b> sur quelques âges (effritement, chargé, sprint) ;</li>
 *   <li><b>survol</b> : curseur sur un pas frais puis sur un pas de 7 min ;</li>
 *   <li><b>couleurs</b> : ivoire, bleu chakra, rouge sang ; réglage dans Paramètres › Interface › Concentration ;</li>
 *   <li><b>sortie</b> : fondu à 75 ms, puis plus rien.</li>
 * </ul>
 * En 1920 × 1080 puis 1366 × 768 → {@code run/screenshots/lot4c7_*.png}, bilans « [lot4c7] OK / ECHEC », puis ferme le jeu.
 *
 * <p>{@code REBORN_LOT4_DEBUG=c8} (retour du 08/11, contraste automatique, une trace reste) : bandes d'herbe, de pierre, de sable,
 * de neige, de béton blanc, de quartz, de calcite, de bouleau ; jour et nuit, piste à 80 % de sa vie, étiquette « il y a 1 h 20 »
 * sur l'herbe et sur le sable, réglage « Automatique » → {@code run/screenshots/lot4c8_*.png}.
 */
public final class Lot4c7Debug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival", difficulte = "normal";
    private static long h;
    private static int ox, oy, oz;
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    private static final Vector3f V = new Vector3f();
    private static int couleurAvant;
    private static boolean autoAvant, c8;
    private static int[] recentesAvant;
    private static RebornCamera.Mode cameraAvant;

    private static final int D = 1, S = 2, A = 4, C = 8, SANG = 16;
    private static final float DECAL = 0.12f, OUVERTURE = 6f;

    /** Les pas de la scène en cours (id, x, y, z, angle, drapeaux, âge s, vie s). */
    private record Pas(int id, double x, double y, double z, int ang, int fl, int age, int vie) { }

    private static final List<Pas> SCENE = new ArrayList<>();
    private static int prochainId = 1;
    private static int pasFrais = -1, pasVieux = -1, pasHerbe = -1, pasSable = -1, pasRelecture = -1;
    private static List<Integer> idsHerbe = List.of(), idsPierre = List.of(), idsSable = List.of();
    private static List<List<Integer>> idsClairs = List.of();

    private Lot4c7Debug() {}

    public static void init() {
        c8 = "c8".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT4_DEBUG")));
        if (!c8 && !"c7".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT4_DEBUG")))) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            volant = true;
            vol(mc, true);
            var pr = RebornPrefs.INSTANCE;
            pr.ensureLoaded();
            couleurAvant = pr.tracesCouleur;
            recentesAvant = pr.tracesRecentes;
            pr.tracesCouleur = Traces.IVOIRE;
            autoAvant = pr.tracesAuto;
            System.out.println("[lot4c7] réglage lu : automatique = " + autoAvant);
            pr.tracesAuto = c8;
            cameraAvant = RebornCamera.INSTANCE.mode();
            c.sendCommand("kill @e[type=minecraft:item,distance=..60]");
        }, "préparation");
        pas(10, c8 ? Lot4c7Debug::batirSols : Lot4c7Debug::batir, "scène");
        pas(40, mc -> {
            figer(Util.getMillis());
            ConcentrationCanal.debugServeur = Lot4c7Debug::serveur;
            System.out.println("[lot4c7] origine " + ox + " " + oy + " " + oz);
            // règles pures du client
            verifie(Traces.minutes(0) == 0 && Traces.minutes(29) == 0 && Traces.minutes(30) == 1 && Traces.minutes(240) == 4, "âge arrondi à la minute");
            verifie(Traces.minutes(4800) == 80, "âge en heures");
            verifie("à l'instant".equals(Traces.texteDuree(0)) && "il y a 7 min".equals(Traces.texteDuree(7)) && "il y a 59 min".equals(Traces.texteDuree(59)), "texte de l'étiquette en minutes");
            verifie("il y a 1 h".equals(Traces.texteDuree(60)) && "il y a 1 h 05".equals(Traces.texteDuree(65))
                && "il y a 1 h 20".equals(Traces.texteDuree(80)) && "il y a 4 h".equals(Traces.texteDuree(240)), "texte de l'étiquette en heures");
            // le contraste automatique : la couleur de carte du vrai bloc, seuil unique
            for (Block b : new Block[] {Blocks.SAND, Blocks.SNOW_BLOCK, Blocks.SNOW, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE), Blocks.QUARTZ_BLOCK, Blocks.CALCITE, Blocks.BIRCH_PLANKS, Blocks.SANDSTONE}) {
                sol(mc, b, true);
            }
            for (Block b : new Block[] {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.DIRT_PATH, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.RED_SAND, Blocks.GRAVEL}) {
                sol(mc, b, false);
            }
            // une trace reste : rien d'effrité ni de pâli avant 60 % de sa vie, effritée à 80 %, pâle mais là au bout
            boolean age = Traces.opaciteAgeBanc(0f) == 1f && Traces.opaciteAgeBanc(0.60f) == 1f && Traces.opaciteAgeBanc(0.80f) > 0.5f
                && Traces.opaciteAgeBanc(0.999f) >= 0.25f && Traces.opaciteAgeBanc(1f) == 0f;
            verifie(age, "opacité selon l'âge (80 % : " + Traces.opaciteAgeBanc(0.80f) + ")");
            for (int id = 0; id < 5; id++) {
                verifie(Traces.colonneBanc(0.60f, id) == 0 && Traces.colonneBanc(0.80f, id) >= 2 && Traces.colonneBanc(0.99f, id) == 5, "effritement du pas " + id);
            }
            // la poussée : chaque préréglage, à l'écran passé l'effet (hors vignette), et sans effet quand le filtre n'est pas là
            for (int i = 0; i < Traces.PRESETS.length; i++) {
                int c = Traces.PRESETS[i], p = Traces.poussee(c, 1f);
                int[] e = ecran(p), brut = ecran(c);
                System.out.printf("[lot4c7] %s %06X : posé %06X, à l'écran (%d,%d,%d), écart %d (sans poussée %d)%n", Traces.PRESETS_NOMS[i], c, p,
                    e[0], e[1], e[2], ecart(e), ecart(brut));
                verifie(Traces.poussee(c, 0f) == c, Traces.PRESETS_NOMS[i] + " : hors effet, la couleur telle quelle");
                // l'écart plafonne à 51 passé l'effet (20 % de 255) : une couleur saturée doit s'en approcher, et gagner sur la couleur brute
                if (i >= 2) verifie(ecart(e) >= 40 && ecart(e) > ecart(brut), Traces.PRESETS_NOMS[i] + " : la poussée renforce la teinte (écart " + ecart(e) + ")");
            }
        }, "horloges figées, serveur simulé");

        if (c8) {
            serieAuto();
        } else {
            serie("1080");
            pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
            pas(25, mc -> { }, "fenêtre posée");
            serie("1366");
        }

        pas(10, mc -> {
            ModeConcentration.reinitialiser();
            ModeConcentration.horloge = Util::getMillis;
            Plaques.horloge = Util::getMillis;
            Recit.horloge = Util::getMillis;
            Traces.horloge = Util::getMillis;
            ConcentrationCanal.debugServeur = null;
            InteractionMode.INSTANCE.debugClose();
            mc.setScreenAndShow(null);
            RebornCamera.INSTANCE.setMode(cameraAvant);
            RebornPrefs.INSTANCE.tracesCouleur = couleurAvant;
            RebornPrefs.INSTANCE.tracesAuto = autoAvant;
            RebornPrefs.INSTANCE.tracesRecentes = recentesAvant;
            RebornPrefs.INSTANCE.save();
            var c = mc.player.connection;
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 14, oy, oz - 8, ox + 14, oy + 24, oz + 26));
            if (c8) c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 14, oy - 1, oz - 8, ox + 14, oy - 1, oz + 28));
            c.sendCommand("kill @e[type=minecraft:item,distance=..60]");
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[lot4c7] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4c7Debug::tick);
    }

    /* ================================================================ contraste automatique, une trace reste (c8) */

    private static void sol(Minecraft mc, Block b, boolean clair) {
        int col = b.defaultBlockState().getMapColor(mc.level, BlockPos.ZERO).col;
        verifie(Traces.solClair(col) == clair, String.format(Locale.ROOT, "%s : couleur de carte %06X, luminance %.2f, sol %s (seuil %.2f)",
            b.builtInRegistryHolder().key().identifier().getPath(), col, Traces.luminance(col), clair ? "clair" : "sombre", Traces.SEUIL_SOL_CLAIR));
    }

    /** Herbe partout ; pierre et sable en bandes ; plus loin, les sols clairs côte à côte. */
    private static void batirSols(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 14, oy, oz - 8, ox + 14, oy + 24, oz + 28));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 14, oy - 1, oz - 8, ox + 14, oy - 1, oz + 28));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", ox - 1, oy - 1, oz - 8, ox + 2, oy - 1, oz + 16));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:sand", ox - 5, oy - 1, oz - 8, ox - 2, oy - 1, oz + 16));
        String[] clairs = {"snow_block", "white_concrete", "quartz_block", "calcite", "birch_planks"};
        for (int i = 0; i < clairs.length; i++) {
            int dx = 5 - i * 3;   // de la gauche de l'écran vers la droite : x monde décroissant
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:%s", ox + dx, oy - 1, oz + 17, ox + dx + 2, oy - 1, oz + 28, clairs[i]));
        }
    }

    private static void sceneSols(Minecraft mc) {
        idsHerbe = piste(mc, new double[][] {{-4.6, 1.2}, {-4.6, 14.0}}, 1.2, 0, 4.0, 1.0, 1, 7200);
        idsPierre = piste(mc, new double[][] {{0.9, 1.2}, {0.9, 14.0}}, 1.2, 0, 4.0, 1.0, 1, 2880);
        idsSable = piste(mc, new double[][] {{3.6, 1.2}, {3.6, 14.0}}, 1.2, 0, 4.0, 1.0, 1, 14400);
        piste(mc, new double[][] {{-8.0, 8.0}, {8.0, 8.0}}, 1.2, 0, 0.5, 0.2, 1, 7200);
        // un pas au-dessus du vide : sol inconnu, relu quand le bloc arrive (comme un chunk pas encore chargé)
        pasRelecture = prochainId++;
        SCENE.add(new Pas(pasRelecture, wx(-9.0), oy + 1, wz(3.0), 0, 0, 60, 7200));
    }

    private static void sceneClairs(Minecraft mc) {
        idsClairs = new ArrayList<>();
        for (double mx : new double[] {-6.0, -3.0, 0.6, 3.0, 6.0}) {
            idsClairs.add(piste(mc, new double[][] {{mx, 17.6}, {mx, 27.0}}, 1.2, 0, 3.0, 1.0, 1, 14400));
        }
        piste(mc, new double[][] {{-9.5, 21.5}, {9.5, 21.5}}, 1.2, 0, 0.5, 0.2, 1, 7200);
    }

    /** Trois rangées à travers l'herbe, la pierre et le sable, vie 2 h : 10 %, 80 %, 95 % de la vie. */
    private static void sceneVieux(Minecraft mc) {
        piste(mc, new double[][] {{-6.6, 3.4}, {6.6, 3.4}}, 1.2, 0, 12.0, 12.0, 1, 7200);
        piste(mc, new double[][] {{-6.6, 2.4}, {6.6, 2.4}}, 1.2, 0, 96.0, 96.0, 1, 7200);
        piste(mc, new double[][] {{-6.6, 1.4}, {6.6, 1.4}}, 1.2, 0, 114.0, 114.0, 1, 7200);
    }

    /** Sur le sable, côte à côte : une piste de sprint, une de marche. */
    private static void sceneSprintSable(Minecraft mc) {
        piste(mc, new double[][] {{3.0, 0.6}, {3.0, 3.8}}, 0.8, S, 3.0, 3.0, 1, 14400);
        piste(mc, new double[][] {{4.5, 0.6}, {4.5, 3.8}}, 0.8, 0, 3.0, 3.0, 1, 14400);
    }

    /** Une piste de 1 h 20 sur l'herbe, une sur le sable. */
    private static void sceneLabel(Minecraft mc) {
        pasHerbe = piste(mc, new double[][] {{-3.8, 1.2}, {-3.8, 8.0}}, 1.2, 0, 80.0, 80.0, 1, 7200).get(2);
        pasSable = piste(mc, new double[][] {{3.2, 1.2}, {3.2, 8.0}}, 1.2, 0, 80.0, 80.0, 1, 14400).get(2);
    }

    private static void classes(List<Integer> ids, int attendu, String nom) {
        boolean ok = !ids.isEmpty();
        for (int id : ids) ok &= Traces.solDe(id) == attendu;
        verifie(ok, nom + " : " + ids.size() + " pas " + (attendu == 2 ? "sur sol clair" : "sur sol sombre"));
    }

    private static void serieAuto() {
        String pre = "lot4c8_";
        pas(2, mc -> mc.player.connection.sendCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", (int) Math.floor(wx(-9.0)), oy, (int) Math.floor(wz(3.0)))), "pas de bloc sous le pas à relire");
        // ── herbe, pierre, sable, de jour ──
        scene(Lot4c7Debug::sceneSols, "sols");
        pas(3, mc -> epaule(mc, -0.3, 0.0, 12f), "joueur sur la pierre");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(6, mc -> {
            verifie(RebornPrefs.INSTANCE.tracesAuto, "couleur automatique");
            verifie(Traces.nombre() == SCENE.size(), "tous les pas reçus (" + Traces.nombre() + ")");
            classes(idsHerbe, 1, "herbe");
            classes(idsPierre, 1, "pierre");
            classes(idsSable, 2, "sable");
            verifie(Traces.contourNuit() == 0f, "de jour : pas de contour (" + Traces.contourNuit() + ")");
            verifie(Traces.solDe(pasRelecture) == 0, "au-dessus du vide : sol inconnu (" + Traces.solDe(pasRelecture) + ")");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:sand", (int) Math.floor(wx(-9.0)), oy, (int) Math.floor(wz(3.0))));
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.9, mc.getWindow().getGuiScaledHeight() * 0.15);
        }, "bilan des sols");
        pas(30, mc -> verifie(Traces.solDe(pasRelecture) == 2, "le sol inconnu est relu en moins de 2 s (" + Traces.solDe(pasRelecture) + ")"), "relecture");
        pas(4, mc -> shot(mc, pre + "jour_sols_1920x1080"), "capture sols de jour");
        pas(1, mc -> sortir(), "sortie");
        // ── la même, de nuit ──
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(4, mc -> {
            classes(idsSable, 2, "sable, de nuit : c'est le bloc qui décide");
            classes(idsHerbe, 1, "herbe, de nuit");
            verifie(Traces.contourNuit() == 1f, "de nuit : le contour (" + Traces.contourNuit() + ")");
            shot(mc, pre + "nuit_sols");
        }, "capture sols de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(16, mc -> { }, "jour revenu");
        // ── neige, béton blanc, quartz, calcite, bouleau ──
        scene(Lot4c7Debug::sceneClairs, "sols clairs");
        pas(3, mc -> epaule(mc, 0.0, 15.2, 12f), "joueur devant les sols clairs");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(4, mc -> {
            for (int i = 0; i < idsClairs.size(); i++) classes(idsClairs.get(i), 2, new String[] {"neige", "béton blanc", "quartz", "calcite", "bouleau"}[i]);
            shot(mc, pre + "jour_clairs");
        }, "capture sols clairs de jour");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(4, mc -> shot(mc, pre + "nuit_clairs"), "capture sols clairs de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(16, mc -> { }, "jour revenu");
        // ── vieille piste ──
        scene(Lot4c7Debug::sceneVieux, "vieilles pistes");
        pas(4, mc -> {
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            a(mc, 0.0, -1.9, 0f, 56f, 4.0);
        }, "vue plongeante");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.5, mc.getWindow().getGuiScaledHeight() * 0.96);
        }, "fondu fini");
        pas(4, mc -> shot(mc, pre + "vieux_10_80_95pc"), "capture vieille piste");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER), "caméra d'épaule");
        // ── un pas de sprint sur un sol clair, gros plan : le contour se ferme au talon (nuit), rien de jour ──
        scene(Lot4c7Debug::sceneSprintSable, "sprint sur le sable");
        pas(4, mc -> {
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            a(mc, 3.7, -0.8, 0f, 62f, 1.0);
        }, "gros plan sur le sable");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.9, mc.getWindow().getGuiScaledHeight() * 0.1);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(4, mc -> shot(mc, pre + "jour_sprint_sable"), "capture sprint de jour");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.9, mc.getWindow().getGuiScaledHeight() * 0.1);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(4, mc -> shot(mc, pre + "nuit_sprint_sable"), "capture sprint de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> {
            mc.player.connection.sendCommand("time set 6000");
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        }, "jour, caméra d'épaule");
        pas(16, mc -> { }, "jour revenu");
        // ── étiquette ──
        scene(Lot4c7Debug::sceneLabel, "étiquette");
        pas(4, mc -> epaule(mc, -0.3, 0.0, 22f), "joueur sur la pierre");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(1, mc -> curseurSur(mc, pasHerbe), "curseur sur l'herbe");
        pas(3, mc -> {
            verifie(Traces.survolId() == pasHerbe && Traces.minutesLibelle() == 80, "herbe : « il y a 1 h 20 » (" + Traces.survolId() + ", " + Traces.minutesLibelle() + ")");
            avance(200);
        }, "étiquette sur l'herbe");
        pas(4, mc -> shot(mc, pre + "label_herbe"), "capture étiquette sur l'herbe");
        pas(1, mc -> curseurSur(mc, pasSable), "curseur sur le sable");
        pas(3, mc -> {
            verifie(Traces.survolId() == pasSable && Traces.minutesLibelle() == 80, "sable : « il y a 1 h 20 » (" + Traces.survolId() + ", " + Traces.minutesLibelle() + ")");
            avance(200);
        }, "étiquette sur le sable");
        pas(4, mc -> shot(mc, pre + "label_sable"), "capture étiquette sur le sable");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(1, mc -> curseurSur(mc, pasSable), "curseur sur le sable");
        pas(3, mc -> avance(200), "étiquette de nuit");
        pas(4, mc -> shot(mc, pre + "label_sable_nuit"), "capture étiquette sur le sable, de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(16, mc -> { }, "jour revenu");
        // ── le réglage, par les vrais événements de l'écran ──
        pas(2, mc -> {
            CouleurTracesBanc.auto();
            RebornPrefs.INSTANCE.tracesRecentes = new int[] {0x7FD0C8, 0xFF7A5C};
            mc.setScreenAndShow(new ConfigShellScreen(null, ConfigShellScreen.Onglet.INTERFACE, InterfaceTab.CONCENTRATION));
        }, "Paramètres › Interface › Concentration");
        pas(10, mc -> shot(mc, pre + "reglage_auto_1920x1080"), "capture réglage automatique");
        pas(1, mc -> clic(mc, 2), "clic sur le bleu chakra");
        pas(2, mc -> {
            verifie(!RebornPrefs.INSTANCE.tracesAuto && RebornPrefs.INSTANCE.tracesCouleur == 0x6FB4FF, "une pastille : choix manuel");
            shot(mc, pre + "reglage_manuel");
        }, "choix manuel");
        pas(3, mc -> clic(mc, -1), "clic sur « Automatique »");
        pas(2, mc -> {
            verifie(RebornPrefs.INSTANCE.tracesAuto, "retour à « Automatique »");
            clic(mc, 7);
        }, "ouverture de « Personnalisée » depuis « Automatique »");
        pas(3, mc -> {
            verifie(CouleurTracesBanc.ouvert() && RebornPrefs.INSTANCE.tracesAuto, "ouvrir ne quitte pas « Automatique »");
            int[] m = CouleurTracesBanc.montre();
            boolean cales = m[0] == 0x7FD0C8 && m[1] == 0x7FD0C8 && Math.abs((m[2] >> 16 & 255) - 0x7F) <= 2 && Math.abs((m[2] >> 8 & 255) - 0xD0) <= 2 && Math.abs((m[2] & 255) - 0xC8) <= 2;
            verifie(cales, String.format(Locale.ROOT, "sélecteur calé sur une seule couleur : champ %06X, aperçu %06X, curseurs %06X", m[0], m[1], m[2]));
            shot(mc, pre + "reglage_auto_ouvert");
            clic(mc, 8);
        }, "un geste dans le carré");
        pas(3, mc -> {
            verifie(!RebornPrefs.INSTANCE.tracesAuto, "un geste quitte « Automatique »");
            CouleurTracesBanc.reinitialiser();
        }, "Réinitialiser");
        pas(3, mc -> {
            verifie(RebornPrefs.INSTANCE.tracesAuto && RebornPrefs.INSTANCE.tracesCouleur == Traces.IVOIRE && !CouleurTracesBanc.ouvert(), "« Réinitialiser » revient à « Automatique »");
        }, "bilan du réglage");
        pas(1, mc -> mc.setScreenAndShow(null), "Paramètres fermés");
        // ── 1366 × 768 ──
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(25, mc -> { }, "fenêtre posée");
        scene(Lot4c7Debug::sceneSols, "sols");
        pas(3, mc -> epaule(mc, -0.3, 0.0, 12f), "joueur sur la pierre");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.9, mc.getWindow().getGuiScaledHeight() * 0.15);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(4, mc -> shot(mc, pre + "jour_sols_1366x768"), "capture sols de jour");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.setScreenAndShow(new ConfigShellScreen(null, ConfigShellScreen.Onglet.INTERFACE, InterfaceTab.CONCENTRATION)), "réglage en 1366 × 768");
        pas(10, mc -> shot(mc, pre + "reglage_auto_1366x768"), "capture réglage automatique");
        pas(1, mc -> mc.setScreenAndShow(null), "Paramètres fermés");
        pas(5, mc -> { }, "écran fermé");
    }

    /* ================================================================ la série de captures */

    /** Le rendu de {@code concentration_ecran.fsh} à plein effet, sans vignette ni nuance froide. */
    private static int[] ecran(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new int[] {(int) Math.round(l + (r - l) * 0.2), (int) Math.round(l + (g - l) * 0.2), (int) Math.round(l + (b - l) * 0.2)};
    }

    private static int ecart(int[] c) { return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2])); }

    private static void serie(String res) {
        String pre = "lot4c7_" + res + "_";
        // ── pistes, de jour puis de nuit ──
        scene(Lot4c7Debug::scenePistes, "pistes");
        pas(2, mc -> epaule(mc, -0.3, 0.0, 7f), "joueur sur le chemin");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini, pistes reçues");
        pas(6, mc -> {
            verifie(Traces.nombre() == SCENE.size(), res + " : tous les pas reçus (" + Traces.nombre() + " / " + SCENE.size() + ")");
            verifie(Traces.dessines() > 0 && Traces.dessines() <= Traces.MAX_DESSINES, res + " : " + Traces.dessines() + " pas dessinés");
            verifie(ModeConcentration.filtreAffiche(), res + " : effet d'écran posé");
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.82, mc.getWindow().getGuiScaledHeight() * 0.58);
        }, "bilan des pistes");
        pas(4, mc -> shot(mc, pre + "jour_pistes"), "capture pistes de jour");
        // ── sortie : fondu puis rien ──
        pas(1, mc -> ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE), "sortie");
        pas(1, mc -> avance(75), "sortie à 75 ms");
        pas(4, mc -> shot(mc, pre + "sortie_75ms"), "capture sortie à 75 ms");
        pas(1, mc -> avance(600), "sortie finie");
        pas(5, mc -> {
            verifie(Traces.nombre() == 0 && Traces.dessines() == 0, res + " : plus aucun pas après la sortie");
            verifie(!ModeConcentration.filtreVoulu(), res + " : effet retiré");
            shot(mc, pre + "sortie_finie");
        }, "capture après la sortie");
        // ── nuit ──
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(4, mc -> shot(mc, pre + "nuit_pistes"), "capture pistes de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(16, mc -> { }, "jour revenu");

        // ── du pas frais au pas ancien, en vue plongeante ──
        scene(Lot4c7Debug::sceneAge, "âge");
        pas(4, mc -> {
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            a(mc, 0.0, -1.9, 0f, 56f, 4.0);
        }, "vue plongeante");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.5, mc.getWindow().getGuiScaledHeight() * 0.94);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(3, mc -> shot(mc, pre + "jour_age"), "capture âge de jour");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        pas(1, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.5, mc.getWindow().getGuiScaledHeight() * 0.94);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(3, mc -> shot(mc, pre + "nuit_age"), "capture âge de nuit");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(16, mc -> { }, "jour revenu");

        // ── gros plan : la semelle, droit et gauche, effritement, sprint, chargé, accroupi, sang ──
        scene(Lot4c7Debug::sceneGrosPlan, "gros plan");
        pas(4, mc -> a(mc, 0.0, -0.6, 0f, 62f, 1.0), "gros plan");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> {
            avance(300);
            InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.5, mc.getWindow().getGuiScaledHeight() * 0.96);
        }, "fondu fini");
        pas(3, mc -> avance(200), "pas d'étiquette");
        pas(3, mc -> shot(mc, pre + "gros_plan"), "capture gros plan");
        pas(1, mc -> sortir(), "sortie");
        pas(2, mc -> RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER), "caméra d'épaule");

        // ── temps écoulé au survol ──
        scene(Lot4c7Debug::sceneSurvol, "survol");
        pas(4, mc -> epaule(mc, -0.3, 0.0, 12f), "joueur sur le chemin");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        pas(1, mc -> curseurSur(mc, pasFrais), "curseur sur le pas frais");
        pas(3, mc -> {
            verifie(Traces.survolId() == pasFrais, res + " : pas frais survolé (" + Traces.survolId() + ")");
            verifie(Traces.minutesLibelle() == 0, res + " : « à l'instant »");
            avance(60);
        }, "fondu de l'étiquette, à moitié");
        pas(3, mc -> shot(mc, pre + "survol_fondu_60ms"), "capture étiquette à 60 ms");
        pas(1, mc -> avance(100), "étiquette pleine");
        pas(4, mc -> shot(mc, pre + "survol_instant"), "capture « à l'instant »");
        pas(1, mc -> curseurSur(mc, pasVieux), "curseur sur le vieux pas");
        pas(3, mc -> {
            verifie(Traces.survolId() == pasVieux, res + " : vieux pas survolé (" + Traces.survolId() + ")");
            verifie(Traces.minutesLibelle() == 7, res + " : « il y a 7 min » (" + Traces.minutesLibelle() + ")");
            avance(200);
        }, "étiquette du vieux pas");
        pas(4, mc -> shot(mc, pre + "survol_7min"), "capture « il y a 7 min »");
        pas(1, mc -> InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.9, mc.getWindow().getGuiScaledHeight() * 0.2),
            "curseur ailleurs");
        pas(3, mc -> {
            verifie(Traces.survolId() < 0, res + " : plus rien sous le curseur");
            avance(200);
        }, "hors du pas");
        pas(1, mc -> sortir(), "sortie");

        // ── couleurs, dans le monde ──
        scene(Lot4c7Debug::scenePistes, "pistes (couleurs)");
        pas(3, mc -> epaule(mc, -0.3, 0.0, 7f), "joueur sur le chemin");
        pas(15, Lot4c7Debug::entrer, "entrée");
        pas(16, mc -> avance(300), "fondu fini");
        for (int[] coul : new int[][] {{Traces.IVOIRE}, {0x6FB4FF}, {0xD9443F}, {0xFF9A3C}}) {
            String nom = coul[0] == Traces.IVOIRE ? "ivoire" : coul[0] == 0x6FB4FF ? "chakra" : coul[0] == 0xD9443F ? "sang" : "ambre";
            pas(1, mc -> RebornPrefs.INSTANCE.tracesCouleur = coul[0], "couleur " + nom);
            pas(4, mc -> shot(mc, pre + "couleur_" + nom), "capture couleur " + nom);
        }
        // sous le Byakugan : plus de traces ni d'étiquette (son filtre négatif les inverserait), elles reviennent ensuite
        pas(1, mc -> { RebornPrefs.INSTANCE.tracesCouleur = Traces.IVOIRE; ByakuganClient.update(true, 0f); }, "Byakugan");
        pas(4, mc -> {
            verifie(!ModeConcentration.filtreAffiche(), res + " : le Byakugan a pris l'emplacement du filtre");
            verifie(Traces.survolId() < 0, res + " : pas d'étiquette sous le Byakugan");
            shot(mc, pre + "byakugan");
        }, "capture sous le Byakugan");
        pas(1, mc -> ByakuganClient.update(false, 0f), "fin du Byakugan");
        pas(4, mc -> verifie(ModeConcentration.filtreAffiche() && Traces.nombre() > 0, res + " : l'effet et les traces reviennent"), "retour");
        pas(1, mc -> sortir(), "sortie");

        // ── le réglage ──
        pas(2, mc -> {
            CouleurTracesBanc.poser(Traces.IVOIRE, false);
            RebornPrefs.INSTANCE.tracesRecentes = new int[] {0x7FD0C8, 0xFF7A5C, 0xBEA0FF, 0xF29CC2};
            mc.setScreenAndShow(new ConfigShellScreen(null, ConfigShellScreen.Onglet.INTERFACE, InterfaceTab.CONCENTRATION));
        }, "Paramètres › Interface › Concentration");
        pas(10, mc -> shot(mc, pre + "reglage_ivoire"), "capture réglage ivoire");
        pas(1, mc -> CouleurTracesBanc.poser(0x6FB4FF, false), "bleu chakra");
        pas(5, mc -> shot(mc, pre + "reglage_chakra"), "capture réglage bleu chakra");
        pas(1, mc -> CouleurTracesBanc.poser(0xF29CC2, true), "Personnalisée ouverte");
        pas(6, mc -> shot(mc, pre + "reglage_perso_ouvert"), "capture sélecteur ouvert");
        // le parcours d'un vrai joueur, par les vrais événements de l'écran
        pas(2, mc -> {
            CouleurTracesBanc.poser(Traces.IVOIRE, false);
        }, "retour à l'ivoire");
        // regarder sans rien toucher : ouvrir « Personnalisée » depuis l'ivoire ne change pas la couleur
        pas(3, mc -> clic(mc, 7), "ouverture de « Personnalisée » depuis l'ivoire");
        pas(3, mc -> {
            verifie(CouleurTracesBanc.ouvert(), res + " : le sélecteur s'ouvre depuis l'ivoire");
            verifie(RebornPrefs.INSTANCE.tracesCouleur == Traces.IVOIRE, res + " : ouvrir ne change pas la couleur (" + Integer.toHexString(RebornPrefs.INSTANCE.tracesCouleur) + ")");
            shot(mc, pre + "reglage_ouvert_ivoire");
            clic(mc, 7);
        }, "refermeture sans geste");
        pas(3, mc -> {
            verifie(!CouleurTracesBanc.ouvert() && RebornPrefs.INSTANCE.tracesCouleur == Traces.IVOIRE, res + " : refermé, toujours ivoire");
        }, "bilan de l'ouverture");
        pas(3, mc -> clic(mc, 2), "clic sur la pastille bleu chakra");
        pas(2, mc -> {
            verifie(RebornPrefs.INSTANCE.tracesCouleur == 0x6FB4FF, res + " : clic sur une pastille = bleu chakra");
            clic(mc, 7);
        }, "clic sur « Personnalisée »");
        pas(3, mc -> {
            verifie(CouleurTracesBanc.ouvert(), res + " : le sélecteur s'ouvre");
            clic(mc, 8);
        }, "clic dans le carré");
        pas(2, mc -> {
            int c = RebornPrefs.INSTANCE.tracesCouleur;
            verifie(c != 0x6FB4FF, res + " : le carré change la couleur (" + Integer.toHexString(c) + ")");
            verifie(RebornPrefs.INSTANCE.tracesRecentes.length > 0 && RebornPrefs.INSTANCE.tracesRecentes[0] == c, res + " : couleur retenue dans les récentes");
            clic(mc, 9);
        }, "clic dans le champ hexadécimal");
        pas(2, mc -> {
            var e = mc.gui.screen();
            for (char ch : "#202020".toCharArray()) e.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
        }, "saisie #202020");
        pas(3, mc -> {
            int c = RebornPrefs.INSTANCE.tracesCouleur;
            verifie(c == fr.reborn.hud.concentration.HaloPlaque.Palette.relever(0x202020), res + " : une couleur trop sombre est relevée (" + Integer.toHexString(c) + ")");
            shot(mc, pre + "reglage_hex_releve");
        }, "couleur relevée");
        pas(1, mc -> {
            CouleurTracesBanc.poser(Traces.IVOIRE, false);
            mc.setScreenAndShow(null);
        }, "Paramètres fermés");
        pas(5, mc -> { }, "écran fermé");
    }

    /* ================================================================ scène */

    /** x monde selon la coordonnée de planche : la planche a +x à droite, et regard au sud, +x est à gauche. */
    private static double wx(double mx) { return ox + 0.5 - mx; }

    private static double wz(double z) { return oz + 0.5 + z; }

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        // tout d'abord le terrain à plat
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 14, oy, oz - 8, ox + 14, oy + 24, oz + 26));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 14, oy - 1, oz - 8, ox + 14, oy - 1, oz + 26));
        // chemin de pierre (planche : mx de -1,5 à 1,5)
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", ox - 1, oy - 1, oz - 6, ox + 1, oy - 1, oz + 10));
        // sable à droite de l'écran (x monde plus petit)
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:sand", ox - 9, oy - 1, oz - 3, ox - 3, oy - 1, oz + 12));
        // dalle, escalier, terrasse
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:smooth_stone_slab", ox - 2, oy, oz + 11, ox + 2, oy, oz + 11));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:spruce_stairs[facing=south]", ox - 2, oy, oz + 12, ox + 2, oy, oz + 12));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 3, oy, oz + 13, ox + 3, oy, oz + 21));
        // mur au fond, bâtisse à gauche de l'écran
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 11, oy, oz + 23, ox + 11, oy + 2, oz + 23));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:spruce_planks", ox + 8, oy, oz - 4, ox + 12, oy + 4, oz + 12));
        // lanternes au pied de la dalle
        for (int dx : new int[] {-3, 3}) {
            c.sendCommand(String.format("setblock %d %d %d minecraft:spruce_fence", ox + dx, oy, oz + 10));
            c.sendCommand(String.format("setblock %d %d %d minecraft:lantern", ox + dx, oy + 1, oz + 10));
        }
    }

    /** Le dessus de la collision à cet endroit (comme le serveur). */
    private static double dessus(Minecraft mc, double x, double z, double depuis) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double fx = x - bx, fz = z - bz;
        for (int y = (int) Math.floor(depuis); y > oy - 4; y--) {
            BlockPos p = new BlockPos(bx, y, bz);
            BlockState s = mc.level.getBlockState(p);
            VoxelShape sh = s.getCollisionShape(mc.level, p);
            double top = -1;
            for (AABB b : sh.toAabbs()) {
                if (fx >= b.minX && fx <= b.maxX && fz >= b.minZ && fz <= b.maxZ) top = Math.max(top, b.maxY);
            }
            if (top >= 0) return y + top;
        }
        return oy;
    }

    private static int vieSol(Minecraft mc, double x, double y, double z, boolean accroupi) {
        BlockPos p = BlockPos.containing(x, y - 0.01, z);
        String n = mc.level.getBlockState(p).getBlock().builtInRegistryHolder().key().identifier().getPath();
        double f = n.contains("sand") ? 2.0 : n.contains("stone") || n.contains("brick") || n.contains("spruce") || n.contains("path") ? 0.4 : 1.0;
        return (int) Math.round(7200 * f * (accroupi ? 0.5 : 1.0));
    }

    /**
     * Une piste comme le serveur la pose : un pas tous les {@code pas} blocs le long de la polyligne (coordonnées de planche),
     * âges de {@code age0} à {@code age1} minutes. Renvoie les id.
     */
    private static List<Integer> piste(Minecraft mc, double[][] pts, double pas, int fl, double age0, double age1, double k, int vieImposee) {
        List<Integer> ids = new ArrayList<>();
        double tot = 0;
        for (int i = 0; i + 1 < pts.length; i++) tot += Math.hypot(wx(pts[i + 1][0]) - wx(pts[i][0]), pts[i + 1][1] - pts[i][1]);
        int n = (int) (tot / pas) + 1;
        double fait = 0;
        int seg = 0;
        double long0 = 0;
        boolean droit = false;
        for (int i = 0; i < n; i++) {
            double s = i * pas;
            while (seg + 2 < pts.length) {
                double l = Math.hypot(wx(pts[seg + 1][0]) - wx(pts[seg][0]), pts[seg + 1][1] - pts[seg][1]);
                if (s <= long0 + l) break;
                long0 += l;
                seg++;
            }
            double ax = wx(pts[seg][0]), az = wz(pts[seg][1]), bx = wx(pts[seg + 1][0]), bz = wz(pts[seg + 1][1]);
            double l = Math.hypot(bx - ax, bz - az), u = (s - long0) / l;
            double px = ax + (bx - ax) * u, pz = az + (bz - az) * u;
            double cap = Math.toDegrees(Math.atan2(bx - ax, bz - az)), r = Math.toRadians(cap);
            double lat = (droit ? DECAL : -DECAL) * k;
            double x = px - Math.cos(r) * lat, z = pz + Math.sin(r) * lat;
            double jit = (Math.floorMod((i + 1) * 37, 7) - 3) * 0.8;
            double ang = cap + (droit ? -OUVERTURE : OUVERTURE) + jit;
            double y = dessus(mc, x, z, oy + 3.5);
            int vie = vieImposee > 0 ? vieImposee : vieSol(mc, x, y, z, (fl & A) != 0);
            int age = (int) Math.round((age0 + (age1 - age0) * (i / (double) Math.max(1, n - 1))) * 60);
            int id = prochainId++;
            SCENE.add(new Pas(id, x, y, z, Math.floorMod((int) Math.round(ang), 360), fl | (droit ? D : 0), age, vie));
            ids.add(id);
            droit = !droit;
        }
        return ids;
    }

    private static void scene(Consumer<Minecraft> f, String nom) {
        pas(1, mc -> {
            SCENE.clear();
            f.accept(mc);
            System.out.println("[lot4c7] scène " + nom + " : " + SCENE.size() + " pas");
        }, "scène " + nom);
    }

    private static void scenePistes(Minecraft mc) {
        piste(mc, new double[][] {{0.55, 0.8}, {0.55, 20.4}}, 1.2, 0, 0.6, 0.1, 1, 0);
        piste(mc, new double[][] {{7.5, 2.0}, {-6.5, 11.5}}, 2.0, S, 0.25, 0.0, 1, 0);
        piste(mc, new double[][] {{-3.3, 1.6}, {-3.1, 5.0}, {-2.7, 8.4}}, 0.8, A, 1.6, 1.0, 1, 0);
        piste(mc, new double[][] {{5.6, 6.0}, {5.3, 9.0}, {6.0, 11.8}}, 1.2, 0, 200.0, 170.0, 1, 0);
    }

    private static void sceneAge(Minecraft mc) {
        piste(mc, new double[][] {{-6.6, 3.4}, {6.6, 3.4}}, 1.2, 0, 10.4, 0.0, 1, 660);
        piste(mc, new double[][] {{-6.6, 1.6}, {6.6, 1.6}}, 0.8, A, 5.1, 0.0, 1, 330);
    }

    private static void sceneGrosPlan(Minecraft mc) {
        // devant la caméra, dans l'herbe : trois paires d'âges croissants, puis les drapeaux
        double[][] rang = {{-1.5, 1.8}, {1.5, 1.8}};
        piste(mc, rang, 0.5, 0, 0.5, 0.5, 1, 600);
        rang = new double[][] {{-1.5, 1.0}, {1.5, 1.0}};
        piste(mc, rang, 0.5, 0, 6.5, 6.5, 1, 600);
        rang = new double[][] {{-1.5, 0.3}, {1.5, 0.3}};
        piste(mc, rang, 0.5, 0, 9.4, 9.4, 1, 600);
        rang = new double[][] {{-1.5, 2.6}, {-0.6, 2.6}};
        piste(mc, rang, 0.5, S, 0.1, 0.1, 1, 600);
        rang = new double[][] {{-0.2, 2.6}, {0.7, 2.6}};
        piste(mc, rang, 0.5, A, 0.1, 0.1, 1, 300);
        rang = new double[][] {{1.0, 2.6}, {1.9, 2.6}};
        piste(mc, rang, 0.5, C, 0.1, 0.1, 1, 600);
        rang = new double[][] {{2.2, 1.8}, {2.9, 1.8}};
        piste(mc, rang, 0.5, SANG, 0.1, 0.1, 1, 600);
        // une marche vers le fond : gauche, droite, pointes vers l'extérieur
        rang = new double[][] {{-3.0, 0.0}, {-3.0, 3.2}};
        piste(mc, rang, 0.8, 0, 0.1, 0.1, 1, 600);
    }

    private static void sceneSurvol(Minecraft mc) {
        List<Integer> fraiche = piste(mc, new double[][] {{0.55, 0.8}, {0.55, 14.0}}, 1.2, 0, 0.3, 0.05, 1, 0);
        List<Integer> vieille = piste(mc, new double[][] {{5.0, 1.0}, {4.6, 12.0}}, 1.2, 0, 7.0, 7.0, 1, 1200);
        pasFrais = fraiche.get(2);
        pasVieux = vieille.get(2);
    }

    /* ================================================================ serveur simulé */

    private static void serveur(JsonObject in) {
        String a = in.has("a") ? in.get("a").getAsString() : "";
        if ("on".equals(a)) {
            FILE.add("{\"v\":1,\"t\":\"etat\",\"on\":true}");
            FILE.add("{\"v\":1,\"t\":\"ancres\",\"l\":[]}");
            FILE.add(message());
        }
    }

    /** Le message tel que {@code TraceProtocole} l'écrit côté serveur. */
    private static String message() {
        StringBuilder s = new StringBuilder("{\"v\":1,\"t\":\"traces\",\"plein\":true,\"o\":[");
        s.append(ox).append(',').append(oy).append(',').append(oz).append("],\"a\":[");
        boolean premier = true;
        for (Pas p : SCENE) {
            if (!premier) s.append(',');
            premier = false;
            s.append(p.id()).append(',').append(4242).append(',').append(Math.round((p.x() - ox) * 32)).append(',')
                .append(Math.round((p.y() - oy) * 32)).append(',').append(Math.round((p.z() - oz) * 32)).append(',')
                .append(p.ang()).append(',').append(p.fl()).append(',').append(100).append(',').append(p.age()).append(',').append(p.vie());
        }
        return s.append("],\"r\":[]}").toString();
    }

    /* ================================================================ outils */

    private static void clic(Minecraft mc, int point) {
        int[] p = CouleurTracesBanc.point(point);
        var ev = new net.minecraft.client.input.MouseButtonEvent(p[0], p[1], new net.minecraft.client.input.MouseButtonInfo(0, 0));
        mc.gui.screen().mouseClicked(ev, false);
        mc.gui.screen().mouseReleased(ev);
    }

    private static void curseurSur(Minecraft mc, int id) {
        double[] p = Traces.position(id);
        if (p == null || !ProjectionMonde.versGui(p[0], p[1], p[2], V)) {
            verifie(false, "pas " + id + " hors écran");
            return;
        }
        InteractionMode.INSTANCE.debugCursor(V.x, V.y);
    }

    /** Le joueur au sol en caméra d'épaule, regard au sud, inclinaison {@code pitch} (l'orbite de la caméra Reborn). */
    private static void epaule(Minecraft mc, double mx, double z, float pitch) {
        a(mc, mx, z, 0f, pitch, 0.0);
        RebornCamera.INSTANCE.initOrientation(0f, pitch);
    }

    private static void a(Minecraft mc, double mx, double z, float yaw, float pitch, double dy) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", wx(mx), oy + dy, wz(z), yaw, pitch));
    }

    private static void entrer(Minecraft mc) {
        avance(300);
        boolean ok = ModeConcentration.entrer();
        verifie(ok, "entrée en concentration (refus : " + ModeConcentration.dernierRefus() + ")");
    }

    private static void sortir() {
        ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE);
        avance(600);
    }

    private static void figer(long now) {
        h = now;
        ModeConcentration.horloge = () -> h;
        Plaques.horloge = () -> h;
        Recit.horloge = () -> h;
        Traces.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4c7] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4c7] " + nom);
            action.accept(mc);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (volant) vol(mc, true);
        String r = FILE.poll();
        if (r != null) ConcentrationCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
