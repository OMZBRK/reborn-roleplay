package fr.reborn.hud.cosmetic;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.menu.cosmetic.TeintureScreen;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.InventoryScreen;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Banc d'essai des cosmétiques de tête (v3 du 18/10), <b>inactif en production</b> ({@code REBORN_TEINTURE_DEBUG=1},
 * monde solo de dev, jamais de serveur) : une dalle dans le ciel au-dessus du joueur (blocs relevés puis rendus), le
 * joueur porte tour à tour le bandeau de l'utilisateur ({@code nexo:bandeau_ninja}, sans teinte puis teint), le masque
 * Hannya, le chapeau d'Akatsuki et l'oreillette radio (sacoche simulée ; pack de dev
 * {@code run/resourcepacks/reborn_boutique_dev} : copies des modèles du pack Nexo). <b>Aucun placement n'est posé par le
 * banc</b> : c'est le placement par défaut du rendu cosmétique qui est vérifié (casque vanilla pour la tête, oreille
 * pour l'oreillette). Un mannequin « autre joueur » reçoit la diffusion {@code reborn:cosmetics} sans transform (même
 * défaut), puis une fois avec un transform diffusé (un repositionnement reste prioritaire). Caméra en orbite du mode
 * Repositionner, HUD masqué : face, profil, dos, tête tournée de 35°, puis « duo » avec le mannequin (le mannequin garde la tête droite : le serveur intégré ne la tourne pas).
 * La séquence attend la fin de tout rechargement des ressources avant d'avancer. Captures
 * {@code run/screenshots/tete_*.png}, puis remet le monde et ferme le jeu.
 */
public final class TeintureDebug {

    private static int t = -1;
    private static int etape, prochain;
    private static final Map<BlockPos, BlockState> AVANT = new LinkedHashMap<>();
    private static BlockPos base;
    private static double px, py, pz;
    private static float pyaw, ppitch;
    private static String mode = "survival";
    private static boolean packAjoute, pauseAvant;
    private static int echelleAvant = -1;
    private static RebornCamera.Mode camAvant;
    private static CameraType vueAvant;
    private static final String PACK = "file/reborn_boutique_dev";
    private static final String BANDEAU = "nexo:bandeau_ninja";
    /** Inventaire du joueur relevé au début (la barre affiche des objets d'autres bancs), rendu à la fin. */
    private static final Map<Integer, net.minecraft.world.item.ItemStack> SAC = new LinkedHashMap<>();
    /** Lacet de la tête par rapport au corps (le cosmétique doit suivre la tête), joueur et mannequin. */
    private static float tete;

    private record Pas(int attente, Runnable action) {}

    private static final List<Pas> PAS = new ArrayList<>();

