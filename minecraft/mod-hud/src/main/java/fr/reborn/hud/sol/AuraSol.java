package fr.reborn.hud.sol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Aura au sol</b> des objets lâchés, à la couleur de leur rareté (GAMEPLAN §3.3, maquette 04_ramassage) : un halo
 * doux posé à plat sous l'objet + un anneau fin (à partir de Peu commun), trois braises qui montent pour un Légendaire.
 * L'objet visé (touche Ramasser) a son halo ravivé. Aucun faisceau, aucune particule en nuage (DA §6).
 *
 * <p>Performance : au plus {@value #MAX} objets à {@value #RAYON} blocs, triés par distance, envoyés en <b>un seul
 * lot</b> de géométrie ({@code submitCustomGeometry}, texture {@code gui/inventaire/aura.png} teintée au sommet) ;
 * rareté mise en cache par objet. Réglage : Réglages → Interface → « Auras des objets au sol ».
 */
public final class AuraSol {

    private static final Identifier TEX = DaKit.tex("inventaire", "aura"), BRAISE = DaKit.tex("inventaire", "braise");
    private static final int MAX = 48;
    private static final double RAYON = 16;
    private static final float HALO = 0.58f, ANNEAU = 0.46f;

    private record Cache(ItemStack stack, Rarete r) {}
    private static final Map<Integer, Cache> RARETE = new HashMap<>();
    private record Objet(ItemEntity e, Vec3 pos, Rarete r, double d2) {}

    private AuraSol() {}

    public static void init() {
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

    private static void collecter(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        RebornPrefs.INSTANCE.ensureLoaded();
        if (!RebornPrefs.INSTANCE.aurasSol) return;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        if (cam == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double r2 = RAYON * RAYON;
        List<Objet> objets = new ArrayList<>();
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof ItemEntity e) || !e.isAlive() || e.getItem().isEmpty()) continue;
            Vec3 p = e.getPosition(pt);
            double d2 = p.distanceToSqr(cam);
            if (d2 > r2) continue;
            objets.add(new Objet(e, p, rarete(e), d2));
        }
        if (RARETE.size() > 512) RARETE.clear();
        if (objets.isEmpty()) return;
        objets.sort((a, b) -> Double.compare(a.d2(), b.d2()));
        if (objets.size() > MAX) objets = objets.subList(0, MAX);
        final List<Objet> lot = objets;
        ItemEntity cible = RamasserClient.cible();
        long t = System.currentTimeMillis();

        PoseStack ps = ctx.poseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        RenderType rt = RenderTypes.entityTranslucentEmissive(TEX);
        ctx.submitNodeCollector().submitCustomGeometry(ps, rt, (pose, vc) -> {
            for (Objet o : lot) {
                boolean vise = o.e() == cible;
                float force = Math.min(0.95f, o.r().force + (vise ? 0.2f : 0f));
                int rgb = o.r().rgb;
                float x = (float) o.pos().x, y = (float) o.pos().y + 0.02f, z = (float) o.pos().z;
                plat(pose, vc, x, y, z, HALO, 0f, 0.5f, rgb, force * 0.85f);
                if (o.r() != Rarete.COMMUN || vise) plat(pose, vc, x, y + 0.003f, z, ANNEAU, 0.5f, 1f, rgb, Math.min(1f, force * 0.9f));
            }
        });
        boolean legendaire = false;
        for (Objet o : lot) if (o.r() == Rarete.LEGENDAIRE) { legendaire = true; break; }
        if (legendaire) {
            Camera c = mc.gameRenderer.mainCamera();
            Vector3fc up = c.upVector(), left = c.leftVector();
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(BRAISE), (pose, vc) -> {
                for (Objet o : lot) {
                    if (o.r() != Rarete.LEGENDAIRE) continue;
                    int seed = o.e().getId() * 31;
                    for (int i = 0; i < 3; i++) {
                        float cycle = 2200f + (seed + i * 97) % 600;
                        float ph = ((t + (seed + i * 733L) % 2000) % (long) cycle) / cycle;
                        float bx = (float) o.pos().x + (float) Math.sin(seed + i * 2.1) * 0.16f;
                        float bz = (float) o.pos().z + (float) Math.cos(seed + i * 1.7) * 0.16f;
                        float by = (float) o.pos().y + 0.15f + ph * 0.55f;
                        float a = (ph < 0.2f ? ph / 0.2f : 1f) * (1f - ph) * 0.9f;
                        billboard(pose, vc, bx, by, bz, 0.035f, up, left, 0xFFE9B8, a);
                    }
                }
            });
        }
        ps.popPose();
    }

    /** Quad horizontal centré (x, y, z), demi-côté s ; u de u0 à u1 (moitié gauche halo, droite anneau). */
    private static void plat(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float s,
                             float u0, float u1, int rgb, float a) {
        int c = ((Math.round(a * 255) & 0xFF) << 24) | (rgb & 0xFFFFFF);
        sommet(pose, vc, x - s, y, z - s, u0, 0f, c);
        sommet(pose, vc, x - s, y, z + s, u0, 1f, c);
        sommet(pose, vc, x + s, y, z + s, u1, 1f, c);
        sommet(pose, vc, x + s, y, z - s, u1, 0f, c);
    }

    private static void billboard(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float s,
                                  Vector3fc up, Vector3fc left, int rgb, float a) {
        int c = ((Math.round(a * 255) & 0xFF) << 24) | (rgb & 0xFFFFFF);
        float ux = up.x() * s, uy = up.y() * s, uz = up.z() * s, lx = left.x() * s, ly = left.y() * s, lz = left.z() * s;
        sommet(pose, vc, x + lx + ux, y + ly + uy, z + lz + uz, 0f, 0f, c);
        sommet(pose, vc, x + lx - ux, y + ly - uy, z + lz - uz, 0f, 1f, c);
        sommet(pose, vc, x - lx - ux, y - ly - uy, z - lz - uz, 1f, 1f, c);
        sommet(pose, vc, x - lx + ux, y - ly + uy, z - lz + uz, 1f, 0f, c);
    }

    private static void sommet(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f);
    }
}
