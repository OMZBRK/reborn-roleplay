package fr.reborn.hud.eau;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.ByteBuffer;

/**
 * <b>Marche sur l'eau</b>, côté client (15/10) — la mécanique du joueur local :
 * <ul>
 *   <li>quand le serveur la <b>permet</b> ({@code reborn:eau}, ShinobiAbilities {@code MarcheEau} : capacité apprise,
 *       chakra, état…), la surface de l'eau devient un sol pour le joueur local seulement
 *       ({@code mixin/BlockStateEauMixin} : forme de collision à la hauteur de l'eau + 2 cm, uniquement si les pieds
 *       sont au-dessus). Le joueur y marche, trotte, sprinte et saute avec la physique normale du jeu ;</li>
 *   <li><b>accroupi maintenu</b> (~0,35 s) : on s'enfonce volontairement (un appui bref, pour le dash ou la course,
 *       ne fait pas couler) ;</li>
 *   <li>dans l'eau, <b>Saut</b> près de la surface : on remonte d'un bond sur l'eau ;</li>
 *   <li>permis retiré (chakra vide, KO, douleur, étourdi, mode staff…) : la surface redevient de l'eau, on tombe.</li>
 * </ul>
 * Les ondes, gouttelettes et bruits de chaque pas (vus et entendus de tous, l'eau du jeu n'est pas déformée) sont dans {@link Ondes} et
 * {@link EauRendu}.
 */
public final class MarcheEauClient {

    /** Permis du serveur (faux sans plugin). */
    private static boolean permis;
    /** Banc de captures : permis forcé en solo. */
    public static boolean debug;
    /** Lu par la collision (chemin chaud) : le joueur local tient-il sur l'eau en ce moment ? */
    public static volatile boolean solide;
    /** Dernière raison d'un refus (0 aucune). */
    private static byte raison;
    private static int accroupi;
    private static int rebond;

    private static final double LEVRE = 0.02;
    private static final VoxelShape[] FORMES = new VoxelShape[101];

    private MarcheEauClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(EauPayload.ID, EauPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(EauPayload.ID,
                (payload, context) -> context.client().execute(() -> recevoir(payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(MarcheEauClient::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(EauRendu::apresEauTranslucide);
        EauDebug.init();
    }

    private static void reset() {
        permis = false;
        solide = false;
        raison = 0;
        accroupi = 0;
        Ondes.vider();
    }

    static void recevoir(byte[] d) {
        if (d == null || d.length == 0) return;
        ByteBuffer b = ByteBuffer.wrap(d);
        byte op = b.get();
        if (op == 1 && b.remaining() >= 1) {
            permis = b.get() != 0;
            raison = b.remaining() >= 1 ? b.get() : 0;
        } else if (op == 0 && b.remaining() >= 1) {
            if (b.get() == 0) permis = false;     // désactivé sur le serveur
        }
    }

    public static boolean permis() { return permis || debug; }

    /** Cette entité se tient-elle debout à la surface de l'eau (marche sur l'eau, vue de ce client) ? */
    public static boolean surEau(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.Entity e) {
        return !Double.isNaN(Ondes.surfaceSous(level, e));
    }

    public static byte raison() { return raison; }

    private static void tick(Minecraft mc) {
        Ondes.tick(mc);
        LocalPlayer p = mc.player;
        if (p == null) { solide = false; return; }
        boolean ok = permis() && !p.isSpectator() && !p.getAbilities().flying && !p.isPassenger() && !p.isFallFlying();
        if (p.isShiftKeyDown()) accroupi++; else accroupi = 0;
        if (accroupi >= 7) ok = false;           // accroupi maintenu : on s'enfonce volontairement
        solide = ok;
        if (rebond > 0) rebond--;
        // Remonter sur l'eau : dans l'eau, près de la surface, Saut → un bond qui sort les pieds de l'eau
        if (ok && rebond == 0 && p.isInWater() && !p.isShiftKeyDown() && mc.options.keyJump.isDown()) {
            double surf = surfaceAuDessus(mc, p.blockPosition());
            if (!Double.isNaN(surf) && surf - p.getY() < 1.75 && surf - p.getY() > 0) {
                var v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, 0.72, v.z);
                rebond = 14;
            }
        }
    }

    /** Hauteur de la surface de l'eau dans la colonne du joueur (en remontant jusqu'à 3 blocs), NaN sinon. */
    private static double surfaceAuDessus(Minecraft mc, BlockPos pied) {
        if (mc.level == null) return Double.NaN;
        BlockPos.MutableBlockPos m = pied.mutable();
        for (int k = 0; k < 3; k++) {
            FluidState f = mc.level.getFluidState(m);
            if (!f.is(FluidTags.WATER)) return Double.NaN;
            if (!mc.level.getFluidState(m.above()).is(FluidTags.WATER)) return m.getY() + f.getHeight(mc.level, m);
            m.move(0, 1, 0);
        }
        return Double.NaN;
    }

    /**
     * Forme de collision de l'eau pour le joueur local qui marche dessus (appelé par {@code BlockStateEauMixin} quand
     * {@link #solide}) : un bloc plein jusqu'à la surface + 2 cm, seulement pour le bloc de surface et seulement si
     * les pieds sont au-dessus ; {@code null} = forme d'origine.
     */
    public static VoxelShape forme(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(ctx instanceof EntityCollisionContext ec)) return null;
        Minecraft mc = Minecraft.getInstance();
        if (ec.getEntity() == null || ec.getEntity() != mc.player) return null;
        FluidState f = state.getFluidState();
        if (f.isEmpty() || !f.is(FluidTags.WATER)) return null;
        if (f.hasProperty(FlowingFluid.FALLING) && f.getValue(FlowingFluid.FALLING)) return null;   // chute d'eau
        if (level.getFluidState(pos.above()).is(FluidTags.WATER)) return null;                      // pas la surface
        float h = f.getHeight(level, pos);
        if (h < 0.1f) return null;
        int k = Math.max(0, Math.min(100, Math.round(h * 100)));
        VoxelShape s = FORMES[k];
        if (s == null) FORMES[k] = s = Shapes.box(0, 0, 0, 1, Math.min(1.0, k / 100.0 + LEVRE), 1);
        return ec.isAbove(s, pos, true) ? s : null;
    }
}
