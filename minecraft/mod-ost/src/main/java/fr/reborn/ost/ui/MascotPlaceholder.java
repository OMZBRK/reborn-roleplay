package fr.reborn.ost.ui;

import fr.reborn.ost.audio.OstCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Placeholder de mascotte par catégorie — un panneau coloré avec un
 * cercle "tête" stylisé + le nom de la catégorie + le compte de pistes.
 * Pas une vraie mascotte, juste un focal visuel qui anime le screen
 * en attendant les assets PNG dédiés.
 *
 * <p>Chaque catégorie a sa palette pour donner un sens visuel distinct
 * — l'utilisateur sait au coup d'œil dans quel onglet il est.
 */
public final class MascotPlaceholder {

    /** Palette ARGB par catégorie : couleur principale (mascot). */
    private static final Map<OstCategory, Palette> PALETTES = new EnumMap<>(OstCategory.class);

    static {
        PALETTES.put(OstCategory.APAISANT,    new Palette(0xFF5DADE2, 0xFF1B4F72, "🌙"));
        PALETTES.put(OstCategory.COMBAT,      new Palette(0xFFE74C3C, 0xFF641E16, "⚔"));
        PALETTES.put(OstCategory.MISSION,     new Palette(0xFFF39C12, 0xFF7E5109, "🎯"));
        PALETTES.put(OstCategory.MOTIVATION,  new Palette(0xFFF1C40F, 0xFF7D6608, "🔥"));
        PALETTES.put(OstCategory.MYSTERE,     new Palette(0xFF8E44AD, 0xFF4A235A, "✦"));
        PALETTES.put(OstCategory.TRISTE,      new Palette(0xFF5D6D7E, 0xFF212F3D, "❄"));
        PALETTES.put(OstCategory.FAVORIS,     new Palette(0xFFE84393, 0xFF6C2247, "♥"));
    }

    private MascotPlaceholder() {}

    /**
     * Dessine la mascotte dans la box ({@code x, y, w, h}).
     *
     * @param trackCount nombre de pistes dans la catégorie courante
     *                   (affiché en sous-titre)
     */
    public static void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
                              OstCategory category, int trackCount) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;
        Palette pal = PALETTES.getOrDefault(category, PALETTES.get(OstCategory.APAISANT));

        // Fond gradient diagonal (haut→bas) primary → dark.
        g.fillGradient(x, y, x + w, y + h, pal.primary, pal.shadow);
        // Cadre 1px subtil.
        drawBorder(g, x, y, w, h, 0x55FFFFFF);

        // "Tête" — disque blanc semi-transparent centré, avec un cœur
        // de couleur primary plus saturée.
        int cx = x + w / 2;
        int cy = y + h / 2 - 10;
        int outerR = Math.min(w, h) / 4;
        int innerR = outerR - 6;
        drawDisc(g, cx, cy, outerR, 0x40FFFFFF);
        drawDisc(g, cx, cy, innerR, brighten(pal.primary, 0.15f));
        // Petit highlight pour effet 3D.
        drawDisc(g, cx - innerR / 3, cy - innerR / 3, innerR / 4, 0x88FFFFFF);

        // Symbole catégorie (centré sur le disque).
        String symbol = pal.symbol;
        int symW = tr.width(symbol);
        // Échelle x2 pour le symbole.
        float scale = 2.0f;
        g.pose().pushMatrix();
        g.pose().translate(cx - symW * scale / 2f, cy - tr.lineHeight * scale / 2f);
        g.pose().scale(scale, scale);
        g.text(tr, symbol, 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();

        // Nom de la catégorie (gros, sous le disque).
        String name = category.displayName().toUpperCase();
        int nameW = tr.width(name);
        float nameScale = 1.4f;
        int nameX = cx - Math.round(nameW * nameScale) / 2;
        int nameY = cy + outerR + 14;
        g.pose().pushMatrix();
        g.pose().translate(nameX, nameY);
        g.pose().scale(nameScale, nameScale);
        g.text(tr, name, 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();

        // Compte de pistes.
        String countLabel = trackCount + (trackCount > 1 ? " pistes" : " piste");
        int countW = tr.width(countLabel);
        g.text(tr, countLabel, cx - countW / 2,
            nameY + Math.round(tr.lineHeight * nameScale) + 6, 0xCCEEEEEE, false);
    }

    // ──────────────────────────────────────────────────────
    // Helpers de rendu primitifs (évite la dépendance à DrawHelpers
    // de mod-integrity — on reste self-contained dans mod-ost).
    // ──────────────────────────────────────────────────────

    private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawDisc(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static int brighten(int argb, float t) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * t));
        g = Math.min(255, Math.round(g + (255 - g) * t));
        b = Math.min(255, Math.round(b + (255 - b) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record Palette(int primary, int shadow, String symbol) {}
}
