package fr.reborn.hud.nameplate;

import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Set;

/**
 * Plaques de nom RP au-dessus des têtes — remplacent le pseudo Minecraft (masqué
 * côté serveur par une équipe {@code NAME_TAG_VISIBILITY=NEVER} et côté client par
 * {@code EntityNameTagHideMixin}). Rendu <b>2D HUD</b> (projection de la tête à l'écran).
 *
 * <p>Quatre styles au choix (Options → Interface → Noms au-dessus des têtes,
 * {@link RebornPrefs#nameplateStyle}) :
 * <ol start="0">
 *   <li><b>Détouré</b> : le nom seul, contour sombre, petit carré à la couleur du clan.</li>
 *   <li><b>Filet doré</b> : nom, filet d'or à losange à la couleur du clan.</li>
 *   <li><b>Village</b> : emblème du village + nom sur une bande sombre estompée, filet du clan.</li>
 *   <li><b>Ruban</b> : nom sur un ruban vermillon à pointes.</li>
 * </ol>
 * Tout tient sur <b>une ligne</b> : le nom RP envoyé par le serveur est déjà « Prénom Clan ».
 * Joueur non présenté = « ??? » (« Inconnu » pour le style Village), en gris.
 *
 * <p>La position de la tête est <b>interpolée</b> sur l'image affichée (partial tick) :
 * avant, elle prenait la position du dernier tick (20/s) et la plaque « traînait » derrière
 * le perso quand il bougeait.
 */
public final class Nameplates {

    private Nameplates() {}

    /** Portée de visibilité de la plaque (blocs) — « quand on se rapproche ». */
    private static final double MAX_DIST = 5.0;
    /** Marge d'inflation du hitbox pour le test « je le regarde vraiment » (tolérance). */
    private static final double LOOK_INFLATE = 0.30;
    private static final int OUTLINE = 0xFF0E080A, UNKNOWN = 0xFFA8A0A0, NEUTRAL_CLAN = 0xFFC8B4A0;
    private static final Set<String> VILLAGES = Set.of("konoha", "suna", "kiri", "kumo", "iwa", "ame");

    private static final ThreadLocal<Matrix4f> VP = ThreadLocal.withInitial(Matrix4f::new);
    private static final ThreadLocal<Vector4f> CLIP = ThreadLocal.withInitial(Vector4f::new);

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        // Sans data serveur (hors serveur Shinobi / solo), on ne remplace rien.
        if (!TablistData.hasData()) return;

        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(VP.get());
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // Rayon du VISEUR (œil du joueur local + direction de regard), borné à MAX_DIST :
        // la plaque n'apparaît que si ce rayon touche le hitbox de la cible = « on le
        // regarde vraiment » (indépendant de la perspective 1re/3e personne).
        Vec3 eye = mc.player.getEyePosition(pt);
        Vec3 look = mc.player.getViewVector(pt);
        Vec3 rayEnd = eye.add(look.x * MAX_DIST, look.y * MAX_DIST, look.z * MAX_DIST);

