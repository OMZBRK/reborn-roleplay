package fr.reborn.hud.menu.connect;

import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Écran de connexion Reborn — appelé par {@code ConnectScreenMixin} (qui annule
 * le rendu vanilla). Style <b>Zenkai</b> minimaliste (cf. {@code chargementigzenkai.png}) :
 * fond noir, logo Reborn centré, une ligne de statut sobre en dessous.
 *
 * <p>Volontairement épuré (plus de spinner / sakura / halo / barre / étapes) :
 * le focal point est le logo + le texte de phase, comme le loading Zenkai.
 */
public final class ConnectingRenderer {

    private static final long BORN_AT = System.currentTimeMillis();

    /** Même logo que le splash (render 3D blocky, fond transparent). */
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    /** Gris doux du texte de statut (façon Zenkai). */
    private static final int STATUS_COLOR = 0xFFB9BDC6;

    private ConnectingRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH, Component status) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;
        float t = (System.currentTimeMillis() - BORN_AT) / 1000f;

        // 1. Fond noir plein.
        ctx.fill(0, 0, screenW, screenH, 0xFF000000);

        // 2. Logo Reborn centré (~40% de la hauteur, ratio conservé).
        int destW = Math.min(Math.round(screenW * 0.32f), 440);
        int destH = Math.round(destW * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (screenW - destW) / 2;
        int logoY = Math.round(screenH * 0.40f) - destH / 2;
        ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0f, 0f,
            destW, destH, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // 3. Ligne de statut sous le logo (phase FR en capitales + points animés).
        //    Même police que le splash/menu = ArcadePix (RebornFont.arcade).
        String label = deriveStatusLabel(status) + animDots(t);
        Component sub = RebornFont.arcade(label);
        float scale = 1.1f;
        int subW = Math.round(tr.width(sub) * scale);
        int subX = (screenW - subW) / 2;
        int subY = Math.round(screenH * 0.66f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(subX, subY);
        ctx.pose().scale(scale, scale);
        ctx.text(tr, sub, 0, 0, STATUS_COLOR, false);
        ctx.pose().popMatrix();
    }

    /** Ellipsis animé 0..3 points (~toutes les 400ms). */
    private static String animDots(float seconds) {
        return ".".repeat(((int) (seconds * 2.5f)) % 4);
    }

    /**
     * Mappe le {@code status} vanilla vers une phase FR en capitales. Vanilla
     * n'expose pas d'étapes atomiques : on infère par mots-clés (approximatif
     * mais cohérent pour l'utilisateur).
     */
    private static String deriveStatusLabel(Component status) {
        if (status == null) return "CONNEXION AU SERVEUR";
        String s = status.getString().toLowerCase();
        if (s.contains("authenti") || s.contains("login")) return "AUTHENTIFICATION";
        if (s.contains("negotiat") || s.contains("handshake")
            || s.contains("encrypt") || s.contains("crypt")) return "NEGOCIATION SECURISEE";
        if (s.contains("downloading") || s.contains("terrain") || s.contains("loading")
            || s.contains("joining") || s.contains("chargement")) return "CHARGEMENT DU MONDE";
        return "CONNEXION AU SERVEUR";
    }
}
