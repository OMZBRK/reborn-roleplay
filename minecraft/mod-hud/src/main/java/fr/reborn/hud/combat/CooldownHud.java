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
 * HUD de cooldowns façon Zenkai : une rangée d'icônes de capacités
 * ({@link CooldownState.Ability}) avec balayage radial + secondes restantes quand
 * en cooldown. Élément HUD déplaçable (registre {@code cooldowns} dans
 * {@code RebornHudClient}). Icône = texture {@code reborn:textures/gui/ability/<name>.png}
 * si livrée, sinon carré coloré + glyphe placeholder.
 */
public final class CooldownHud {

    private CooldownHud() {}

    public static final int ICON = 22, GAP = 4;
    /** Côté d'une frame source des textures d'icône (native 32×32, dessinée réduite). */
    private static final int FRAME = 32;
    private static final CooldownState.Ability[] SHOWN = CooldownState.Ability.values();
    private static final java.util.Map<String, Boolean> TEX_EXISTS = new ConcurrentHashMap<>();
    /** LUT d'angles par rayon pour le voile radial : à rayon fixe l'angle de chaque
     *  offset (dx,dy) est constant → un seul atan2 par pixel, calculé une fois. */
    private static final java.util.Map<Integer, float[]> RADIAL_ANGLE = new ConcurrentHashMap<>();
    /** Cache des libellés de secondes, clé = dixièmes affichés → évite String.format/frame. */
    private static final java.util.Map<Integer, String> SEC_FMT = new ConcurrentHashMap<>();

    public static int width()  { return SHOWN.length * ICON + Math.max(0, SHOWN.length - 1) * GAP; }
    public static int height() { return ICON + 10; }   // icône + ligne de secondes en dessous

    public static void render(GuiGraphicsExtractor ctx, int x, int y, float scale) {
        long now = System.currentTimeMillis();
        Font font = Minecraft.getInstance().font;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1f) ctx.pose().scale(scale, scale);
        // On n'affiche que les capacités EN COOLDOWN (sauf celles marquées alwaysShow) ;
        // elles se compactent depuis la gauche.
        int ix = 0;
        for (CooldownState.Ability a : SHOWN) {
            boolean onCd = CooldownState.INSTANCE.fraction(a, now) > 0.001f;
            if (!onCd && !a.alwaysShow && !CooldownState.INSTANCE.isActive(a)) continue;
            drawIcon(ctx, font, ix, 0, a, now);
            ix += ICON + GAP;
        }
        ctx.pose().popMatrix();
    }

    private static void drawIcon(GuiGraphicsExtractor ctx, Font font, int x, int y,
                                 CooldownState.Ability a, long now) {
        float frac = CooldownState.INSTANCE.fraction(a, now);
        boolean ready = frac <= 0.001f;

        int fill = withA(a.color, ready ? 0.5f : 0.22f);
        int border = ready ? withA(a.color, 0.95f) : withA(0xFFFFFFFF, 0.3f);
        DrawHelpers.roundedOutlinedRect(ctx, x, y, ICON, ICON, 5, fill, border);

        // Icône : texture si livrée, sinon glyphe placeholder.
        String slug = a.name().toLowerCase(Locale.ROOT);
        if (texExists(slug)) {
            // Icône 32×32 native (bande verticale de frames empilées), dessinée réduite
            // via blit avec scaling (dest inner ← source FRAME). Animée par le temps.
            int inner = ICON - 4;
            int frames = Math.max(1, a.frames);
            int fr = (frames <= 1 || a.frameMs <= 0) ? 0 : (int) ((now / a.frameMs) % frames);
            ctx.blit(RenderPipelines.GUI_TEXTURED, iconId(slug),
                x + 2, y + 2, 0f, (float) (fr * FRAME), inner, inner, FRAME, FRAME, FRAME, FRAME * frames);
        } else {
            Component g = RebornFont.arcade(a.glyph);
            ctx.text(font, g, x + (ICON - font.width(g)) / 2, y + (ICON - 8) / 2,
                ready ? 0xFFFFFFFF : 0x99FFFFFF, false);
        }

        // Cooldown : voile radial LÉGER (l'icône reste bien visible) + secondes SOUS l'icône.
        if (!ready) {
            radialCover(ctx, x, y, ICON, frac, 0x55000000);
            // Cache par dixième de seconde affiché : ne reformate que quand la valeur change.
            float secs = CooldownState.INSTANCE.remainingMs(a, now) / 1000f;
            int tenths = Math.round(secs * 10f);
            String s = SEC_FMT.computeIfAbsent(tenths, t -> String.format(Locale.US, "%.1f", t / 10f));
            Component t = RebornFont.bold(s);
            int tx = x + (ICON - font.width(t)) / 2, ty = y + ICON + 1;
            ctx.text(font, t, tx + 1, ty + 1, 0xC0000000, false);   // ombre lisibilité
            ctx.text(font, t, tx, ty, 0xFFFFFFFF, false);
        }
    }

    /** Assombrit l'arc de cooldown RESTANT (horaire depuis le haut, se réduit à mesure). */
    private static void radialCover(GuiGraphicsExtractor ctx, int x, int y, int size, float frac, int color) {
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
                    ctx.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                ctx.fill(cx + runStart, cy + dy, cx + r, cy + dy + 1, color);
            }
        }
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
