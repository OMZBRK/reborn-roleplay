package fr.reborn.hud.menu;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Hub des fonts Reborn — TTF embarquées dans
 * {@code assets/reborn/font/}, déclarées via les JSON manifestes
 * Minecraft (auto-chargés au boot).
 *
 * <p>Trois fonts principales + une fallback :
 * <ul>
 *   <li>{@link #DISPLAY} — Bebas Neue Regular. Gros titres, logo
 *       "REBORN", labels d'accroche (LIVE, NEW, etc.).</li>
 *   <li>{@link #BODY} — Inter 18pt Medium. Tout le texte courant
 *       (descriptions, hints, labels de Row).</li>
 *   <li>{@link #BOLD} — Inter 18pt Bold. Sous-titres, valeurs
 *       mises en évidence, server status.</li>
 *   <li>{@link #BLACK} — Inter 18pt Black. Boutons primaires,
 *       key caps (ESPACE, F1, etc.), countdown timers.</li>
 *   <li>{@link #FALLBACK} — la TTF historique (Minecraft.ttf
 *       renommée). Gardée pour compat avec l'ancien
 *       {@code RebornLogo} v1 jusqu'à ce qu'on supprime la v1.</li>
 * </ul>
 *
 * <p>Usage standard :
 * <pre>{@code
 *   context.text(font, RebornFont.display("REBORN"), x, y, color, false);
 * }</pre>
 */
public final class RebornFont {

    private RebornFont() {}

    public static final Identifier DISPLAY  = Identifier.fromNamespaceAndPath("reborn", "bebas_neue");
    public static final Identifier BODY     = Identifier.fromNamespaceAndPath("reborn", "inter");
    public static final Identifier BOLD     = Identifier.fromNamespaceAndPath("reborn", "inter_bold");
    public static final Identifier BLACK    = Identifier.fromNamespaceAndPath("reborn", "inter_black");
    public static final Identifier FALLBACK = Identifier.fromNamespaceAndPath("reborn", "reborn");
    /** ArcadePix (Reekee) — police pixel arcade pour le title screen. */
    public static final Identifier ARCADE   = Identifier.fromNamespaceAndPath("reborn", "arcadepix");

    // Police unifiée du main-menu : ArcadePix pour TOUS les titres, labels,
    // boutons, onglets et valeurs courtes (display/bold/black → arcade). Seul
    // BODY reste Inter, pour garder le texte long lisible (descriptions, règles,
    // lore, chat). Choix design 2026-08-20 (« mêmes polices que le main-menu »).
    public static final Style DISPLAY_STYLE  = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(ARCADE));
    public static final Style BODY_STYLE     = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(BODY));
    public static final Style BOLD_STYLE     = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(ARCADE));
    public static final Style BLACK_STYLE    = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(ARCADE));
    public static final Style FALLBACK_STYLE = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(FALLBACK));
    public static final Style ARCADE_STYLE   = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(ARCADE));

    public static MutableComponent display(String content)  { return Component.literal(content).setStyle(DISPLAY_STYLE); }
    public static MutableComponent body(String content)     { return Component.literal(content).setStyle(BODY_STYLE); }
    public static MutableComponent bold(String content)     { return Component.literal(content).setStyle(BOLD_STYLE); }
    public static MutableComponent black(String content)    { return Component.literal(content).setStyle(BLACK_STYLE); }
    /** Texte en police pixel ArcadePix (majuscules ASCII conseillées). */
    public static MutableComponent arcade(String content)   { return Component.literal(content).setStyle(ARCADE_STYLE); }

    /**
     * @deprecated PR #1 — gardé pour compat avec {@code RebornLogo}
     *     v1. À supprimer quand PR #2 aura migré tous les call sites
     *     vers {@link #display(String)}.
     */
    @Deprecated
    public static MutableComponent styled(String content) {
        return Component.literal(content).setStyle(FALLBACK_STYLE);
    }
}
