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
    private static final CooldownState.Ability[] SHOWN = CooldownState.Ability.values();
    private static final java.util.Map<String, Boolean> TEX_EXISTS = new ConcurrentHashMap<>();

    public static int width()  { return SHOWN.length * ICON + Math.max(0, SHOWN.length - 1) * GAP; }
    public static int height() { return ICON; }

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
            if (!onCd && !a.alwaysShow) continue;
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
            // Icône = bande verticale de frames (côté = inner). On anime par le temps.
            int inner = ICON - 6;
            int frames = Math.max(1, a.frames);
            int fr = (frames <= 1 || a.frameMs <= 0) ? 0 : (int) ((now / a.frameMs) % frames);
            ctx.blit(RenderPipelines.GUI_TEXTURED, iconId(slug),
                x + 3, y + 3, 0f, (float) (fr * inner), inner, inner, inner, inner * frames);
        } else {
            Component g = RebornFont.arcade(a.glyph);
            ctx.text(font, g, x + (ICON - font.width(g)) / 2, y + (ICON - 8) / 2,
                ready ? 0xFFFFFFFF : 0x99FFFFFF, false);
        }

        // Cooldown : cache radial (balayage horaire) + secondes restantes.
        if (!ready) {
            radialCover(ctx, x, y, ICON, frac, 0x99000000);
            String s = String.format(Locale.US, "%.1f", CooldownState.INSTANCE.remainingMs(a, now) / 1000f);
            Component t = RebornFont.bold(s);
            ctx.text(font, t, x + (ICON - font.width(t)) / 2, y + (ICON - 8) / 2, 0xFFFFFFFF, false);
        }
    }

    /** Assombrit l'arc de cooldown RESTANT (horaire depuis le haut, se réduit à mesure). */
    private static void radialCover(GuiGraphicsExtractor ctx, int x, int y, int size, float frac, int color) {
        int cx = x + size / 2, cy = y + size / 2;
        float startDeg = (1f - frac) * 360f;   // l'arc restant va de startDeg à 360
        int r = size / 2;
        for (int dy = -r; dy < r; dy++) {
            for (int dx = -r; dx < r; dx++) {
                float ang = (float) Math.toDegrees(Math.atan2(dx, -dy));
                if (ang < 0) ang += 360f;
                if (ang >= startDeg) ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
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
