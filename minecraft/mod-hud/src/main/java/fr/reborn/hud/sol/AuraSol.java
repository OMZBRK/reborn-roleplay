package fr.reborn.hud.sol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Lueur de rareté</b> des objets au sol (v3, 15/10 : « comme avant, mais plus pixel art, moins détaillé, une petite
 * lueur subtile ») : un petit <b>halo pixel art</b> plaqué au sol sous l'objet, à la couleur de sa rareté —
 * texture 11×11 ({@code gui/inventaire/lueur.png}, 1 texel = 1/16 de bloc comme les objets, disque en escalier à
 * 3 tons, rendu NEAREST), alpha faible qui respire lentement ; à partir de Rare, 1-2 pixels scintillent de temps en
 * temps (images 1-3 de la texture). Commun : rien ; l'objet <b>visé</b> (touche Ramasser) : halo un peu plus vif (et
 * un halo crème s'il est Commun). Pas de particules, pas d'anneau.
 *
 * <p>Seuls les objets <b>en vue</b> de la caméra ont leur halo (rien à travers un mur). Performance : sélection une
 * fois par tick, au plus {@value #MAX} objets à {@value #RAYON} blocs triés par distance, un rayon (bloc) par objet
 * retenu, rareté mise en cache ; rendu : <b>un quad par objet, un seul lot</b> ({@code submitCustomGeometry}).
 * Réglage : Réglages → Interface → « Lueur de rareté ».
 */
public final class AuraSol {

    private static final Identifier TEX = DaKit.tex("inventaire", "lueur");
    private static final int MAX = 48, IMAGES = 4;
    private static final double RAYON = 16;
    /** Demi-côté du halo : 11 texels de 1/16 de bloc. */
    private static final float DEMI = 11f / 32f;
    /** Halo de l'objet visé quand il est Commun (crème). */
    private static final int VISE_COMMUN = 0xEDE4CF;

    private record Cache(ItemStack stack, Rarete r) {}
    private static final Map<Integer, Cache> RARETE = new HashMap<>();
    private record Objet(ItemEntity e, Rarete r, boolean vise) {}

    /** Objets retenus au dernier tick (lus à chaque image). */
    private static List<Objet> lot = List.of();

    private AuraSol() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AuraSol::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(AuraSol::collecter);
    }

    private static Rarete rarete(ItemEntity e) {
        ItemStack s = e.getItem();
        Cache c = RARETE.get(e.getId());
        if (c != null && c.stack() == s) return c.r();
        Rarete r = Rarete.of(s);
        RARETE.put(e.getId(), new Cache(s, r));
        return r;
    }

    private record Cand(ItemEntity e, Rarete r, double d2) {}

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { lot = List.of(); return; }
        RebornPrefs.INSTANCE.ensureLoaded();
        if (!RebornPrefs.INSTANCE.aurasSol) { lot = List.of(); return; }
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        ItemEntity cible = RamasserClient.cible();
        double r2 = RAYON * RAYON;
        List<Cand> cands = new ArrayList<>();
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(cam, cam).inflate(RAYON))) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            Rarete r = rarete(e);
            if (r == Rarete.COMMUN && e != cible) continue;
            double d2 = e.position().distanceToSqr(cam);
            if (d2 > r2) continue;
            cands.add(new Cand(e, r, d2));
        }
        if (RARETE.size() > 512) RARETE.clear();
        if (cands.isEmpty()) { lot = List.of(); return; }
        cands.sort((a, b) -> Double.compare(a.d2(), b.d2()));
        List<Objet> res = new ArrayList<>(Math.min(MAX, cands.size()));
        for (Cand c : cands) {
            if (res.size() >= MAX) break;
            boolean vise = c.e() == cible;
            if (!vise && !enVue(mc, cam, c.e().position().add(0, 0.2, 0))) continue;
            res.add(new Objet(c.e(), c.r(), vise));
        }
        lot = res;
    }

    /** Rien d'opaque entre la caméra et l'objet (blocs pleins : l'herbe haute ne cache pas). */
    private static boolean enVue(Minecraft mc, Vec3 cam, Vec3 p) {
        HitResult h = mc.level.clip(new ClipContext(cam, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return h.getType() == HitResult.Type.MISS || h.getLocation().distanceToSqr(p) < 0.35 * 0.35;
    }

    private static void collecter(LevelRenderContext ctx) {
        final List<Objet> objets = lot;
        if (objets.isEmpty()) return;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        if (cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long t = System.currentTimeMillis();
        PoseStack ps = ctx.poseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(TEX), (pose, vc) -> {
            for (Objet o : objets) {
                if (!o.e().isAlive()) continue;
                Vec3 p = o.e().getPosition(pt);
                int id = o.e().getId();
                // respiration lente (≈ 2,8 s), déphasée par objet
                float souffle = 0.82f + 0.18f * (float) Math.sin((t + id * 977L) % 2800L / 2800.0 * Math.PI * 2);
                float a = Math.min(1f, (o.r().force + (o.vise() ? 0.3f : 0f)) * souffle);
                int rgb = o.r() == Rarete.COMMUN ? VISE_COMMUN : o.r().rgb;
                quad(pose, vc, (float) p.x, (float) p.y + 0.015f, (float) p.z, image(o.r(), id, t), rgb, a);
            }
        });
        ps.popPose();
    }

    /** Image de la texture : 0 = halo seul ; à partir de Rare, 1-3 (pixels qui scintillent) pendant ~0,2 s de temps en temps. */
    private static int image(Rarete r, int id, long t) {
        int periode = switch (r) {
            case LEGENDAIRE -> 2200;
            case EPIQUE -> 3000;
            case RARE -> 4200;
            default -> 0;
        };
        if (periode == 0) return 0;
        long ph = (t + id * 1543L) % periode;
        if (ph >= 210) return 0;
        return 1 + (int) ((t + id * 1543L) / periode % 3);
    }

    /** Quad horizontal centré (x, y, z), image {@code img} de l'atlas. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int img, int rgb, float a) {
        int c = ((Math.round(a * 255) & 0xFF) << 24) | (rgb & 0xFFFFFF);
        float u0 = img / (float) IMAGES, u1 = (img + 1) / (float) IMAGES;
        sommet(pose, vc, x - DEMI, y, z - DEMI, u0, 0f, c);
        sommet(pose, vc, x - DEMI, y, z + DEMI, u0, 1f, c);
        sommet(pose, vc, x + DEMI, y, z + DEMI, u1, 1f, c);
        sommet(pose, vc, x + DEMI, y, z - DEMI, u1, 0f, c);
    }

    private static void sommet(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f);
    }
}
