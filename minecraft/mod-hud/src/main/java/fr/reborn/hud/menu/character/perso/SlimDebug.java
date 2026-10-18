package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.cosmetic.CosmeticTransform;
import fr.reborn.hud.cosmetic.RepositionMode;
import fr.reborn.hud.menu.character.scene.SceneActors;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Banc du personnage masculin en carrure fine (18/10), <b>inactif en production</b> ({@code REBORN_SLIM_DEBUG=<étiquette>},
 * monde solo de dev, jamais de serveur). Une dalle dans le ciel (blocs relevés puis rendus) ; le joueur porte tour à
 * tour un perso Homme composé — sans tenue (bras de peau), puis quelques tenues — et un mannequin « autre joueur » à côté
 * reçoit la même apparence. Captures (HUD masqué, 1920×1080) : face, profil, dos, gros plan des bras, duo ; main à la
 * première personne ; Test de la Feuille (confirmation et canalisation : le bout de bras au skin) ; création (Sexe,
 * Apparence). Fichiers {@code run/screenshots/slim_<étiquette>_*.png}, puis le monde est remis et le jeu se ferme.
 */
public final class SlimDebug {

    private static String etiq;
    private static int t = -1, etape, prochain;
    private static final Map<BlockPos, BlockState> AVANT = new LinkedHashMap<>();
    private static final Map<Integer, net.minecraft.world.item.ItemStack> SAC = new LinkedHashMap<>();
    private static BlockPos base;
    private static double px, py, pz;
    private static float pyaw, ppitch;
    private static String mode = "survival";
    private static int echelleAvant = -1;
    private static boolean pauseAvant;
    private static float regard;
    private static ClientMannequin autre;

    private record Pas(int attente, Runnable action) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static final String[] TENUES = {"", "tenueh1", "tenueh25", "Complet_Ceremonie2", "Complet_Uchiha"};