        for (Player e : mc.level.players()) {
            if (e == mc.player) continue;                 // pas de plaque sur soi
            if (e.isCrouching()) continue;                // accroupi = discret (comme vanilla)
            if (e.isInvisible()) continue;
            double dist = mc.player.getPosition(pt).distanceTo(e.getPosition(pt));
            if (dist > MAX_DIST) continue;                // seulement de près (~5 blocs)
            // On ne l'affiche que si le viseur pointe réellement sur le joueur.
            if (e.getBoundingBox().inflate(LOOK_INFLATE).clip(eye, rayEnd).isEmpty()) continue;
            drawFor(ctx, font, e, camPos, vp, gw, gh, dist, pt);
        }
    }

    private static void drawFor(GuiGraphicsExtractor ctx, Font font, Player e, Vec3 camPos,
                                Matrix4f vp, int gw, int gh, double dist, float pt) {
        TablistData.RpName rp = TablistData.rpNameFor(e.getUUID());
        boolean known = rp != null && rp.relation() != TabEntry.Relation.INCONNU
            && rp.name() != null && !rp.name().isBlank();
        String name = known ? rp.name() : null;
        int clanCol = known && rp.clanColor() != 0 ? (0xFF000000 | rp.clanColor()) : NEUTRAL_CLAN;
        String village = known ? villageKey(rp.village()) : null;

        Vec3 head = e.getEyePosition(pt).add(0.0, 0.62, 0.0);
        Vector4f clip = vp.transform(CLIP.get().set(
            (float) (head.x - camPos.x),
            (float) (head.y - camPos.y),
            (float) (head.z - camPos.z), 1f));
        if (clip.w() <= 0.05f) return;                    // derrière la caméra
        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        if (ndcX < -1.1f || ndcX > 1.1f || ndcY < -1.1f || ndcY > 1.1f) return;
        float sx = (ndcX * 0.5f + 0.5f) * gw;
        float sy = (1f - (ndcY * 0.5f + 0.5f)) * gh;
        float alpha = (float) Math.max(0.45, 1.0 - dist / (MAX_DIST + 2.0));

        switch (Math.floorMod(RebornPrefs.INSTANCE.nameplateStyle, 4)) {
            case 1 -> drawFilet(ctx, font, sx, sy, name, clanCol, alpha);
            case 2 -> drawVillage(ctx, font, sx, sy, name, clanCol, village, alpha);
            case 3 -> drawRuban(ctx, font, sx, sy, name, alpha);
            default -> drawDetoure(ctx, font, sx, sy, name, clanCol, alpha);
        }
    }

    /**
     * Plaque au style choisi par le joueur, centrée en {@code (x, y)} (bas de la plaque) — pour les écrans qui
     * montrent un perso hors du monde (sélection du personnage).
     */
    public static void drawPlate(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                 String village, float alpha) {
        int col = 0xFF000000 | clanCol;
        switch (Math.floorMod(RebornPrefs.INSTANCE.nameplateStyle, 4)) {
            case 1 -> drawFilet(g, f, x, y, name, col, alpha);
            case 2 -> drawVillage(g, f, x, y, name, col, villageKey(village), alpha);
            case 3 -> drawRuban(g, f, x, y, name, alpha);
            default -> drawDetoure(g, f, x, y, name, col, alpha);
        }
    }

    /** Style « Village » quel que soit le réglage (emblème + nom). */
    public static void drawVillagePlate(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                        String village, float alpha) {
        drawVillage(g, f, x, y, name, 0xFF000000 | clanCol, villageKey(village), alpha);
    }

    // ── styles ───────────────────────────────────────────────────

    /** Détouré : nom seul avec contour, carré du clan à droite. */
    private static void drawDetoure(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        float w = Da.width(f, s, sc);
        float top = y - 8 * sc - 2;
        outlined(g, f, s, x, top, sc, name != null ? Da.CREAM : UNKNOWN, OUTLINE, a);
        if (name != null) {
            int bx = Math.round(x + w / 2f + 3), by = Math.round(top + 1);
            g.fill(bx - 1, by - 1, bx + 5, by + 5, fade(OUTLINE, a));
            g.fill(bx, by, bx + 4, by + 4, fade(clanCol, a));
        }
    }

    /** Filet doré : nom, filet d'or à losange à la couleur du clan. */
    private static void drawFilet(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        float top = y - 14;
        outlined(g, f, s, x, top, sc, name != null ? Da.CREAM : UNKNOWN, 0xFF000000, a);
        int ly = Math.round(top + 8 * sc + 2);
        int half = Math.max(16, Math.round(Da.width(f, s, sc) / 2f) + 6);
        int cx = Math.round(x);
        g.fill(cx - half, ly, cx - 3, ly + 1, fade(Da.GOLD, a));
        g.fill(cx + 4, ly, cx + half + 1, ly + 1, fade(Da.GOLD, a));
        int dc = fade(name != null ? clanCol : UNKNOWN, a);
        g.fill(cx, ly - 2, cx + 1, ly + 3, dc);
        g.fill(cx - 1, ly - 1, cx + 2, ly + 2, dc);
    }

    /** Village : emblème du village + nom sur une bande estompée, filet de la couleur du clan. */
    private static void drawVillage(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                    String village, float a) {
        float sc = Da.title();
        String s = name != null ? name : "Inconnu";
        int icon = 8;
        boolean hasIcon = name != null;
        int tw = Da.width(f, s, sc);
        int w = tw + (hasIcon ? icon + 14 : 12), h = 12;
        int x0 = Math.round(x - w / 2f), y0 = Math.round(y - h - 3);
        // bande sombre qui s'estompe sur les bords
        int fadeW = Math.max(1, Math.min(8, w / 3));
        for (int i = 0; i < w; i++) {
            float k = Math.min(1f, Math.min(i, w - 1 - i) / (float) fadeW);
            g.fill(x0 + i, y0, x0 + i + 1, y0 + h, fade(((int) (0xB4 * k) << 24) | 0x0A0608, a));
        }
        int tx = x0 + 6;
        if (hasIcon) {
            Identifier id = village != null
                ? Identifier.fromNamespaceAndPath("reborn", "textures/gui/esc/village_" + village + ".png") : null;
            if (id != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, id, tx, y0 + 2, 0f, 0f, icon, icon, 48, 48, 48, 48, fade(Da.CREAM, a));
            } else {
                // village inconnu côté client : petit sceau à la couleur du clan
                g.fill(tx, y0 + 2, tx + icon, y0 + 2 + icon, fade(clanCol, a));
                g.fill(tx + 3, y0 + 4, tx + 5, y0 + icon, fade(Da.CREAM, a));
                g.fill(tx + 2, y0 + 5, tx + icon - 2, y0 + 6, fade(Da.CREAM, a));
            }
            tx += icon + 4;
        }
        Da.text(g, f, s, tx, y0 + (h - 8 * sc) / 2f + .5f, sc, fade(name != null ? Da.CREAM : UNKNOWN, a), 0);
        g.fill(x0 + 2, y0 + h, x0 + w - 2, y0 + h + 1, fade(name != null ? clanCol : 0xFF786E6E, a));
    }

    /** Ruban : nom sur un ruban vermillon à pointes. */
    private static void drawRuban(GuiGraphicsExtractor g, Font f, float x, float y, String name, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        int tw = Da.width(f, s, sc);
        int w = tw + 14, h = 11;
        int x0 = Math.round(x - w / 2f), y0 = Math.round(y - 15);
        int body = fade(name != null ? 0xFF96202A : 0xFF463C3E, a);
        int light = fade(name != null ? 0xFFD65A4A : 0xFF645A5C, a);
        g.fill(x0, y0, x0 + w, y0 + h, body);
        g.fill(x0, y0 + 1, x0 + w, y0 + 2, light);
        // queues d'aronde : rangées plus courtes au milieu (encoche)
        for (int i = 0; i < h; i++) {
            int notch = Math.round(3 * (1f - Math.abs(i - (h - 1) / 2f) / ((h - 1) / 2f)));
            g.fill(x0 - 5 + notch, y0 + i, x0, y0 + i + 1, body);
            g.fill(x0 + w, y0 + i, x0 + w + 5 - notch, y0 + i + 1, body);
        }
        Da.text(g, f, s, x, y0 + (h - 8 * sc) / 2f + .5f, sc, fade(name != null ? Da.CREAM : UNKNOWN, a), 1);
    }

    // ── outils ───────────────────────────────────────────────────

    /** « Konohagakure », « Konoha no Sato »… → clé d'emblème (konoha, suna, kiri, kumo, iwa) ou null. */
    static String villageKey(String v) {
        if (v == null || v.isBlank()) return null;
        String k = v.trim().toLowerCase(Locale.ROOT);
        for (String id : VILLAGES) if (k.startsWith(id)) return id;
        return null;
    }

    /** Texte ArcadePix centré avec contour (lisible sur tout fond). */
    private static void outlined(GuiGraphicsExtractor g, Font f, String s, float cx, float y, float sc,
                                 int col, int outline, float a) {
        float px = 1f / (float) Minecraft.getInstance().getWindow().getGuiScale();
        int oc = fade(outline, a);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) Da.text(g, f, s, cx + dx * px * 2, y + dy * px * 2, sc, oc, 1);
        Da.text(g, f, s, cx, y, sc, fade(col, a), 1);
    }

    /** Atténue l'alpha (plaque plus discrète de loin). */
    private static int fade(int argb, float f) {
        int al = (int) (((argb >>> 24) & 0xFF) * f);
        return (al << 24) | (argb & 0xFFFFFF);
    }
}
