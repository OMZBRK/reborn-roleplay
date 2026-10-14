package fr.reborn.hud.combat;

import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD de cooldowns façon Reborn : une rangée d'icônes de capacités
 * ({@link CooldownState.Ability}) avec balayage radial + secondes restantes quand
 * en cooldown. Élément HUD déplaçable (registre {@code cooldowns} dans
 * {@code RebornHudClient}). Icône = texture {@code reborn:textures/gui/ability/<name>.png}
 * si livrée, sinon carré coloré + glyphe placeholder.
 */
public final class CooldownHud {

    private CooldownHud() {}

    /**
     * Côté d'un créneau en unités de l'élément HUD : 16 = une icône 32 px à 1:1 à l'échelle 2 (élément à 1,0).
     * Les icônes peintes portent leur propre cadre (gabarit de l'utilisateur) : plus de créneau laqué autour.
     */
    public static final int ICON = 16, GAP = 3;
    private static final CooldownState.Ability[] SHOWN = CooldownState.Ability.values();
    private static final java.util.Map<String, Boolean> TEX_EXISTS = new ConcurrentHashMap<>();
    /** LUT d'angles par rayon pour le voile radial : à rayon fixe l'angle de chaque
     *  offset (dx,dy) est constant → un seul atan2 par pixel, calculé une fois. */
    private static final java.util.Map<Integer, float[]> RADIAL_ANGLE = new ConcurrentHashMap<>();
    /** Cache des libellés de secondes, clé = dixièmes affichés → évite String.format/frame. */
    private static final java.util.Map<Integer, String> SEC_FMT = new ConcurrentHashMap<>();

    public static int width()  { return SHOWN.length * ICON + Math.max(0, SHOWN.length - 1) * GAP; }
    public static int height() { return ICON + 9; }   // icône + ligne de secondes en dessous

    /**
     * Les icônes sont posées au <b>pixel écran</b> à une taille multiple de 16 px (texture 32 px si la taille est un
     * multiple de 32, sinon la version 16 px) : jamais de réduction au plus proche, quelle que soit l'échelle de
     * l'élément (éditeur de HUD) ou l'échelle GUI. Taille retenue = le plus grand multiple de 16 qui tient dans le
     * créneau (16 unités × échelle de l'élément × échelle GUI), centrée dedans. Les secondes restent en unités GUI.
     */
    public static void render(GuiGraphicsExtractor ctx, int x, int y, float scale) {
        long now = System.currentTimeMillis();
        Font font = Minecraft.getInstance().font;
        int gs = fr.reborn.hud.ui.DaKit.echelleGui();
        float cible = ICON * scale * gs;                          // côté du créneau en pixels écran
        int d = Math.max(16, (int) Math.floor(cible / 16f + 0.02f) * 16);
        int off = Math.max(0, Math.round((cible - d) / 2f));
        // On n'affiche que les capacités EN COOLDOWN (sauf celles marquées alwaysShow) ;
        // elles se compactent depuis la gauche.
        int ix = 0;
        for (CooldownState.Ability a : SHOWN) {
            boolean onCd = CooldownState.INSTANCE.fraction(a, now) > 0.001f;
            if (!onCd && !a.alwaysShow && !CooldownState.INSTANCE.isActive(a)) continue;
            int sx = Math.round((x + ix * scale) * gs) + off, sy = Math.round(y * gs) + off;
            drawIcon(ctx, font, sx, sy, d, a, now);
            float frac = CooldownState.INSTANCE.fraction(a, now);
            if (frac > 0.001f) {
                // secondes SOUS l'icône (unités GUI, comme avant)
                float secs = CooldownState.INSTANCE.remainingMs(a, now) / 1000f;
                int tenths = Math.round(secs * 10f);
                String s = SEC_FMT.computeIfAbsent(tenths, t -> String.format(Locale.US, "%.1f", t / 10f));
                ctx.pose().pushMatrix();
                ctx.pose().translate(x + ix * scale, y);
                if (scale != 1f) ctx.pose().scale(scale, scale);
                fr.reborn.hud.ui.DaKit.inter7o(ctx, font, fr.reborn.hud.ui.DaKit.inter(s), ICON / 2f, ICON + 4.5f,
                    fr.reborn.hud.ui.DaKit.CREME, 1);
                ctx.pose().popMatrix();
            }
            ix += ICON + GAP;
        }
    }

