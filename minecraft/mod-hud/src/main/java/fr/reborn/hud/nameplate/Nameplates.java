package fr.reborn.hud.nameplate;

import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.menu.tablist.TabEntry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Plaques de nom RP au-dessus des têtes — remplacent le pseudo Minecraft (masqué
 * côté serveur par une équipe {@code NAME_TAG_VISIBILITY=NEVER}). Rendu <b>2D HUD</b>
 * (projection de la tête à l'écran via la matrice caméra, aucun rendu monde 3D →
 * défensif), même pattern que {@link fr.reborn.hud.voice.SpeechBubbles}.
 *
 * <p>Le texte affiché vient du roster {@code reborn:tablist} indexé par UUID
 * ({@link TablistData#rpNameFor}) : le nom RP « Prénom [Clan] » si le joueur est
 * <b>connu</b>, sinon « Inconnu ». Visible seulement de PRÈS ({@link #MAX_DIST}
 * blocs) et masqué quand la cible est accroupie (comme les pseudos vanilla).
 */
public final class Nameplates {

    private Nameplates() {}

    /** Portée de visibilité de la plaque (blocs) — « quand on se rapproche ». */
    private static final double MAX_DIST = 5.0;
    /** Marge d'inflation du hitbox pour le test « je le regarde vraiment » (tolérance). */
    private static final double LOOK_INFLATE = 0.30;
    private static final int BG        = 0xB0140D0A;   // fond sombre translucide
    private static final int NAME_COL  = 0xFFF2F2F2;   // nom connu (blanc)
    private static final int UNKNOWN_COL = 0xFFB4B4B4;  // « Inconnu » (gris)
    private static final String UNKNOWN_LABEL = "Inconnu";

    private static final ThreadLocal<Matrix4f> VP = ThreadLocal.withInitial(Matrix4f::new);
    private static final ThreadLocal<Vector4f> CLIP = ThreadLocal.withInitial(Vector4f::new);

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        // Sans data serveur (hors serveur Shinobi / solo), on ne remplace rien.
        if (!TablistData.hasData()) return;

        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(VP.get());
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // Rayon du VISEUR (œil du joueur local + direction de regard), borné à MAX_DIST :
        // la plaque n'apparaît que si ce rayon touche le hitbox de la cible = « on le
        // regarde vraiment » (indépendant de la perspective 1re/3e personne).
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f);
        Vec3 rayEnd = eye.add(look.x * MAX_DIST, look.y * MAX_DIST, look.z * MAX_DIST);

        for (Player e : mc.level.players()) {
            if (e == mc.player) continue;                 // pas de plaque sur soi
            if (e.isCrouching()) continue;                // accroupi = discret (comme vanilla)
            if (e.isInvisible()) continue;
            double dist = mc.player.position().distanceTo(e.position());
            if (dist > MAX_DIST) continue;                // seulement de près (~5 blocs)
            // On ne l'affiche que si le viseur pointe réellement sur le joueur.
            if (e.getBoundingBox().inflate(LOOK_INFLATE).clip(eye, rayEnd).isEmpty()) continue;
            drawFor(ctx, font, e, camPos, vp, gw, gh, dist);
        }
    }

    private static void drawFor(GuiGraphicsExtractor ctx, Font font, Player e, Vec3 camPos,
                                Matrix4f vp, int gw, int gh, double dist) {
        // Nom résolu serveur : connu → « Prénom [Clan] », sinon « Inconnu ».
        TablistData.RpName rp = TablistData.rpNameFor(e.getUUID());
        String label;
        int color;
        if (rp == null || rp.relation() == TabEntry.Relation.INCONNU
                || rp.name() == null || rp.name().isBlank()) {
            label = UNKNOWN_LABEL;
            color = UNKNOWN_COL;
        } else {
            label = rp.name();
            color = NAME_COL;
        }

        Vec3 head = e.getEyePosition(1f).add(0.0, 0.55, 0.0);
        Vector4f clip = vp.transform(CLIP.get().set(
            (float) (head.x - camPos.x),
            (float) (head.y - camPos.y),
            (float) (head.z - camPos.z), 1f));
        if (clip.w() <= 0.05f) return;                    // derrière la caméra
        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        if (ndcX < -1.1f || ndcX > 1.1f || ndcY < -1.1f || ndcY > 1.1f) return;
        int sx = Math.round((ndcX * 0.5f + 0.5f) * gw);
        int sy = Math.round((1f - (ndcY * 0.5f + 0.5f)) * gh);

        int tw = font.width(label);
        int padX = 3, h = 11;
        int x = sx - tw / 2 - padX, y = sy - h - 4;
        int w = tw + padX * 2;
        DrawHelpers.roundedRectFull(ctx, x, y, w, h, 4, fade(BG, dist));
        ctx.text(font, Component.literal(label), sx - tw / 2, y + 2, fade(color, dist), true);
    }

    /** Atténue l'alpha avec la distance (plaque plus discrète de loin). */
    private static int fade(int argb, double dist) {
        double f = Math.max(0.35, 1.0 - dist / (MAX_DIST + 2.0));
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
