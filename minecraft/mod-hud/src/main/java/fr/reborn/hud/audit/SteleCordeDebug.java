package fr.reborn.hud.audit;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.corde.CordeLigotes;
import fr.reborn.hud.immersion.PhotoMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Banc des maquettes v2 de la <b>stèle</b> (variante A, {@code reborn:stele_uchiha} / {@code reborn:stele_pierre}) et de
 * la <b>corde de ligotage</b> (équipement {@code reborn:corde} / {@code reborn:corde_haut}), <b>inactif en production</b> :
 * {@code REBORN_STELE_CORDE=1} (monde solo de dev, jamais de serveur).
 *
 * <p>Le pack de dev {@code run/resourcepacks/reborn_stele_corde_dev} (copie de {@code Maquettes/gameplan/20_stele_corde/v2/pack})
 * est activé le temps du banc. Scène posée à 24 blocs à l'est du joueur : sol d'herbe, la stèle (un {@code item_display}
 * « fixed », pied au sol), un mannequin debout à côté pour l'échelle, puis deux mannequins ligotés (haut seul ; haut et
 * bas). Caméra libre du mode photo, joueur invisible et placé derrière elle. Captures {@code run/screenshots/v2_stele_*.png}
 * et {@code v2_corde_*.png} ; de jour, puis la stèle au crépuscule et de nuit. <b>Le monde est remis en état</b> (blocs
 * relevés puis rendus, entités du banc retirées, heure, météo, mode de jeu, pack, caméra), puis le jeu se ferme.
 *
 * <p>Depuis le 25/11, {@code reborn:stele_uchiha} est la stèle <b>modélisée par le propriétaire</b> (son export Blockbench
 * tel quel) et notre stèle générée devient {@code reborn:stele_uchiha_v2}. Après les vues de la v2, le banc pose sa stèle
 * à sa taille d'origine, la même grossie 1,5 fois et la nôtre côte à côte : captures {@code v2_user_stele_*.png}.
 * {@code REBORN_STELE_CORDE=uchiha} ne fait que cette partie (sans les vues de la v2 ni la corde).
 *
 * <p>Depuis le 26/11, la corde v3 dessinée par le mod ({@code fr.reborn.hud.corde}) : trois mannequins mis dans la liste
 * des ligotés du client (haut seul, complet, complet sur bras fins) à côté d'un mannequin en v2, une marche, le joueur
 * local ligoté ; captures {@code v3_corde_*.png}. {@code REBORN_STELE_CORDE=corde} ne fait que cette partie.
 */
public final class SteleCordeDebug {

    private record Etape(int delai, Predicate<Minecraft> action, String nom) { }

    private static final String PACK = "file/reborn_stele_corde_dev";
    private static final String TAG = "sc_banc";
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static final Map<BlockPos, BlockState> AVANT = new LinkedHashMap<>();
    private static int t = -1, etape, prochaine, attente;
    private static int images, imagesEtape, imagesCapture;
    private static String capture;
    private static boolean packAjoute;
    private static int ox, oy, oz;
    private static float oyaw, opitch;
    private static String mode = "creative";
    private static long heure = -1;
    private static boolean pluie;
    private static RebornCamera.Mode cameraAvant;
    /** Centre de la stèle, des deux mannequins ligotés et de leur milieu (pieds au sol). */
    private static Vec3 S, C1, C2, C3, M;
    /** Corde v3 : mannequin en v2 pour comparer, milieu C3-C4, place du joueur local. */
    private static Vec3 C4, CMP, J;
    /** Mannequin qui marche (téléporté de {@link #PAS_MARCHE} par tick) et ticks restants. */
    private static String marcheTag;
    private static int marcheReste, marcheId = -1, temoinId = -1;
    private static final double PAS_MARCHE = 0.11;
    /** Vue du joueur local : la caméra libre ne déplace pas le joueur ; vue de la caméra à rendre ensuite. */
    private static boolean joueurFixe;
    private static net.minecraft.client.CameraType cameraJoueur;

    private SteleCordeDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_STELE_CORDE");
        boolean tout = "1".equals(v);
        boolean v3 = "corde".equals(v);
        if (!tout && !v3 && !"uchiha".equals(v)) return;

        pas(5, SteleCordeDebug::preparer, "préparation");
        pas(4, mc -> packDev(mc, true), "pack de dev activé");
        attendre(mc -> mc.gui.overlay() == null && t > 120, 600, "rechargement du pack");
        pas(10, SteleCordeDebug::verifierPack, "pack chargé ?");
        pas(2, SteleCordeDebug::batir, "scène : sol d'herbe");
        pas(10, SteleCordeDebug::poser, "stèle, mannequins, cordes");
        pas(40, mc -> System.out.println("[stele] entités du banc : " + compter(mc)), "attente de l'arrivée des entités");

