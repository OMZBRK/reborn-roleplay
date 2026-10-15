package fr.reborn.hud.boutique;

import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Banc d'essai des boutiques-lieux, <b>inactif en production</b> ({@code REBORN_BOUTIQUE_DEBUG=1}, monde solo de dev,
 * jamais de serveur) : construit une petite échoppe de tailleur dans le ciel au-dessus du joueur (blocs relevés puis
 * rendus), le place sur l'estrade, rejoue les messages de {@code reborn:boutique} et capture chaque visuel dans
 * {@code run/screenshots/boutique_*.png}, puis ferme le jeu. Les envois du mod sont interceptés (aucun serveur).
 *
 * <p>Le pack de dev {@code run/resourcepacks/reborn_boutique_dev} (copie des modèles du pack Nexo : masque Hannya,
 * chapeau, rouleaux) est activé le temps du banc. <b>Le monde est remis en état</b> : blocs, position, mode de jeu,
 * heure, météo, packs, échelle, caméra.
 */
public final class BoutiqueDebug {

    private static int t = -1;
    private static final Map<BlockPos, BlockState> AVANT = new LinkedHashMap<>();
    private static BlockPos base;
    private static double px, py, pz;
    private static float pyaw, ppitch;
    private static String mode = "survival";
    private static long heure = -1;
    private static boolean pluie, packAjoute;
    private static int echelleAvant = -1;
    private static final String PACK = "file/reborn_boutique_dev";
    private static String apparenceAvant;

