package fr.reborn.integrity.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Font Reborn — TTF embarquee dans assets/reborn/font/reborn.ttf.
 *
 * <p>Minecraft charge automatiquement les fonts custom declarees dans
 * {@code assets/<ns>/font/<name>.json}. Notre identifier est
 * {@code reborn:reborn} et pointe sur la TTF Minecraft-style fournie
 * par le designer.
 *
 * <p>Usage type :
 * <pre>{@code
 * context.drawText(textRenderer, RebornFont.styled("REBORN"), x, y, 0xFFFFFFFF, true);
 * }</pre>
 */
public final class RebornFont {

    /** Identifier de la font dans le registry Minecraft (auto-chargee depuis le JSON). */
    public static final Identifier FONT_ID = Identifier.of("reborn", "reborn");

    /** Style pre-cree qui applique notre font ; reutilisable en immutable. */
    public static final Style STYLE = Style.EMPTY.withFont(FONT_ID);

    private RebornFont() {}

    /** Construit un {@link Text} stylise avec la font Reborn. */
    public static MutableText styled(String content) {
        return Text.literal(content).setStyle(STYLE);
    }
}
