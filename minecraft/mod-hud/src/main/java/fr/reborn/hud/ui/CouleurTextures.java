package fr.reborn.hud.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import fr.reborn.hud.concentration.HaloPlaque;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Le sélecteur de couleur partagé (lot 4 : halo des plaques au Poste de garde, couleur des traces dans les Paramètres) :
 * carré saturation × luminosité, de 100 % à {@link HaloPlaque.Palette#V_MIN} (une lumière sombre ne se verrait pas), recalculé
 * seulement quand la teinte change, et barre de teinte, faite une fois. Filtrés en linéaire.
 */
public final class CouleurTextures {

    public static final int SV_W = 128, SV_H = 64, TEINTE_W = 128;
    public static final Identifier SV = Identifier.fromNamespaceAndPath("reborn-hud", "staff/halo_sv"),
        TEINTE = Identifier.fromNamespaceAndPath("reborn-hud", "staff/halo_teinte");

    private static DynamicTexture svTex;
    private static NativeImage svImg;
    private static float svTeinte = -1;
    private static boolean teintePrete, enPanne;

    private CouleurTextures() {}

    /** Textures prêtes pour la teinte {@code h} (0 à 1) ; faux si elles sont coupées (une erreur, journalisée une fois). */
    public static boolean preparer(float h) {
        if (enPanne) return false;
        try {
            var tm = Minecraft.getInstance().getTextureManager();
            if (!teintePrete) {
                NativeImage im = new NativeImage(TEINTE_W, 1, false);
                for (int i = 0; i < TEINTE_W; i++) im.setPixel(i, 0, SkinSpec.hsvToArgb(i / (float) (TEINTE_W - 1), 1f, 1f));
                tm.register(TEINTE, new Lineaire("reborn-hud teinte du sélecteur", im));
                teintePrete = true;
            }
            if (svTex == null) {
                svImg = new NativeImage(SV_W, SV_H, false);
                svTex = new Lineaire("reborn-hud carré du sélecteur", svImg);
                tm.register(SV, svTex);
            }
            if (Math.abs(svTeinte - h) > 1e-4f) {
                svTeinte = h;
                for (int j = 0; j < SV_H; j++) {
                    float v = 1f - j / (float) (SV_H - 1) * (1f - HaloPlaque.Palette.V_MIN);
                    for (int i = 0; i < SV_W; i++) svImg.setPixel(i, j, SkinSpec.hsvToArgb(h, i / (float) (SV_W - 1), v));
                }
                svTex.upload();
            }
            return true;
        } catch (RuntimeException | LinkageError ex) {
            enPanne = true;
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Sélecteur de couleur coupé ({})", ex.toString());
            return false;
        }
    }

    /** Texture dynamique filtrée en linéaire (carré et teinte étirés sans escaliers). */
    private static final class Lineaire extends DynamicTexture {
        Lineaire(String nom, NativeImage img) {
            super(() -> nom, img);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
