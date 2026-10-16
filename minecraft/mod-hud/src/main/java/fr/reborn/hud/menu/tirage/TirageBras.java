package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Le <b>bout du bras Minecraft</b> du joueur local qui pince la feuille par le haut, au gros plan du test de la
 * Feuille v3 (16/10 : « assumer le côté Minecraft », à la place des deux doigts dessinés de la v2, même place, même
 * taille). Un court tronçon carré, aux proportions du bras du skin : 4 px de large (3 en « slim »), la face avant (les
 * rangées du bas : la main, manche comprise par la couche de dessus) et, dessous, la rangée de la face de la main
 * (le dessous du poing, ombrée). Mise à l'échelle entière, au plus proche : 1 px de skin = {@link #K} × {@link #K}
 * texels de la feuille, texels carrés, sur sa grille ; le haut sort de l'ombre par paliers (assombri, pas transparent).
 *
 * <p>La texture est celle du skin déjà chargé par le jeu ({@code getSkin().body()}), lue par le rendu de texture
 * ordinaire (un blit par rangée de skin) : aucune copie de pixels, aucune allocation par image. Le skin est relu toutes
 * les 10 ticks (il peut arriver après l'ouverture) ; repli sur le skin par défaut du joueur (Steve / Alex…).
 */
final class TirageBras {

    /** 1 pixel du skin = K × K texels de la feuille (4 px × 5 = 20 texels : la largeur des deux doigts de la v2). */
    static final int K = 5;
    /** Rangées de la face avant montrées (le bas du bras : la main), les deux du haut dans l'ombre. */
    private static final int RANGEES = 6;
    /** Axe du bras dans la case 128×160 de la feuille (texels) et bas de la face avant (le dessous du poing suit). */
    private static final int AXE = 64, BAS_AVANT = 52;

    /** Banc : skin imposé (null = celui du joueur). */
    static Identifier debugSkin;
    static boolean debugSlim;

    private Identifier skin = DefaultPlayerSkin.getDefaultTexture();
    private boolean slim;

    /** Relit le skin du joueur local (à l'ouverture puis toutes les 10 ticks, jamais à l'image). */
    void actualiser() {
        if (debugSkin != null) { skin = debugSkin; slim = debugSlim; return; }
        LocalPlayer p = Minecraft.getInstance().player;
        try {
            PlayerSkin s = p != null ? p.getSkin() : DefaultPlayerSkin.getDefaultSkin();
            if (s == null || s.body() == null) s = p != null ? DefaultPlayerSkin.get(p.getUUID()) : DefaultPlayerSkin.getDefaultSkin();
            skin = s.body().texturePath();
            slim = s.model() == PlayerModelType.SLIM;
        } catch (RuntimeException e) {
            skin = DefaultPlayerSkin.getDefaultTexture();
            slim = false;
        }
    }

    /**
     * Pose le bout du bras sur la feuille dont la case est en (fx, fy) (GUI), à z GUI par texel. {@code glow} : chakra
     * qui passe (0..1), liseré d'1 texel autour de la main.
     */
    void dessiner(GuiGraphicsExtractor ctx, int fx, int fy, int z, float a, float glow, float pouls) {
        if (a <= 0.003f || skin == null) return;
        int w = slim ? 3 : 4, s = K * z;
        int x0 = fx + (AXE - (w * K + 1) / 2) * z;     // sur la grille de la feuille (slim : 15 texels)
        int yBas = fy + BAS_AVANT * z, y0 = yBas - RANGEES * s;
        int larg = w * s;
        if (glow > 0.01f) {
            int col = DaKit.argb(0x7CC4FF, Math.min(1f, (0.35f + 0.25f * pouls) * glow * a));
            int yl = y0 + 2 * s;                           // le liseré ne monte pas dans l'ombre
            ctx.fill(x0 - z, yl, x0, yBas + s, col);
            ctx.fill(x0 + larg, yl, x0 + larg + z, yBas + s, col);
            ctx.fill(x0, yBas + s, x0 + larg, yBas + s + z, col);
        }
        // face avant : les RANGEES du bas du bras droit (skin 44,20 → rangées 4..11), main en bas ; manche par-dessus
        for (int r = 0; r < RANGEES; r++) {
            int col = DaKit.argb(0xFFFFFF, a);
            int y = y0 + r * s, v = 20 + 12 - RANGEES + r;
            ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x0, y, 44, v, larg, s, w, 1, 64, 64, col);
            ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x0, y, 44, v + 16, larg, s, w, 1, 64, 64, col);
            if (r < 2) ctx.fill(x0, y, x0 + larg, y + s, DaKit.argb(0x0A0608, (r == 0 ? 0.55f : 0.25f) * a));
        }
        // dessous du poing : la rangée de la face de la main voisine de la face avant (skin 48,16 / 47,16 en slim),
        // ombrée comme une face de dessous
        int uMain = 44 + w, ombre = DaKit.argb(0x9A9094, a);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x0, yBas, uMain, 16, larg, s, w, 1, 64, 64, ombre);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x0, yBas, uMain, 32, larg, s, w, 1, 64, 64, ombre);
        // arête entre la face avant et le dessous : 1 texel plus sombre (le pavé se lit, pas de dégradé)
        ctx.fill(x0, yBas, x0 + larg, yBas + z, DaKit.argb(0x000000, 0.22f * a));
    }
}