    private SlimDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_SLIM_DEBUG");
        if (v == null || v.isBlank()) return;
        etiq = v.trim();
        ClientTickEvents.END_CLIENT_TICK.register(SlimDebug::tick);
    }

    private static SkinSpec homme(String tenue) {
        SkinSpec s = new SkinSpec();
        s.female = false;
        s.hairId = "Cheveux_Ebouriffe";
        s.hairColor = 0xFF3A2618;
        s.skinColor = SkinSpec.skinRamp(4 / 11f);
        s.eyeColor = s.eyeColorRight = 0xFF2E2014;
        s.outfitId = tenue;
        return s;
    }

    private static String nomTenue(String id) { return id.isEmpty() ? "sans_tenue" : id; }

    private static void pas(int attente, Runnable action) { PAS.add(new Pas(attente, action)); }

    private static void hud(Minecraft mc, boolean cache) {
        ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(cache);
    }

    private static void porter(Minecraft mc, String tenue) {
        SkinSpec s = homme(tenue);
        RebornSkins.applySpec(mc.player.getUUID(), s);
        Vec3 pied = new Vec3(base.getX() + 0.5 + 1.4, base.getY() + 1, base.getZ() + 0.5);
        if (autre == null) autre = SceneActors.spawn(mc, 990_417, pied, 0f, s, null, 0f);
        else SceneActors.reskin(autre, s);
    }

    private static void construireSequence(Minecraft mc) {
        PAS.clear();
        var c = mc.player.connection;
        pas(0, () -> {
            releverMonde(mc);
            pauseAvant = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            echelleAvant = mc.options.guiScale().get();
            if (mc.getWindow().getScreenWidth() != 1920 || mc.getWindow().getScreenHeight() != 1080) {
                mc.getWindow().setWindowed(1920, 1080);
            }
            mc.options.guiScale().set(2);
            mc.resizeGui();
            c.sendCommand("gamerule send_command_feedback false");
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            base = new BlockPos((int) Math.floor(px), 250, (int) Math.floor(pz));
            construire(mc);
            inventaire(mc, true);
        });
        pas(10, () -> {
            vol(mc);
            c.sendCommand(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 0", base.getX(), base.getY() + 1, base.getZ()));
        });
        // 1) Tenues : face, profil, dos, gros plan des bras, duo avec l'« autre joueur »
        pas(20, () -> {
            mc.setScreenAndShow(null);
            porter(mc, TENUES[0]);
            RepositionMode.INSTANCE.begin(mc, "slim_banc", "Banc", CosmeticTransform.Anchor.HEAD);
            RepositionMode.INSTANCE.zoom(2);
            hud(mc, true);
        });
        // asset live peint en bras de 4 px (REBORN_CREATOR_ASSETS=<dossier> avec outfit/<id>.png + catalog.json) :
        // rejoué comme la diffusion reborn:creatorpack, le compositeur doit le convertir en bras fins
        List<String> tenues = new ArrayList<>(List.of(TENUES));
        String live = System.getenv("REBORN_CREATOR_ASSETS");
        if (live != null && !live.isBlank()) tenues.addAll(injecterLive(java.nio.file.Path.of(live.trim())));
        for (String tn : tenues) {
            String n = nomTenue(tn);
            pas(4, () -> porter(mc, tn));
            pas(12, () -> shot(mc, "tenue_" + n + "_1_face"));
            pas(2, () -> RepositionMode.INSTANCE.orbit(180, 0));
            pas(10, () -> shot(mc, "tenue_" + n + "_2_profil"));
            pas(2, () -> RepositionMode.INSTANCE.orbit(180, 0));
            pas(10, () -> shot(mc, "tenue_" + n + "_3_dos"));
            pas(2, () -> { RepositionMode.INSTANCE.orbit(-360 + 70, 0); RepositionMode.INSTANCE.zoom(5); });
            pas(10, () -> shot(mc, "tenue_" + n + "_4_bras_trois_quarts"));
            pas(2, () -> { RepositionMode.INSTANCE.orbit(-70 - 40, 0); RepositionMode.INSTANCE.zoom(-8); });
            pas(10, () -> shot(mc, "tenue_" + n + "_5_duo_autre_joueur"));
            pas(2, () -> { RepositionMode.INSTANCE.orbit(40, 0); RepositionMode.INSTANCE.zoom(3); });
        }
        // 2) Main à la première personne (bras droit au skin composé), main vide
        pas(2, () -> {
            RepositionMode.INSTANCE.end(mc);
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            hud(mc, false);                                  // HUD masqué = main masquée (comme F1)
            regard = 20f;
        });
        for (String tn : new String[]{"", "tenueh1", "Complet_Ceremonie2"}) {
            pas(4, () -> porter(mc, tn));
            pas(12, () -> shot(mc, "main_1re_personne_" + nomTenue(tn)));
        }
        // 3) Test de la Feuille : le bout de bras du gros plan lit le skin (carrure comprise)
        pas(2, () -> { regard = 0f; porter(mc, "tenueh1"); });
        pas(6, () -> mc.setScreenAndShow(fr.reborn.hud.menu.tirage.TirageScreen.confirm("Uchiha")));
        pas(20, () -> shot(mc, "feuille_1_confirmer"));
        pas(2, () -> mc.setScreenAndShow(null));
        pas(6, () -> mc.setScreenAndShow(fr.reborn.hud.menu.tirage.TirageScreen.banc("KATON", null, 1)));
        pas(40, () -> shot(mc, "feuille_2_canalisation"));
        pas(2, () -> { mc.setScreenAndShow(null); porter(mc, ""); });
        pas(6, () -> mc.setScreenAndShow(fr.reborn.hud.menu.tirage.TirageScreen.confirm("Uchiha")));
        pas(20, () -> shot(mc, "feuille_3_confirmer_sans_tenue"));
        // 4) Création : étapes Sexe et Apparence (Homme)
        pas(2, () -> {
            mc.setScreenAndShow(null);
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            PersoSons.voixCoupee = true;
            PersoCreationScreen sc = new PersoCreationScreen();
            mc.setScreenAndShow(sc);
            sc.debugEtat(0, 2, 0, "Ren Hyūga", 16, 172, 4, 0, 1, 2, 0);
            sc.debugEtape(2);
        });
        pas(40, () -> shot(mc, "creation_1_sexe"));
        pas(2, () -> { if (mc.gui.screen() instanceof PersoCreationScreen sc) sc.debugEtape(6); });
        pas(40, () -> shot(mc, "creation_2_apparence"));
        pas(2, () -> {
            if (mc.gui.screen() instanceof PersoCreationScreen sc) {
                sc.debugEtat(0, 2, 0, "Ren Hyūga", 16, 172, 4, 0, 1, 2, 3);
                sc.debugEtape(6);
            }
        });
        pas(40, () -> shot(mc, "creation_3_apparence_tenue"));
        pas(2, () -> { if (mc.gui.screen() instanceof PersoCreationScreen sc) sc.debugEtape(7); });
        pas(40, () -> shot(mc, "creation_4_recapitulatif"));
        // remise en état
        pas(2, () -> {
            mc.setScreenAndShow(null);
            PersoSons.voixCoupee = !PersoSons.VOIX;
            hud(mc, false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (autre != null) { SceneActors.remove(mc, autre); autre = null; }
            remettreMonde(mc);
        });
        pas(26, () -> c.sendCommand("gamemode " + mode));
        pas(4, mc::stop);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        if (t >= 20 && mc.gui.overlay() != null) return;
        t++;
        if (t == 20) { construireSequence(mc); etape = 0; prochain = t; }
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t > 32 && etape < PAS.size()) {
            mc.player.setYRot(0f);
            mc.player.setYHeadRot(0f);
            mc.player.setYBodyRot(0f);
            mc.player.setXRot(regard);
        }
        if (t < 20 || etape >= PAS.size() || t < prochain) return;
        Pas p = PAS.get(etape++);
        p.action().run();
        prochain = t + (etape < PAS.size() ? Math.max(1, PAS.get(etape).attente()) : 1);
    }

    /** Rejoue {@code reborn:creatorpack} (même corps et point d'entrée que ShinobiCore) pour les tenues du dossier. */
    private static List<String> injecterLive(java.nio.file.Path dir) {
        List<String> ids = new ArrayList<>();
        try {
            var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(dir.resolve("catalog.json")))
                .getAsJsonObject();
            for (var el : root.getAsJsonArray("outfit")) {
                var o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                byte[] pb = java.nio.file.Files.readAllBytes(dir.resolve("outfit").resolve(id + ".png"));
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                try (java.io.DataOutputStream out = new java.io.DataOutputStream(b)) {
                    out.writeUTF("outfit");
                    out.writeUTF(id);
                    out.writeUTF(o.toString());
                    out.writeInt(pb.length);
                    out.write(pb);
                    out.writeInt(0);
                }
                fr.reborn.hud.skin.CreatorAssets.INSTANCE.registerChunk("outfit/" + id, 0, 1, b.toByteArray());
                ids.add(id);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("reborn-slim-banc").warn("assets live illisibles : {}", e.toString());
        }
        return ids;
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
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    for (int y = 0; y <= 3; y++) {
                        BlockState s = y == 0 ? (Math.abs(x) == 6 || Math.abs(z) == 6 ? Blocks.DARK_OAK_PLANKS
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
        RebornSkins.clear(mc.player.getUUID());
        inventaire(mc, false);
        if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
        mc.options.pauseOnLostFocus = pauseAvant;
    }

    private static void vol(Minecraft mc) {
        var ab = mc.player.getAbilities();
        ab.flying = false;
        mc.player.onUpdateAbilities();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "slim_" + etiq + "_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