    private BoutiqueDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_BOUTIQUE_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(BoutiqueDebug::tick);
    }

    // ── l'apparence de Kenta (maquettes) et l'état envoyé par le « serveur » ──

    private static String apparence(String tenue) {
        SkinSpec s = new SkinSpec();
        s.hairId = "Cheveux_Ebouriffe";
        s.hairColor = 0xFF3A2618;
        s.skinColor = 0xFFD9A47A;
        s.eyeColor = s.eyeColorRight = 0xFF2E2014;
        s.browColor = 0xFF2E1E12;
        s.outfitId = tenue;
        return s.serialize();
    }

    private static String art(String id, String nom, String cat, String genre, long prix, String extra) {
        return "{\"id\":\"" + id + "\",\"nom\":\"" + nom + "\",\"cat\":\"" + cat + "\",\"genre\":\"" + genre + "\",\"prix\":" + prix
            + (extra == null ? "" : "," + extra) + "}";
    }

    private static String objet(String model, String slot) {
        return "\"item\":{\"mat\":\"minecraft:paper\",\"model\":\"reborn:" + model + "\",\"count\":1,\"slot\":\"" + slot + "\"}";
    }

    /** Le comptoir des vêtements : tenues + hauts, bas, manteaux (vides : aucun asset de ce genre aujourd'hui). */
    private static String vetements(String t, String toast, String apparence, boolean cam) {
        String[][] tenues = {
            {"tenueh46", "Tenue 46", "300"}, {"tenueh12", "Tenue 12", "250"}, {"Complet_Taijutsuka", "Taijutsuka", "480"},
            {"tenueh3", "Tenue 3", "400"}, {"Complet_Ceremonie", "Cérémonie", "1400"}, {"Complet_Ceremonie2", "Cérémonie 2", "1200"},
            {"tenueh7", "Tenue 7", "350"}, {"tenueh18", "Tenue 18", "300"}, {"tenueh29", "Tenue 29", "250"},
            {"tenueh31", "Tenue 31", "400"}, {"tenueh40", "Tenue 40", "200"}, {"tenueh44", "Tenue 44", "300"}};
        StringBuilder a = new StringBuilder();
        for (String[] x : tenues) {
            if (!a.isEmpty()) a.append(',');
            boolean possede = x[0].equals("tenueh46") || x[0].equals("tenueh3");
            a.append(art(x[0], x[1], "tenues", "tenue", Long.parseLong(x[2]), possede ? "\"possede\":true" : null));
        }
        return "{\"t\":\"" + t + "\",\"boutique\":{\"id\":\"tailleur_hosen\",\"nom\":\"Tailleur Hōsen\",\"genre\":\"tailleur\","
            + "\"sous\":\"Konoha · Au comptoir : Hōsen\"},\"comptoir\":\"Comptoir des vêtements\",\"bourse\":1240,"
            + "\"appearance\":\"" + apparence.replace("\\", "\\\\").replace("\"", "\\\"") + "\","
            + (cam ? camera(0f, 2.7) : "")
            + "\"categories\":[{\"id\":\"tenues\",\"nom\":\"Tenues\"},{\"id\":\"hauts\",\"nom\":\"Hauts\"},"
            + "{\"id\":\"bas\",\"nom\":\"Bas\"},{\"id\":\"manteaux\",\"nom\":\"Manteaux\"}],"
            + "\"articles\":[" + a + "]" + (toast == null ? "" : ",\"toast\":\"" + toast + "\"") + "}";
    }

    /** Le comptoir des masques et coiffes (cosmétiques 3D Nexo ; le masque ANBU demande la fonction). */
    private static String masques(String apparence) {
        String a = String.join(",",
            art("masque_hannya", "Masque Hannya", "masques", "objet", 450, objet("masque_hannya", "MASQUE")),
            art("masque_anbu", "Masque ANBU", "masques", "objet", 600, objet("masque_hannya", "MASQUE")
                + ",\"verrou\":\"Réservé à la fonction ANBU\",\"raison\":\"Ce n'est pas ta fonction\""),
            art("akatsuki_hat", "Chapeau de voyage", "coiffes", "objet", 350, objet("akatsuki_hat", "CHAPEAU")),
            art("rouleau_jaune", "Rouleau jaune", "accessoires", "objet", 120, objet("rouleau_jaune", "DOS")),
            art("rouleau_vert", "Rouleau vert", "accessoires", "objet", 120, objet("rouleau_vert", "DOS")),
            art("rouleau_violet", "Rouleau violet", "accessoires", "objet", 150, objet("rouleau_violet", "DOS")));
        return "{\"t\":\"ouvrir\",\"boutique\":{\"id\":\"tailleur_hosen\",\"nom\":\"Tailleur Hōsen\",\"genre\":\"tailleur\","
            + "\"sous\":\"Konoha · Libre-service\"},\"comptoir\":\"Masques et coiffes\",\"bourse\":1240,"
            + "\"appearance\":\"" + apparence.replace("\\", "\\\\").replace("\"", "\\\"") + "\"," + camera(0f, 2.7)
            + "\"categories\":[{\"id\":\"masques\",\"nom\":\"Masques\"},{\"id\":\"coiffes\",\"nom\":\"Coiffes\"},"
            + "{\"id\":\"accessoires\",\"nom\":\"Accessoires\"}],\"articles\":[" + a + "]}";
    }

    private static final String BAZAR = """
        {"t":"ouvrir","boutique":{"id":"bazar_konoha","nom":"Bazar de Konoha","genre":"bazar","sous":"Konoha · Libre-service"},
         "comptoir":"Comptoir","bourse":1240,"appearance":"",
         "categories":[{"id":"vivres","nom":"Vivres"},{"id":"cartes","nom":"Cartes"}],
         "articles":[{"id":"vivre:ration","nom":"Ration sèche ×2","cat":"vivres","genre":"vivre","prix":30},
                     {"id":"vivre:panier_ramen","nom":"Panier à ramen ×2","cat":"vivres","genre":"vivre","prix":40},
                     {"id":"vivre:panier_dango","nom":"Panier à dango ×2","cat":"vivres","genre":"vivre","prix":20},
                     {"id":"carte:konoha","nom":"Carte — Konoha","cat":"cartes","genre":"carte","prix":150}]}
        """;

    private static String camera(float yaw, double dist) {
        return "\"cam\":{\"x\":" + (base.getX() + 0.5) + ",\"y\":" + (base.getY() + 1) + ",\"z\":" + (base.getZ() + 0.5)
            + ",\"yaw\":" + yaw + ",\"dist\":" + dist + ",\"haut\":1.0},";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        String a46 = base == null ? "" : apparence("tenueh46");
        switch (t) {
            case 20 -> {
                releverMonde(mc);
                echelleAvant = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeGui();
                BoutiqueClient.debugEnvoi = s -> System.out.println("[BoutiqueDebug] envoi " + s.replace('\n', '|'));
                c.sendCommand("gamemode creative");
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                base = new BlockPos((int) Math.floor(px), 200, (int) Math.floor(pz));
                construire(mc);
                packDev(mc, true);
            }
            case 30 -> {
                vol(mc, false);
                c.sendCommand("tp @s " + (base.getX() + 0.5) + " " + (base.getY() + 1) + " " + (base.getZ() + 0.5) + " 0 0");
                apparenceAvant = null;
                RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(apparence("tenueh46")));
            }
            // 01 — le comptoir des vêtements, catégorie Hauts (vide aujourd'hui : aucun haut au catalogue)
            case 120 -> BoutiqueClient.recevoir(vetements("ouvrir", null, a46, true));
            case 122 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancCategorie("hauts"); }
            case 140 -> shot(mc, "boutique_01_comptoir_hauts");
            // 02 — Tenues : essai de « Tenue 12 » sur soi
            case 142 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancChoisir("tenueh12", true); }
            case 156 -> shot(mc, "boutique_02_essai");
            // 03 — de dos (le personnage pivote pour de vrai)
            case 158 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancTourner(180f); }
            case 170 -> shot(mc, "boutique_03_rotation_dos");
            // 04 — molette : cadrage visage (de trois quarts)
            case 172 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) { b.bancTourner(-25f); b.bancCadrer(2); } }
            case 190 -> shot(mc, "boutique_04_zoom_visage");
            // 05 — l'achat : la confirmation
            case 192 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) { b.bancTourner(0f); b.bancCadrer(0); b.bancConfirmer("tenueh12"); } }
            case 210 -> shot(mc, "boutique_05_achat");
            // 05b — après l'achat : « portée », la notification du serveur
            case 212 -> {
                if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancConfirmer(null);
                String a12 = apparence("tenueh12");
                BoutiqueClient.recevoir(vetements("etat", "§aTenue 12 achetée et portée.", a12, false)
                    .replace("\"id\":\"tenueh12\",\"nom\":\"Tenue 12\",\"cat\":\"tenues\",\"genre\":\"tenue\",\"prix\":250",
                        "\"id\":\"tenueh12\",\"nom\":\"Tenue 12\",\"cat\":\"tenues\",\"genre\":\"tenue\",\"prix\":250,\"possede\":true")
                    .replace("\"bourse\":1240", "\"bourse\":990"));
                RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(a12));
            }
            case 222 -> shot(mc, "boutique_06_achete");
            // 07 — le comptoir des masques : essai du masque Hannya (objet 3D sur la tête)
            case 224 -> {
                mc.setScreenAndShow(null);
                RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(a46));
            }
            case 230 -> BoutiqueClient.recevoir(masques(a46));
            case 236 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) { b.bancChoisir("masque_hannya", true); b.bancCadrer(1); } }
            case 256 -> shot(mc, "boutique_07_comptoir_masques");
            // 08 — condition non remplie : masque ANBU grisé, raison au survol
            case 258 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancChoisir("masque_anbu", false); }
            case 262, 266 -> survolerCase(mc, 1, 0);
            case 270 -> shot(mc, "boutique_08_condition_grisee");
            // 09 — le bazar (liste simple, sans caméra)
            case 272 -> { mc.setScreenAndShow(null); curseur(mc, 10, 10); }
            case 276 -> BoutiqueClient.recevoir(BAZAR.trim());
            case 280 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancChoisir("vivre:ration", false); }
            case 290 -> shot(mc, "boutique_09_bazar");
            // 10 — repli : un mur coupe la caméra → le personnage dessiné sur un voile
            case 292 -> mc.setScreenAndShow(null);
            case 298 -> BoutiqueClient.recevoir(vetements("ouvrir", null, a46, false).replace("\"categories\"", camera(180f, 3.6) + "\"categories\""));
            case 300 -> { if (mc.gui.screen() instanceof BoutiqueScreen b) b.bancChoisir("Complet_Taijutsuka", true); }
            case 312 -> shot(mc, "boutique_10_repli_voile");
            // 11 — Échap : la carte BOUTIQUE ; 12 — l'écran Boutique vide
            case 314 -> mc.setScreenAndShow(null);
            case 318 -> mc.setScreenAndShow(new net.minecraft.client.gui.screens.PauseScreen(true));
            case 340 -> shot(mc, "boutique_11_echap_carte");
            case 342 -> mc.setScreenAndShow(new BoutiqueVideScreen(mc.gui.screen()));
            case 354 -> shot(mc, "boutique_12_echap_boutique_vide");
            case 356 -> { mc.setScreenAndShow(null); remettreMonde(mc); }
            case 400 -> c.sendCommand("gamemode " + mode);
            case 404 -> mc.stop();
            default -> { }
        }
    }

    /* ================================================================ l'échoppe dans le ciel */

    /**
     * Une petite échoppe de tailleur (x −7..7, z −3..4 autour de l'estrade, sol en y, plafond en y + 7) : mur du fond
     * en planches et piliers, rayonnages de tissus (laines), porte au centre, lanternes, comptoir à gauche. Chaque bloc
     * remplacé est relevé, puis rendu à la fin.
     */
    private static void construire(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var uuid = mc.player.getUUID();
        BlockPos b = base;
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var lvl = (net.minecraft.server.level.ServerLevel) sp.level();
            net.minecraft.world.item.DyeColor[] teintes = {net.minecraft.world.item.DyeColor.BLACK, net.minecraft.world.item.DyeColor.BROWN,
                net.minecraft.world.item.DyeColor.RED, net.minecraft.world.item.DyeColor.PURPLE, net.minecraft.world.item.DyeColor.BLUE,
                net.minecraft.world.item.DyeColor.YELLOW, net.minecraft.world.item.DyeColor.LIME, net.minecraft.world.item.DyeColor.WHITE,
                net.minecraft.world.item.DyeColor.CYAN, net.minecraft.world.item.DyeColor.LIGHT_GRAY, net.minecraft.world.item.DyeColor.PINK,
                net.minecraft.world.item.DyeColor.ORANGE};
            Block[] laines = new Block[teintes.length];
            for (int i = 0; i < teintes.length; i++) laines[i] = Blocks.WOOL.pick(teintes[i]);
            for (int x = -7; x <= 7; x++) {
                for (int z = -3; z <= 4; z++) {
                    for (int y = 0; y <= 7; y++) {
                        BlockState s = Blocks.AIR.defaultBlockState();
                        if (y == 0) s = (Math.abs(x) <= 1 && z >= -1 && z <= 1 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.DARK_OAK_PLANKS).defaultBlockState();
                        else if (y == 7) s = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                        else if (Math.abs(x) == 7) s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                        else if (z == -3) {
                            if (Math.abs(x) == 3) s = Blocks.DARK_OAK_LOG.defaultBlockState();
                            else if (Math.abs(x) <= 1) s = (Math.abs(x) == 1 || y >= 5 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_PLANKS).defaultBlockState();
                            else if (y == 2 || y == 4) s = laines[Math.floorMod(x * 3 + y * 5, laines.length)].defaultBlockState();
                            else s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                        } else if (z == -2 && (y == 1 || y == 3) && Math.abs(x) >= 4 && Math.abs(x) <= 6) {
                            s = Blocks.SPRUCE_SLAB.defaultBlockState();
                        } else if (y == 1 && z == 1 && x >= -6 && x <= -4) {
                            s = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
                        } else if (y == 6 && ((Math.abs(x) == 4 && z == -1) || (x == 0 && z == 2))) {
                            s = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
                        }
                        BlockPos p = b.offset(x, y, z);
                        AVANT.putIfAbsent(p, lvl.getBlockState(p));
                        lvl.setBlock(p, s, Block.UPDATE_CLIENTS);
                    }
                }
            }
        });
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
            List<BlockPos> lanternes = new ArrayList<>();
            for (var e : copie.entrySet()) {   // d'abord ce qui pend, puis le reste
                if (lvl.getBlockState(e.getKey()).is(Blocks.LANTERN)) lanternes.add(e.getKey());
            }
            for (BlockPos p : lanternes) lvl.setBlock(p, copie.get(p), Block.UPDATE_CLIENTS);
            for (var e : copie.entrySet()) lvl.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        });
    }

    /* ================================================================ outils */

    /** Pose la souris au centre de la case (colonne, ligne) de la grille du catalogue (même géométrie que l'écran). */
    private static void survolerCase(Minecraft mc, int col, int ligne) {
        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        int pw = 230, ph = Math.min(382, H - 108);
        int px = W - pw - 18, py = Math.max(50, (H - ph) / 2 - 22);
        int gx0 = px + 12 + 1, gy = py + 48;
        curseur(mc, gx0 + col * 71 + 33, gy + ligne * 87 + 41);
    }

    private static void curseur(Minecraft mc, float guiX, float guiY) {
        double k = mc.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), guiX * k, guiY * k);
        try {
            var fx = mc.mouseHandler.getClass().getDeclaredField("xpos");
            var fy = mc.mouseHandler.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, guiX * k);
            fy.setDouble(mc.mouseHandler, guiY * k);
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void packDev(Minecraft mc, boolean on) {
        try {
            var repo = mc.getResourcePackRepository();
            repo.reload();
            if (!repo.isAvailable(PACK)) return;
            Collection<String> sel = new ArrayList<>(repo.getSelectedIds());
            if (on && !sel.contains(PACK)) { sel.add(PACK); packAjoute = true; }
            else if (!on && packAjoute) { sel.remove(PACK); packAjoute = false; }
            else return;
            repo.setSelected(sel);
            mc.reloadResourcePacks();
        } catch (RuntimeException ignored) {
            // pas de pack de dev : les objets s'affichent sans leur modèle
        }
    }

    private static void releverMonde(Minecraft mc) {
        px = mc.player.getX();
        py = mc.player.getY();
        pz = mc.player.getZ();
        pyaw = mc.player.getYRot();
        ppitch = mc.player.getXRot();
        if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
        var srv = mc.getSingleplayerServer();
        if (srv != null) {
            heure = srv.overworld().getOverworldClockTime();
            pluie = srv.overworld().isRaining();
        }
    }

    private static void remettreMonde(Minecraft mc) {
        var c = mc.player.connection;
        detruire(mc);
        c.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", px, py, pz, pyaw, ppitch));
        if (heure >= 0) c.sendCommand("time set " + heure);
        c.sendCommand(pluie ? "weather rain" : "weather clear");
        BoutiqueClient.debugEnvoi = null;
        BoutiqueClient.clear();
        RebornSkins.clear(mc.player.getUUID());
        packDev(mc, false);
        if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
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