    private TeintureDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_TEINTURE_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(TeintureDebug::tick);
    }

    private static SlotItem objet(String modele, String nom, int dye) {
        SlotItem s = new SlotItem("minecraft:paper", modele, 1, nom, 0.1, "Cosmétique ninja", "COMMUN", null, null);
        s.teintable = BANDEAU.equals(modele);
        s.dye = dye;
        return s;
    }

    /** Sacoche simulée : un seul cosmétique porté. */
    private static void porter(CosmeticSlot slot, String modele, String nom, int dye) {
        Map<CosmeticSlot, SlotItem> eq = new EnumMap<>(CosmeticSlot.class);
        eq.put(slot, objet(modele, nom, dye));
        SlotItem[] bag = new SlotItem[9];
        bag[0] = new SlotItem("minecraft:bread", null, 3, "Ration", 0.4, null, "COMMUN", null, null);
        InventoryData.setLocal(new InventoryData.Snapshot("SACOCHE", 9, 9, 20.0, 3.2,
            new SlotItem("minecraft:leather", null, 1, "Sacoche", 0.6), bag, eq));
    }

    private static String apparence() {
        SkinSpec s = new SkinSpec();
        s.hairId = "Cheveux_Ebouriffe";
        s.hairColor = 0xFF3A2618;
        s.skinColor = 0xFFD9A47A;
        s.eyeColor = s.eyeColorRight = 0xFF2E2014;
        s.browColor = 0xFF2E1E12;
        s.outfitId = "tenueh46";
        return s.serialize();
    }

    private static void pas(int attente, Runnable action) { PAS.add(new Pas(attente, action)); }

    private static void hud(Minecraft mc, boolean cache) {
        ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(cache);
    }

    /** Un cosmétique : face, profil, dos, tête tournée (gros plan), puis duo avec le mannequin, tête tournée. */
    private static void serie(Minecraft mc, String nom, CosmeticSlot slot, String modele, String libelle, int dye,
                              String dyeAutre) {
        pas(4, () -> { porter(slot, modele, libelle, dye); diffuser(mc, slot, modele, dyeAutre, null); });
        pas(12, () -> shot(mc, "tete_" + nom + "_1_face"));
        pas(2, () -> RepositionMode.INSTANCE.orbit(180, 0));
        pas(10, () -> shot(mc, "tete_" + nom + "_2_profil"));
        pas(2, () -> RepositionMode.INSTANCE.orbit(180, 0));
        pas(10, () -> shot(mc, "tete_" + nom + "_3_dos"));
        pas(2, () -> { RepositionMode.INSTANCE.orbit(-360 + 60, 0); tete = 35f; teteMannequins(mc, 35f); });
        pas(10, () -> shot(mc, "tete_" + nom + "_4_tete_tournee"));
        pas(2, () -> { RepositionMode.INSTANCE.orbit(-60, 0); RepositionMode.INSTANCE.zoom(-5); });
        pas(10, () -> shot(mc, "tete_" + nom + "_5_duo_mannequin_tete_tournee"));
        pas(2, () -> { tete = 0f; teteMannequins(mc, 0f); RepositionMode.INSTANCE.orbit(140, 0); });
        pas(10, () -> shot(mc, "tete_" + nom + "_6_duo_trois_quarts"));
        pas(2, () -> { RepositionMode.INSTANCE.orbit(-140, 0); RepositionMode.INSTANCE.zoom(5); });
    }

    private static void construireSequence(Minecraft mc) {
        PAS.clear();
        var c = mc.player.connection;
        pas(0, () -> {
            releverMonde(mc);
            pauseAvant = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            echelleAvant = mc.options.guiScale().get();
            mc.options.guiScale().set(2);
            mc.resizeGui();
            TeintureScreen.debugEnvoi = s -> System.out.println("[TeintureDebug] envoi " + s);
            c.sendCommand("gamerule sendCommandFeedback false");
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            base = new BlockPos((int) Math.floor(px), 250, (int) Math.floor(pz));
            construire(mc);
            inventaire(mc, true);
            packDev(mc, true);
        });
        pas(10, () -> {
            vol(mc, false);
            c.sendCommand(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 0", base.getX(), base.getY() + 1, base.getZ()));
            RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(apparence()));
            porter(CosmeticSlot.BANDEAU, BANDEAU, "Bandeau de Ninja", -1);
            c.sendCommand(String.format(Locale.ROOT,
                "summon minecraft:mannequin %.1f %d %d.5 {Rotation:[0f,0f],NoGravity:1b,Tags:[\"teinture_test\"]}",
                base.getX() + 1.6, base.getY() + 1, base.getZ()));
        });
        // 01 — sacoche, onglet Cosmétique, clic droit sur le bandeau porté : « Teinture » (ressources rechargées)
        pas(30, () -> { InventoryScreen.debugOnglet(false); mc.setScreenAndShow(new InventoryScreen()); });
        pas(4, () -> { if (mc.gui.screen() instanceof InventoryScreen s) s.bancPanneauCosmetique(CosmeticSlot.BANDEAU); curseur(mc, 344, 337); });
        pas(14, () -> shot(mc, "tete_01_sacoche_clic_droit_teinture"));
        // 02 — l'écran Teinture : le bandeau sur la tête sans aucun réglage (avant le 18/10 : sur la poitrine)
        pas(2, () -> mc.setScreenAndShow(new TeintureScreen(CosmeticSlot.BANDEAU, InventoryData.get().equipped().get(CosmeticSlot.BANDEAU))));
        pas(14, () -> shot(mc, "tete_02_ecran_teinture_defaut"));
        pas(2, () -> { if (mc.gui.screen() instanceof TeintureScreen s) s.bancChoisir(0x9E2A2E); });
        pas(10, () -> shot(mc, "tete_03_ecran_teinture_rouge"));
        // Gros plans sans interface : caméra en orbite (mode Repositionner), HUD masqué
        pas(2, () -> {
            mc.setScreenAndShow(null);
            camAvant = RebornCamera.INSTANCE.mode();
            vueAvant = mc.options.getCameraType();
            RepositionMode.INSTANCE.begin(mc, BANDEAU, "Bandeau", CosmeticTransform.Anchor.HEAD);
            RepositionMode.INSTANCE.zoom(4);
            hud(mc, true);
        });
        serie(mc, "bandeau_sans_teinte", CosmeticSlot.BANDEAU, BANDEAU, "Bandeau de Ninja", -1, null);
        serie(mc, "bandeau_rouge", CosmeticSlot.BANDEAU, BANDEAU, "Bandeau de Ninja", 0x9E2A2E, "3E6E46");
        serie(mc, "masque_hannya", CosmeticSlot.MASQUE, "reborn:masque_hannya", "Masque Hannya", -1, null);
        serie(mc, "akatsuki_hat", CosmeticSlot.CHAPEAU, "reborn:akatsuki_hat", "Chapeau d'Akatsuki", -1, null);
        serie(mc, "oreillette", CosmeticSlot.BOUCLE, "reborn:oreillette", "Oreillette", -1, null);
        // Contrôle : un repositionnement diffusé (bandeau abaissé sur les yeux) reste prioritaire chez les autres
        pas(2, () -> {
            porter(CosmeticSlot.BANDEAU, BANDEAU, "Bandeau de Ninja", -1);
            diffuser(mc, CosmeticSlot.BANDEAU, BANDEAU, "9E2A2E", "HEAD,0.0,0.0,0.0,180.0,180.0,0.0,0.625");
            RepositionMode.INSTANCE.zoom(-5);
        });
        pas(12, () -> shot(mc, "tete_90_controle_transform_diffuse_prioritaire"));
        pas(2, () -> {
            RepositionMode.INSTANCE.end(mc);
            hud(mc, false);
            mc.options.setCameraType(vueAvant != null ? vueAvant : CameraType.FIRST_PERSON);
            if (camAvant != null) RebornCamera.INSTANCE.setMode(camAvant);
            c.sendCommand("kill @e[tag=teinture_test]");
            remettreMonde(mc);
        });
        pas(26, () -> c.sendCommand("gamemode " + mode));
        pas(4, mc::stop);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        if (t >= 20 && mc.gui.overlay() != null) return;           // rechargement des ressources : on attend
        t++;
        if (t == 20) { construireSequence(mc); etape = 0; prochain = t; }
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t > 30 && etape < PAS.size()) {                          // le joueur regarde le sud, immobile
            mc.player.setYRot(0f);
            mc.player.setYHeadRot(tete);
            mc.player.setYBodyRot(0f);
            mc.player.setXRot(0f);
            for (Mannequin m : mc.level.getEntitiesOfClass(Mannequin.class, mc.player.getBoundingBox().inflate(8))) {
                m.setYHeadRot(tete);
            }
        }
        if (t < 20 || etape >= PAS.size() || t < prochain) return;
        Pas p = PAS.get(etape++);
        p.action().run();
        prochain = t + (etape < PAS.size() ? Math.max(1, PAS.get(etape).attente()) : 1);
    }

    /** Tête des mannequins tournée côté serveur intégré (le client suit la rotation diffusée). */
    private static void teteMannequins(Minecraft mc, float lacet) {
        var srv = mc.getSingleplayerServer();
        if (srv == null || base == null) return;
        var uuid = mc.player.getUUID();
        BlockPos b = base;
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            for (Mannequin m : sp.level().getEntitiesOfClass(Mannequin.class, new net.minecraft.world.phys.AABB(b).inflate(6))) {
                m.setYHeadRot(lacet);
            }
        });
    }

    /** Diffusion reborn:cosmetics simulée vers les mannequins : teinte ({@code RRGGBB}, null = sans) et transform (null = défaut). */
    private static void diffuser(Minecraft mc, CosmeticSlot slot, String modele, String dye, String transform) {
        for (Mannequin m : mc.level.getEntitiesOfClass(Mannequin.class, mc.player.getBoundingBox().inflate(8))) {
            RemoteCosmetics.apply(m.getUUID() + "\n{\"" + slot.name() + "\":{\"mat\":\"minecraft:paper\",\"model\":\"" + modele + "\""
                + (transform != null ? ",\"t\":\"" + transform + "\"" : "")
                + (dye != null ? ",\"dye\":\"" + dye + "\"" : "") + "}}");
        }
    }

    /* ================================================================ la dalle dans le ciel */

    private static void construire(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var uuid = mc.player.getUUID();
        BlockPos b = base;
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var lvl = (net.minecraft.server.level.ServerLevel) sp.level();
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    for (int y = 0; y <= 3; y++) {
                        BlockState s = y == 0 ? (Math.abs(x) == 5 || Math.abs(z) == 5 ? Blocks.DARK_OAK_PLANKS
                            : Blocks.SPRUCE_PLANKS).defaultBlockState() : Blocks.AIR.defaultBlockState();
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
            for (var e : copie.entrySet()) lvl.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        });
    }

    /** Relève (et vide) ou rend l'inventaire du joueur, côté serveur intégré. */
    private static void inventaire(Minecraft mc, boolean relever) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var uuid = mc.player.getUUID();
        srv.execute(() -> {
            var sp = srv.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            var inv = sp.getInventory();
            if (relever) {
                SAC.clear();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (!inv.getItem(i).isEmpty()) SAC.put(i, inv.getItem(i).copy());
                    inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            } else {
                for (var e : SAC.entrySet()) inv.setItem(e.getKey(), e.getValue());
                SAC.clear();
            }
        });
    }

    /* ================================================================ outils */

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
            // pas de pack de dev : le bandeau s'affiche sans son modèle
        }
    }

    private static void releverMonde(Minecraft mc) {
        px = mc.player.getX();
        py = mc.player.getY();
        pz = mc.player.getZ();
        pyaw = mc.player.getYRot();
        ppitch = mc.player.getXRot();
        if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
    }

    private static void remettreMonde(Minecraft mc) {
        var c = mc.player.connection;
        detruire(mc);
        c.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", px, py, pz, pyaw, ppitch));
        TeintureScreen.debugEnvoi = null;
        InventoryData.clear();
        RemoteCosmetics.clearAll();
        RebornSkins.clear(mc.player.getUUID());
        inventaire(mc, false);
        packDev(mc, false);
        if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
        mc.options.pauseOnLostFocus = pauseAvant;
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