        if (tout) vuesSteleV2();

        if (!v3) {
            // ---- la stèle du propriétaire (reborn:stele_uchiha) : taille d'origine, grossie 1,5 fois, la nôtre à côté
            pas(4, SteleCordeDebug::poserUchiha, "stèle du propriétaire : 1x, 1,5x, la nôtre, règle au sol");
            pas(30, mc -> System.out.println("[stele] entités du banc : " + compter(mc)), "attente de l'arrivée des entités");
            vue("v2_user_stele_face", null, 0, 1.0, 5.5, 0, 0.8, 0, 55);
            // trois quarts gauche un peu haut : le chant gauche et le chapeau de la 1x se lisent, la 1,5x entre à gauche
            vue("v2_user_stele_trois_quarts", null, -2.3, 1.9, 3.0, 0, 0.7, 0.2, 55);
            vue("v2_user_stele_dos", null, 2.6, 1.5, -4.6, 0, 0.75, 0, 55);
            vue("v2_user_stele_pres", null, 0, 0.85, 2.05, 0, 0.85, 0.42, 50);
            vue("v2_user_stele_comparaison", null, 0.4, 1.9, 9.5, 0.4, 1.2, 0, 55);
            pas(2, mc -> cmd(mc, "time set 18000"), "nuit");
            vue("v2_user_stele_nuit", null, -2.3, 1.9, 3.0, 0, 0.7, 0.2, 55);
            pas(2, mc -> cmd(mc, "time set 6000"), "retour au jour");
        }

        if (tout) vuesCorde();
        if (tout || v3) vuesCordeV3();

        pas(4, SteleCordeDebug::remettre, "remise en état");
        pas(20, mc -> packDev(mc, false), "pack de dev retiré");
        pas(40, mc -> {
            System.out.println("[stele] FIN");
            mc.stop();
        }, "fin");

