package fr.reborn.hud.menu.connect;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.SakuraParticles;
import fr.reborn.hud.menu.widget.MainMenuRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Rendu de l'écran de connexion Reborn — appelé par
 * {@code ConnectScreenMixin}. Référence visuelle : {@code chargementref.png}.
 *
 * <p>Composition centrée verticalement (~50% du screen) :
 * <ol>
 *   <li>Background quasi-noir avec halo radial discret en haut-droite.</li>
 *   <li>Sakura particles (atténuées pour rester sobre).</li>
 *   <li>Anneau seul (sans logo dedans) — base sombre + arc bleu rotatif
 *       + petite tête blanche.</li>
 *   <li>Titre "CONNEXION À REBORN ROLEPLAY…" Bebas Neue letter-spaced.</li>
 *   <li>Sous-ligne : "Étape … · {status vanilla}" — étape déduite par
 *       keyword matching sur le status.</li>
 *   <li>Barre de progression linéaire indéterminée (segment qui glisse).</li>
 *   <li>Hint italic gris "Cela peut prendre quelques secondes".</li>
 * </ol>
 *
 * <p>Le bouton Annuler vanilla est repositionné à {@code height-48} par
 * {@code ConnectScreenMixin#init} — il n'overlap plus la composition.
 */
public final class ConnectingRenderer {

    private static final long BORN_AT = System.currentTimeMillis();

    private ConnectingRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH, Component status) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        float responsive = MainMenuRenderer.responsiveScale(screenW);
        float t = (System.currentTimeMillis() - BORN_AT) / 1000f;

        // 1. Background quasi-noir uniforme — pas de gradient bandes.
        ctx.fill(0, 0, screenW, screenH, 0xFF050608);

        // 1b. Halo radial doux en haut-droite (effet cinéma vignette).
        int haloCx = (int) (screenW * 0.82f);
        int haloCy = (int) (screenH * 0.18f);
        int haloRBig = Math.round(screenW * 0.30f);
        for (int layer = 0; layer < 5; layer++) {
            float layerT = layer / 5f;
            int r = Math.round(haloRBig * (1f - layerT));
            int alpha = Math.round((1f - layerT) * 18);
            int color = (alpha << 24) | 0x3B5BDB;
            DrawHelpers.disc(ctx, haloCx, haloCy, r, color);
        }

        // 2. Sakura overlay — atténué (alpha de chaque pétale est déjà
        //    aléatoire entre 0.25 et 0.75 dans la classe).
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 3. Anneau spinner — centré ~42% de la hauteur. PAS de sigil
        //    dedans cette fois : le ring SEUL fait le focal point, plus
        //    propre / minimaliste.
        int centerX = screenW / 2;
        int centerY = Math.round(screenH * 0.42f);
        int spinnerR = Math.round(40 * responsive);

        // Base ring très subtile.
        DrawHelpers.ring(ctx, centerX, centerY, spinnerR, 2, 0x33FFFFFF);
        // Arc principal (90° d'arc), rotation 100°/sec.
        float rotation = (t * 100f) % 360f;
        DrawHelpers.dashedRing(ctx, centerX, centerY, spinnerR, 2,
            Colors.ACCENT_HOVER, 100f, 260f, rotation);
        // Tête lumineuse 12° blanc pur.
        DrawHelpers.dashedRing(ctx, centerX, centerY, spinnerR, 2,
            Colors.WHITE_PURE, 12f, 348f, rotation + 88f);

        // 4. Titre "CONNEXION À REBORN ROLEPLAY…" — Bebas Neue display
        //    sous le spinner.
        String titleStr = "CONNEXION À REBORN ROLEPLAY" + animDots(t);
        Component title = RebornFont.display(titleStr);
        float titleScale = 1.1f * responsive;
        int titleW = Math.round(tr.width(title) * titleScale);
        int titleX = (screenW - titleW) / 2;
        int titleY = centerY + spinnerR + Math.round(40 * responsive);
        ctx.pose().pushMatrix();
        ctx.pose().translate(titleX, titleY);
        ctx.pose().scale(titleScale, titleScale);
        ctx.text(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();

        // 5. Sous-ligne "Étape X · Phase" — déduit l'étape via keyword
        //    matching sur le status vanilla.
        String phaseLabel = status != null ? status.getString() : "Connexion en cours";
        String stepLabel = deriveStepLabel(phaseLabel);
        String subLine = stepLabel + " · " + phaseLabel;
        Component sub = RebornFont.body(subLine);
        float subScale = 0.95f * responsive;
        int subW = Math.round(tr.width(sub) * subScale);
        int subX = (screenW - subW) / 2;
        int subY = titleY + Math.round(22 * responsive);
        ctx.pose().pushMatrix();
        ctx.pose().translate(subX, subY);
        ctx.pose().scale(subScale, subScale);
        ctx.text(tr, sub, 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.pose().popMatrix();

        // 6. Barre de progression linéaire indéterminée — un segment
        //    glisse d'aller-retour le long de la barre.
        int barW = Math.round(360 * responsive);
        int barH = Math.max(2, Math.round(3 * responsive));
        int barX = (screenW - barW) / 2;
        int barY = subY + Math.round(28 * responsive);
        // Track gris.
        ctx.fill(barX, barY, barX + barW, barY + barH, 0x33FFFFFF);
        // Segment lumineux ~25% qui glisse en aller-retour.
        float segCycle = (t / 1.6f) % 1.0f; // 1.6s pour un aller, idem retour
        float ping = (segCycle < 0.5f) ? (segCycle * 2f) : (2f - segCycle * 2f);
        int segW = barW / 4;
        int segX = barX + Math.round(ping * (barW - segW));
        // Dégradé au-dessus pour fade aux bords.
        ctx.fillGradient(segX, barY, segX + segW / 3, barY + barH, 0x003B5BDB, 0xFF4C6CE6);
        ctx.fill(segX + segW / 3, barY, segX + 2 * segW / 3, barY + barH, 0xFF4C6CE6);
        ctx.fillGradient(segX + 2 * segW / 3, barY, segX + segW, barY + barH, 0xFF4C6CE6, 0x003B5BDB);

        // 7. Hint italic gris "Cela peut prendre quelques secondes".
        Component hint = RebornFont.body("Cela peut prendre quelques secondes");
        float hintScale = 0.85f * responsive;
        int hintW = Math.round(tr.width(hint) * hintScale);
        int hintX = (screenW - hintW) / 2;
        int hintY = barY + barH + Math.round(14 * responsive);
        ctx.pose().pushMatrix();
        ctx.pose().translate(hintX, hintY);
        ctx.pose().scale(hintScale, hintScale);
        ctx.text(tr, hint, 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
    }

    /**
     * Anime un ellipsis 0..3 dots, change toutes les 400ms.
     */
    private static String animDots(float seconds) {
        int dots = ((int) (seconds * 2.5f)) % 4; // 0, 1, 2, 3
        return ".".repeat(dots);
    }

    /**
     * Mappe le {@code status} vanilla vers un label "Étape X / 4".
     * Vanilla ne nous expose pas un compteur natif, on infère via
     * keywords. C'est volontairement approximatif — l'utilisateur voit
     * une progression cohérente même si pas atomiquement exacte.
     */
    private static String deriveStepLabel(String status) {
        if (status == null) return "Étape 1 / 4";
        String s = status.toLowerCase();
        if (s.contains("authenti") || s.contains("login") || s.contains("connexion en cours")) {
            return "Étape 1 / 4";
        }
        if (s.contains("negotiat") || s.contains("handshake")) {
            return "Étape 2 / 4";
        }
        if (s.contains("encrypt") || s.contains("crypt")) {
            return "Étape 3 / 4";
        }
        if (s.contains("downloading") || s.contains("terrain") || s.contains("chargement")
            || s.contains("joining") || s.contains("loading")) {
            return "Étape 4 / 4";
        }
        return "Étape 1 / 4";
    }
}