    /** Icône de côté {@code d} pixels écran en (px, py) pixels écran, voile radial si en recharge. */
    private static void drawIcon(GuiGraphicsExtractor ctx, Font font, int px, int py, int d,
                                 CooldownState.Ability a, long now) {
        float frac = CooldownState.INSTANCE.fraction(a, now);
        boolean ready = frac <= 0.001f;
        String slug = a.name().toLowerCase(Locale.ROOT);
        int cote = d % 32 == 0 ? 32 : 16;
        String nom = cote == 32 ? slug : slug + "_16";
        if (!texExists(nom)) { nom = slug; cote = 32; }
        fr.reborn.hud.ui.DaKit.debutPx(ctx);
        if (texExists(nom)) {
            int frames = Math.max(1, a.frames);
            int fr = (frames <= 1 || a.frameMs <= 0) ? 0 : (int) ((now / a.frameMs) % frames);
            ctx.blit(RenderPipelines.GUI_TEXTURED, iconId(nom), px, py, 0f, (float) (fr * cote), d, d, cote, cote,
                cote, cote * frames);
        } else {
            ctx.fill(px, py, px + d, py + d, withA(a.color, 0.35f));
        }
        // Cooldown : voile radial LÉGER (l'icône reste bien visible), dessiné au pixel écran.
        if (!ready) radialCover(ctx, px, py, d, frac, 0x66000000);
        fr.reborn.hud.ui.DaKit.finPx(ctx);
    }

    /** Assombrit l'arc de cooldown RESTANT (horaire depuis le haut, se réduit à mesure). */
    private static void radialCover(GuiGraphicsExtractor ctx, int x, int y, int size, float frac, int color) {
        fr.reborn.hud.ui.LotGui lot = fr.reborn.hud.ui.LotGui.ouvrir(ctx);   // 1 élément GUI (cf. LotGui)
        int cx = x + size / 2, cy = y + size / 2;
        float startDeg = (1f - frac) * 360f;   // l'arc restant va de startDeg à 360
        int r = size / 2;
        float[] lut = radialAngleLut(r);       // angles précalculés (constants à rayon fixe)
        int span = 2 * r;
        // Batch : une span horizontale par run contigu « allumé » au lieu d'un fill 1×1/pixel.
        for (int dy = -r; dy < r; dy++) {
            int base = (dy + r) * span;
            int runStart = Integer.MIN_VALUE;
            for (int dx = -r; dx < r; dx++) {
                boolean on = lut[base + dx + r] >= startDeg;
                if (on) {
                    if (runStart == Integer.MIN_VALUE) runStart = dx;
                } else if (runStart != Integer.MIN_VALUE) {
                    lot.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                lot.fill(cx + runStart, cy + dy, cx + r, cy + dy + 1, color);
            }
        }
        lot.fermer();
    }

    /** Angles (deg, 0 en haut, horaire) par offset (dx,dy) pour un rayon donné — 1 calcul/rayon. */
    private static float[] radialAngleLut(int r) {
        return RADIAL_ANGLE.computeIfAbsent(r, rr -> {
            int span = 2 * rr;
            float[] a = new float[span * span];
            for (int dy = -rr; dy < rr; dy++) {
                for (int dx = -rr; dx < rr; dx++) {
                    float ang = (float) Math.toDegrees(Math.atan2(dx, -dy));
                    if (ang < 0) ang += 360f;
                    a[(dy + rr) * span + (dx + rr)] = ang;
                }
            }
            return a;
        });
    }

    private static Identifier iconId(String slug) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/ability/" + slug + ".png");
    }

    private static boolean texExists(String slug) {
        return TEX_EXISTS.computeIfAbsent(slug, s -> {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.getResourceManager().getResource(iconId(s)).isPresent();
        });
    }

    private static int withA(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) == 0 ? 255 : (argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
