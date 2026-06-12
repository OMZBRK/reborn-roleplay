package fr.reborn.hud.ui;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import net.minecraft.client.gui.DrawContext;

/**
 * Rendu du panel de style "chatbox MMO" derrière le chat history.
 * Inspiration : Lunar / Feather / chats RP type FrPg.
 *
 * <p>Style :
 * <ul>
 *   <li>Fond noir semi-transparent uniforme (lisible sur n'importe quel
 *       background).</li>
 *   <li>Bordure 1px blanc 25% opacite — discrete mais delimite la zone.</li>
 *   <li>Coin haut-gauche : touche subtile d'accent Reborn (3 pixels coin
 *       cyan) — signature visuelle sans etre intrusif.</li>
 * </ul>
 *
 * <p>Pas d'onglets / channels pour cette version — ça impliquerait un
 * systeme de classification cote serveur (FRIENDS / ALL / FACTION...) qui
 * sera un chantier dedie quand on aura le plugin chat-channels.
 */
public final class ChatPanelStyle {

    private static final int BG_FILL        = 0xB0000000; // noir 69% opacite
    private static final int BORDER         = 0x40FFFFFF; // blanc 25%
    private static final int ACCENT         = 0xFF66DDFF; // cyan reborn

    /** Padding autour du contenu chat pour donner de l'air. */
    public static final int PAD = 3;

    private ChatPanelStyle() {}

    /**
     * Render le panel a la position vanilla du chat (matrices appelantes
     * gèrent translate+scale separement, on dessine en coords brutes).
     */
    public static void drawPanel(DrawContext ctx, int screenW, int screenH) {
        HudElementBounds b = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);
        int x  = b.x() - PAD;
        int y  = b.y() - PAD;
        int x2 = b.right()  + PAD;
        int y2 = b.bottom() + PAD;

        // Fill principal
        ctx.fill(x, y, x2, y2, BG_FILL);

        // Bordure 1px
        ctx.fill(x,      y,      x2,     y + 1,  BORDER);
        ctx.fill(x,      y2 - 1, x2,     y2,     BORDER);
        ctx.fill(x,      y,      x + 1,  y2,     BORDER);
        ctx.fill(x2 - 1, y,      x2,     y2,     BORDER);

        // Accent coin haut-gauche (3 pixels), signature Reborn discrete.
        ctx.fill(x,     y,     x + 3, y + 1, ACCENT);
        ctx.fill(x,     y,     x + 1, y + 3, ACCENT);
    }
}