        ClientTickEvents.END_CLIENT_TICK.register(SteleCordeDebug::tick);
        // en mode photo le HUD est coupé : les images se comptent à la fin du rendu du monde
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(ctx -> images++);
    }

    /** Notre stèle v2 ({@code reborn:stele_uchiha_v2}, posée en S) : de jour, nue, au crépuscule et de nuit. */
    private static void vuesSteleV2() {
        // ---- la stèle, de jour
        vue("v2_stele_face", null, 0, 1.5, 7.0, 0, 1.35, 0, 55);
        vue("v2_stele_trois_quarts", null, -4.4, 1.8, 4.8, -0.2, 1.4, 0, 55);
        vue("v2_stele_trois_quarts_droite", null, 4.0, 3.2, 5.0, 0.3, 1.0, 0, 55);
        vue("v2_stele_trois_quarts_bas", null, -4.2, 1.1, 4.6, 0, 1.45, 0, 55);
        vue("v2_stele_dos", null, -3.8, 2.0, -5.6, 0, 1.3, 0, 55);
        vue("v2_stele_pres_eventail", null, -0.6, 2.3, 2.2, 0, 2.15, 0.2, 50);
        vue("v2_stele_pres_coin", null, -2.3, 3.1, 1.9, -1.3, 2.6, 0.2, 50);
        vue("v2_stele_pres_chant", null, 2.6, 2.6, 0.9, 1.4, 2.5, 0.0, 50);
        vue("v2_stele_pres_socle", null, 2.5, 0.8, 2.5, 1.4, 0.3, 0.4, 50);
        vue("v2_stele_loin", null, 3.0, 2.6, 13.0, 0, 1.2, 0, 60);
        pas(2, mc -> cmd(mc, "data merge entity @e[tag=sc_stele,limit=1] {item:{id:\"minecraft:paper\",count:1,"
            + "components:{\"minecraft:item_model\":\"reborn:stele_pierre\"}}}"), "stèle nue (stele_pierre)");
        vue("v2_stele_pierre_trois_quarts", null, -4.4, 1.8, 4.8, -0.2, 1.4, 0, 55);
        pas(2, mc -> cmd(mc, "data merge entity @e[tag=sc_stele,limit=1] {item:{id:\"minecraft:paper\",count:1,"
            + "components:{\"minecraft:item_model\":\"reborn:stele_uchiha_v2\"}}}"), "stèle gravée");
        pas(2, mc -> cmd(mc, "time set 12700"), "crépuscule");
        vue("v2_stele_crepuscule", null, -4.4, 1.8, 4.8, -0.2, 1.4, 0, 55);
        pas(2, mc -> cmd(mc, "time set 18000"), "nuit");
        vue("v2_stele_nuit", null, -4.4, 1.8, 4.8, -0.2, 1.4, 0, 55);
        pas(2, mc -> cmd(mc, "time set 6000"), "retour au jour");
    }

    private static void vuesCorde() {
        // ---- la corde, de jour (C1 : haut seul, bras larges ; C2 : haut et bas, bras larges ; C3 : haut et bas, bras fins)
        vue("v2_corde_face", "M", 0, 1.2, 7.0, 0, 1.0, 0, 50);
        vue("v2_corde_trois_quarts", "M", 4.4, 2.0, 5.6, 0, 1.0, 0, 50);
        vue("v2_corde_dos", "M", 0, 1.3, -7.0, 0, 1.0, 0, 50);
        vue("v2_corde_trois_quarts_dos", "M", -4.4, 2.0, -5.6, 0, 1.0, 0, 50);
        vue("v2_corde_complet_buste_face", "C2", 0, 1.45, 1.6, 0, 1.2, 0, 50);
        vue("v2_corde_complet_buste_dos", "C2", 0, 1.5, -1.6, 0, 1.25, 0, 50);
        vue("v2_corde_complet_profil", "C2", 1.8, 1.2, 0.3, 0, 1.0, 0, 55);
        vue("v2_corde_complet_jambes", "C2", 0.5, 0.75, 1.6, 0, 0.45, 0, 50);
        vue("v2_corde_haut_buste_trois_quarts", "C1", -1.3, 1.5, 1.4, 0, 1.2, 0, 50);
        vue("v2_corde_complet_trois_quarts_haut", "C2", 1.4, 2.4, 1.5, 0, 1.2, 0, 50);
        vue("v2_corde_fins_buste_face", "C3", 0, 1.45, 1.6, 0, 1.2, 0, 50);
        vue("v2_corde_fins_buste_dos", "C3", 0, 1.5, -1.6, 0, 1.25, 0, 50);
        vue("v2_corde_fins_profil", "C3", 2.2, 1.2, 0.3, 0, 1.0, 0, 55);
    }

    /**
     * Corde v3, dessinée par le mod ({@code fr.reborn.hud.corde}) : de gauche à droite vus de face, C1 haut seul (bras
     * larges), C2 complet (bras larges), C3 complet (bras fins), C4 la v2 en armure (complet, bras larges) pour
     * comparer. Les trois premiers sont mis dans la liste des ligotés du client (rien ne part au serveur). Puis C1
     * marche (téléporté pas à pas : le client anime ses jambes) et le joueur local, visible, se voit ligoté.
     */
    private static void vuesCordeV3() {
        pas(4, SteleCordeDebug::poserV3, "corde v3 : mannequins ligotés par le mod, v2 à côté");
        pas(30, SteleCordeDebug::ligoter, "liste des ligotés (client)");
        pas(30, mc -> System.out.println("[stele] liste des ligotés vide ? " + CordeLigotes.vide()), "fondu de la pose");
        vue("v3_corde_face", "M", 0, 1.2, 7.6, 0, 1.0, 0, 55);
        vue("v3_corde_trois_quarts", "M", 4.8, 2.0, 6.0, 0, 1.0, 0, 55);
        vue("v3_corde_dos", "M", 0, 1.3, -7.6, 0, 1.0, 0, 55);
        vue("v3_corde_trois_quarts_dos", "M", -4.8, 2.0, -6.0, 0, 1.0, 0, 55);
        vue("v3_corde_buste_face", "C2", 0, 1.45, 1.6, 0, 1.2, 0, 50);
        vue("v3_corde_buste_dos", "C2", 0, 1.5, -1.6, 0, 1.25, 0, 50);
        vue("v3_corde_buste_trois_quarts_dos", "C2", -1.2, 1.7, -1.3, 0, 1.2, 0, 50);
        vue("v3_corde_profil", "C2", 1.7, 1.2, 0.2, 0, 1.0, 0, 55);
        vue("v3_corde_jambes", "C2", 0.5, 0.75, 1.6, 0, 0.45, 0, 50);
        vue("v3_corde_haut_buste_trois_quarts", "C1", -1.3, 1.5, 1.4, 0, 1.2, 0, 50);
        vue("v3_corde_fins", "C3", -1.3, 1.5, 1.4, 0, 1.2, 0, 50);
        vue("v3_corde_fins_dos", "C3", 1.2, 1.6, -1.4, 0, 1.2, 0, 50);
        vue("v3_corde_comparaison", "CMP", 0, 1.4, 4.4, 0, 1.0, 0, 50);
        vue("v3_corde_comparaison_dos", "CMP", 0, 1.5, -4.4, 0, 1.1, 0, 50);
        // ---- C1 (haut seul) marche vers +z : jambes libres, bras et corde tenus
        pas(2, mc -> {
            cmd(mc, String.format(Locale.ROOT, "tp @e[tag=sc_c1,limit=1] %.3f %d %.3f 0 0", C1.x, oy, C1.z - 1.6));
            marcheTag = "sc_c1";
            marcheReste = 60;
        }, "C1 marche");
        vue("v3_corde_marche", "C1", -2.6, 1.1, 0.2, 0, 0.9, 0.2, 55);
        vue("v3_corde_marche_dos", "C1", -1.8, 1.4, -1.6, 0, 1.0, 0.6, 55);
        // témoin : C4 (v2 en armure, sans pose du mod) reçoit la même démarche sur place
        vue("v3_corde_marche_temoin", "C4", 2.6, 1.1, 0.2, 0, 0.9, 0.2, 55);
        pas(30, mc -> cmd(mc, String.format(Locale.ROOT, "tp @e[tag=sc_c1,limit=1] %.3f %d %.3f 0 0", C1.x, oy, C1.z)),
            "C1 revient");
        // ---- le joueur local, visible, ligoté (complet), vu en troisième personne
        pas(2, mc -> {
            cmd(mc, "effect clear @s minecraft:invisibility");
            cmd(mc, String.format(Locale.ROOT, "tp @s %.3f %d %.3f 0 0", J.x, oy, J.z));
            cameraJoueur = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            CordeLigotes.poser(mc.player.getId(), CordeLigotes.COMPLET);
            joueurFixe = true;
        }, "joueur local ligoté");
        pas(20, mc -> { }, "fondu de la pose du joueur");
        vue("v3_corde_joueur_local", "J", 1.2, 1.5, 2.4, 0, 1.1, 0, 55);
        vue("v3_corde_joueur_local_dos", "J", -1.2, 1.6, -2.4, 0, 1.1, 0, 55);
        pas(2, mc -> {
            CordeLigotes.retirer(mc.player.getId());
            joueurFixe = false;
            if (cameraJoueur != null) mc.options.setCameraType(cameraJoueur);
            cmd(mc, "effect give @s minecraft:invisibility infinite 0 true");
        }, "joueur local libéré");
    }

    private static void poserV3(Minecraft mc) {
        int bx = ox + 24, bz = oz;
        C1 = new Vec3(bx + 13.5, oy, bz + 0.5);
        C2 = new Vec3(bx + 16.0, oy, bz + 0.5);
        C3 = new Vec3(bx + 18.5, oy, bz + 0.5);
        C4 = new Vec3(bx + 21.0, oy, bz + 0.5);
        M = new Vec3(bx + 17.25, oy, bz + 0.5);
        CMP = new Vec3(bx + 19.75, oy, bz + 0.5);
        J = new Vec3(bx + 9.5, oy, bz + 0.5);
        // reposés à neuf (un mannequin téléporté garde, côté client, le corps tourné vers son déplacement)
        String[] tags = {"sc_c1", "sc_c2", "sc_c3"};
        Vec3[] pos = {C1, C2, C3};
        for (int i = 0; i < 3; i++) {
            cmd(mc, "kill @e[tag=" + tags[i] + "]");
            mannequin(mc, pos[i].x, pos[i].z, 0f, tags[i]);
            if (i < 2) {
                cmd(mc, "data merge entity @e[tag=" + tags[i] + ",limit=1] {profile:{texture:\"entity/player/wide/steve\",model:\"wide\"}}");
            }
        }
        mannequin(mc, C4.x, C4.z, 0f, "sc_c4");
        cmd(mc, "data merge entity @e[tag=sc_c4,limit=1] {profile:{texture:\"entity/player/wide/steve\",model:\"wide\"}}");
        cmd(mc, "item replace entity @e[tag=sc_c4,limit=1] armor.chest with minecraft:string[minecraft:equippable={slot:\"chest\",asset_id:\"reborn:corde\"}]");
        cmd(mc, "item replace entity @e[tag=sc_c4,limit=1] armor.legs with minecraft:string[minecraft:equippable={slot:\"legs\",asset_id:\"reborn:corde\"}]");
    }

    /** Les mannequins C1, C2, C3 du client (les étiquettes ne sont pas envoyées : on les trouve par leur position). */
    private static void ligoter(Minecraft mc) {
        Vec3[] pos = {C1, C2, C3};
        byte[] var = {CordeLigotes.HAUT, CordeLigotes.COMPLET, CordeLigotes.COMPLET};
        for (int i = 0; i < 3; i++) {
            for (var e : mc.level.entitiesForRendering()) {
                if (!(e instanceof net.minecraft.world.entity.decoration.Mannequin)) continue;
                double dx = e.getX() - pos[i].x, dz = e.getZ() - pos[i].z;
                if (dx * dx + dz * dz > 0.1) continue;
                CordeLigotes.poser(e.getId(), var[i]);
                if (i == 0) marcheId = e.getId();
                System.out.println("[stele] ligoté C" + (i + 1) + " : entité " + e.getId() + " variante " + var[i]);
            }
        }
        for (var e : mc.level.entitiesForRendering()) {
            double dx = e.getX() - C4.x, dz = e.getZ() - C4.z;
            if (e instanceof net.minecraft.world.entity.decoration.Mannequin && dx * dx + dz * dz < 0.1) temoinId = e.getId();
        }
    }

    /* ================================================================ étapes de la scène */

    private static void preparer(Minecraft mc) {
        ox = mc.player.getBlockX();
        oy = mc.player.getBlockY();
        oz = mc.player.getBlockZ();
        oyaw = mc.player.getYRot();
        opitch = mc.player.getXRot();
        if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
        var srv = mc.getSingleplayerServer();
        if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
        int bx = ox + 24, bz = oz;
        S = new Vec3(bx + 0.5, oy, bz + 0.5);
        C1 = new Vec3(bx + 14.5, oy, bz + 0.5);
        C2 = new Vec3(bx + 16.5, oy, bz + 0.5);
        C3 = new Vec3(bx + 20.5, oy, bz + 0.5);
        M = new Vec3(bx + 17.5, oy, bz + 0.5);
        cmd(mc, "gamemode creative");
        cmd(mc, "time set 6000");
        cmd(mc, "gamerule advance_time false");
        cmd(mc, "weather clear");
        cmd(mc, "effect give @s minecraft:invisibility infinite 0 true");
        cameraAvant = RebornCamera.INSTANCE.mode();
        System.out.println("[stele] origine " + ox + " " + oy + " " + oz);
    }

    private static void verifierPack(Minecraft mc) {
        var rm = mc.getResourceManager();
        for (String p : new String[] {"textures/item/stele_uchiha.png", "models/item/stele_uchiha.json", "items/stele_uchiha.json",
                "textures/item/stele_uchiha_v2.png", "models/item/stele_uchiha_v2.json", "items/stele_uchiha_v2.json",
                "equipment/corde.json", "textures/entity/equipment/humanoid/corde.png",
                "textures/entity/equipment/humanoid_leggings/corde.png", "textures/entity/equipment/humanoid_leggings/corde_torse.png"}) {
            boolean ok = rm.getResource(Identifier.fromNamespaceAndPath("reborn", p)).isPresent();
            System.out.println("[stele] " + (ok ? "OK " : "ABSENT ") + "reborn:" + p);
        }
        System.out.println("[stele] packs actifs : " + mc.getResourcePackRepository().getSelectedIds());
    }

    /** Sol d'herbe de 34 × 20 et air au-dessus (11 blocs), chaque bloc relevé pour être rendu à la fin. */
    private static void batir(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var uuid = mc.player.getUUID();
        int bx = ox + 24, bz = oz, by = oy;
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var lvl = (net.minecraft.server.level.ServerLevel) sp.level();
            for (int x = -9; x <= 24; x++) {
                for (int z = -9; z <= 10; z++) {
                    for (int y = -1; y <= 10; y++) {
                        BlockState s = y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        BlockPos p = new BlockPos(bx + x, by + y, bz + z);
                        AVANT.putIfAbsent(p, lvl.getBlockState(p));
                        lvl.setBlock(p, s, Block.UPDATE_CLIENTS);
                    }
                }
            }
            int hMax = 0;
            for (int x = -9; x <= 24; x++) {
                for (int z = -9; z <= 10; z++) {
                    hMax = Math.max(hMax, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx + x, bz + z));
                }
            }
            System.out.println("[stele] surface la plus haute au-dessus de la scène : y " + hMax + " (sol à " + by + ")");
        });
        cmd(mc, String.format(Locale.ROOT, "kill @e[type=minecraft:item,x=%d,y=%d,z=%d,dx=34,dy=12,dz=20]", bx - 9, by - 1, bz - 9));
    }

    private static void poser(Minecraft mc) {
        cmd(mc, "kill @e[tag=" + TAG + "]");
        cmd(mc, String.format(Locale.ROOT, "summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\",\"sc_stele\"],"
            + "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"reborn:stele_uchiha_v2\"}},"
            + "item_display:\"fixed\",width:4f,height:3.5f,Rotation:[0f,0f]}", S.x, S.y, S.z, TAG));
        // pour l'échelle : un joueur debout à droite de la stèle
        mannequin(mc, S.x + 2.7, S.z + 0.3, -15f, "sc_echelle");
        mannequin(mc, C1.x, C1.z, 0f, "sc_c1");
        mannequin(mc, C2.x, C2.z, 0f, "sc_c2");
        mannequin(mc, C3.x, C3.z, 0f, "sc_c3");
        // bras larges (Steve) pour C1 et C2 ; C3 garde la peau par défaut du mannequin (bras fins)
        for (String c : new String[] {"sc_c1", "sc_c2"}) {
            cmd(mc, "data merge entity @e[tag=" + c + ",limit=1] {profile:{texture:\"entity/player/wide/steve\",model:\"wide\"}}");
        }
        // haut seul : bras (plastron) + torse (jambières corde_haut) ; complet : bras + torse et jambes
        cmd(mc, "item replace entity @e[tag=sc_c1,limit=1] armor.chest with minecraft:string[minecraft:equippable={slot:\"chest\",asset_id:\"reborn:corde\"}]");
        cmd(mc, "item replace entity @e[tag=sc_c1,limit=1] armor.legs with minecraft:string[minecraft:equippable={slot:\"legs\",asset_id:\"reborn:corde_haut\"}]");
        for (String c : new String[] {"sc_c2", "sc_c3"}) {
            cmd(mc, "item replace entity @e[tag=" + c + ",limit=1] armor.chest with minecraft:string[minecraft:equippable={slot:\"chest\",asset_id:\"reborn:corde\"}]");
            cmd(mc, "item replace entity @e[tag=" + c + ",limit=1] armor.legs with minecraft:string[minecraft:equippable={slot:\"legs\",asset_id:\"reborn:corde\"}]");
        }
    }

    /**
     * La stèle du propriétaire, telle qu'exportée de Blockbench (aucune retouche du modèle) : son affichage « fixed »
     * la descend de 4,5 px, son pied est donc 12,5 px (0,78125 bloc) sous l'entité ; la translation de l'item_display
     * le ramène au sol. La copie grossie 1,5 fois l'est par l'échelle de l'item_display (pied à 1,171875 bloc sous
     * l'entité avant correction). Notre stèle v2 part à droite, le mannequin d'échelle se place entre les deux, et une
     * règle de béton blanc et noir (un bloc par case) court au sol devant les trois.
     */
    private static void poserUchiha(Minecraft mc) {
        cmd(mc, String.format(Locale.ROOT, "tp @e[tag=sc_stele,limit=1] %.3f %d %.3f 0 0", S.x + 5.0, oy, S.z));
        cmd(mc, "data merge entity @e[tag=sc_stele,limit=1] {item:{id:\"minecraft:paper\",count:1,"
            + "components:{\"minecraft:item_model\":\"reborn:stele_uchiha_v2\"}}}");
        steleUchiha(mc, S.x, 1f, "sc_user");
        steleUchiha(mc, S.x - 4.3, 1.5f, "sc_user15");
        cmd(mc, String.format(Locale.ROOT, "tp @e[tag=sc_echelle,limit=1] %.3f %d %.3f -15 0", S.x + 1.95, oy, S.z + 0.3));
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var uuid = mc.player.getUUID();
        int bx = ox + 24, bz = oz, by = oy;
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var lvl = (net.minecraft.server.level.ServerLevel) sp.level();
            for (int x = -7; x <= 7; x++) {
                BlockPos p = new BlockPos(bx + x, by - 1, bz + 1);
                AVANT.putIfAbsent(p, lvl.getBlockState(p));
                lvl.setBlock(p, Blocks.CONCRETE.pick((x & 1) == 0 ? net.minecraft.world.item.DyeColor.WHITE : net.minecraft.world.item.DyeColor.BLACK)
                    .defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            }
        });
    }

    private static void steleUchiha(Minecraft mc, double x, float echelle, String tag) {
        float pied = 12.5f / 16f * echelle;
        cmd(mc, String.format(Locale.ROOT, "summon minecraft:item_display %.3f %d %.3f {Tags:[\"%s\",\"%s\"],"
            + "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"reborn:stele_uchiha\"}},"
            + "item_display:\"fixed\",width:%.1ff,height:%.1ff,Rotation:[0f,0f],transformation:{left_rotation:[0f,0f,0f,1f],"
            + "right_rotation:[0f,0f,0f,1f],translation:[0f,%.6ff,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
            x, oy, S.z, TAG, tag, 2.5f * echelle, 2f * echelle, pied, echelle, echelle, echelle));
    }

    private static void mannequin(Minecraft mc, double x, double z, float lacet, String tag) {
        cmd(mc, String.format(Locale.ROOT, "summon minecraft:mannequin %.3f %d %.3f {Tags:[\"%s\",\"%s\"],Rotation:[%.1ff,0f],"
            + "immovable:1b,hide_description:1b}", x, oy, z, TAG, tag, lacet));
    }

    /**
     * Une vue : caméra libre du mode photo posée en {@code origine + (dx, dy, dz)}, visant {@code origine + (cx, cy, cz)} ;
     * le joueur (invisible) est posé au sol, 3 blocs derrière la caméra. Capture après 12 ticks et 3 images.
     */
    private static void vue(String nom, String origine, double dx, double dy, double dz, double cx, double cy, double cz, float fov) {
        pas(2, mc -> {
            Vec3 o = "M".equals(origine) ? M : "C1".equals(origine) ? C1 : "C2".equals(origine) ? C2 : "C3".equals(origine) ? C3
                : "C4".equals(origine) ? C4 : "CMP".equals(origine) ? CMP : "J".equals(origine) ? J : S;
            Vec3 de = o.add(dx, dy, dz), vers = o.add(cx, cy, cz);
            Vec3 d = vers.subtract(de);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            Vec3 h = new Vec3(d.x, 0, d.z).normalize();
            Vec3 joueur = de.subtract(h.scale(3.0));
            if (!joueurFixe) cmd(mc, String.format(Locale.ROOT, "tp @s %.3f %d %.3f %.1f -90", joueur.x, oy, joueur.z, yaw));
            if (!PhotoMode.INSTANCE.isActive()) PhotoMode.INSTANCE.begin(mc);
            cadrer(de, yaw, pitch, fov);
        }, "cadrage " + nom);
        pas(12, mc -> shot(nom), "capture " + nom);
    }

    /** Pose la caméra libre du mode photo (champs privés : le mode n'a pas d'accès en écriture à la position). */
    private static void cadrer(Vec3 p, float yaw, float pitch, float fov) {
        try {
            PhotoMode pm = PhotoMode.INSTANCE;
            for (String[] f : new String[][] {{"x", "" + p.x}, {"y", "" + p.y}, {"z", "" + p.z}}) {
                var c = PhotoMode.class.getDeclaredField(f[0]);
                c.setAccessible(true);
                c.setDouble(pm, Double.parseDouble(f[1]));
            }
            var fy = PhotoMode.class.getDeclaredField("yaw");
            var fp = PhotoMode.class.getDeclaredField("pitch");
            fy.setAccessible(true);
            fp.setAccessible(true);
            fy.setFloat(pm, yaw);
            fp.setFloat(pm, pitch);
            pm.setFov(fov);
            pm.setRoll(0f);
        } catch (ReflectiveOperationException e) {
            System.out.println("[stele] ECHEC caméra libre : " + e);
        }
    }

    private static void remettre(Minecraft mc) {
        if (PhotoMode.INSTANCE.isActive()) PhotoMode.INSTANCE.end(mc);
        CordeLigotes.vider();
        marcheReste = 0;
        cmd(mc, "kill @e[tag=" + TAG + "]");
        detruire(mc);
        cmd(mc, "effect clear @s minecraft:invisibility");
        cmd(mc, "gamerule advance_time true");
        if (heure >= 0) cmd(mc, "time set " + heure);
        cmd(mc, pluie ? "weather rain" : "weather clear");
        cmd(mc, String.format(Locale.ROOT, "tp @s %d %d %d %.1f %.1f", ox, oy, oz, oyaw, opitch));
        cmd(mc, "gamemode " + mode);
        if (cameraAvant != null) {
            RebornCamera.INSTANCE.setMode(cameraAvant);
            RebornCamera.INSTANCE.tickView(mc);
        }
    }

    private static void detruire(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null || AVANT.isEmpty()) return;
        var uuid = mc.player.getUUID();
        Map<BlockPos, BlockState> copie = new LinkedHashMap<>(AVANT);
        AVANT.clear();
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var lvl = (net.minecraft.server.level.ServerLevel) sp.level();
            for (var e : copie.entrySet()) lvl.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        });
    }

    /* ================================================================ outils */

    private static int compter(Minecraft mc) {
        int n = 0;
        for (var e : mc.level.entitiesForRendering()) {
            // les étiquettes ne sont pas envoyées au client : on compte mannequins et item_display autour de la scène
            boolean type = e instanceof net.minecraft.world.entity.decoration.Mannequin || e instanceof net.minecraft.world.entity.Display.ItemDisplay;
            if (type && e.position().distanceTo(S) < 30) n++;
        }
        return n;
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static void packDev(Minecraft mc, boolean on) {
        try {
            var repo = mc.getResourcePackRepository();
            repo.reload();
            if (!repo.isAvailable(PACK)) { System.out.println("[stele] ECHEC pack introuvable : " + PACK); return; }
            Collection<String> sel = new ArrayList<>(repo.getSelectedIds());
            if (on && !sel.contains(PACK)) { sel.add(PACK); packAjoute = true; }
            else if (!on && packAjoute) { sel.remove(PACK); packAjoute = false; }
            else return;
            repo.setSelected(sel);
            mc.reloadResourcePacks();
        } catch (RuntimeException e) {
            System.out.println("[stele] ECHEC pack : " + e);
        }
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        ETAPES.add(new Etape(delai, mc -> { action.accept(mc); return true; }, nom));
    }

    private static void attendre(Predicate<Minecraft> cond, int max, String nom) {
        ETAPES.add(new Etape(1, mc -> {
            if (cond.test(mc)) { attente = 0; return true; }
            if (++attente > max) { attente = 0; System.out.println("[stele] ECHEC attente trop longue : " + nom); return true; }
            return false;
        }, "attente : " + nom));
    }

    private static void shot(String nom) {
        capture = nom;
        imagesCapture = images;
    }

    private static void prendre(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, capture + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println(String.format(Locale.ROOT, "[stele] capture %s (%d i/s)", capture, mc.getFps()));
        capture = null;
        imagesEtape = images;
    }

    private static void tick(Minecraft mc) {
        // monde solo seulement : sur un serveur, le banc ne lance aucune commande et ne touche pas aux packs
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        t++;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        // la souris saisie tournerait la caméra ; un écran (pause au changement de fenêtre) figerait le monde
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
        if (t > 60 && mc.gui.screen() != null && mc.gui.overlay() == null) mc.setScreenAndShow(null);
        if (marcheReste > 0) {
            marcheReste--;
            cmd(mc, String.format(Locale.ROOT, "execute as @e[tag=%s,limit=1] at @s run tp @s ~ ~ ~%.3f", marcheTag, PAS_MARCHE));
            // une téléportation n'anime pas les jambes côté client : la démarche est entretenue ici, après le tick
            // des entités (vitesse d'une marche, comme LivingEntity.updateWalkAnimation)
            if (mc.level.getEntity(marcheId) instanceof net.minecraft.world.entity.LivingEntity le) {
                le.walkAnimation.update(1f, 0.4f, 1f);
                le.setYBodyRot(0f);
                if (mc.level.getEntity(temoinId) instanceof net.minecraft.world.entity.LivingEntity t) t.walkAnimation.update(1f, 0.8f, 1f);
                if (marcheReste % 10 == 0) {
                    System.out.println(String.format(Locale.ROOT, "[stele] marche : vitesse %.2f, position %.1f, z %.2f (%s)",
                        le.walkAnimation.speed(), le.walkAnimation.position(), le.getZ(), le.getClass().getSimpleName()));
                }
            }
        }
        if (capture != null) {
            if (images - imagesCapture >= 3) prendre(mc);
            return;
        }
        if (t == 0) prochaine = 60 + ETAPES.get(0).delai();
        if (etape >= ETAPES.size() || t < prochaine) return;
        if (attente == 0 && images - imagesEtape < 2) return;
        Etape e = ETAPES.get(etape);
        if (attente == 0) System.out.println("[stele] " + e.nom());
        if (!e.action().test(mc)) return;
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + ETAPES.get(etape).delai();
    }
}
