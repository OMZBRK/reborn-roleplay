package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins composés Reborn (Phase 2 création de perso). Compose une texture 64×64
 * à partir de couches (base peau + overlays cheveux/yeux/tenues), l'enregistre
 * comme {@link DynamicTexture} dans le TextureManager, et expose son identifiant
 * pour que {@code AbstractClientPlayerSkinMixin} remplace le {@code body()} du
 * {@code PlayerSkin} du joueur → tout le monde (avec le mod) voit le skin composé.
 *
 * <p>Fondation : d'abord on valide l'<b>override du rendu</b> (une composition de
 * test appliquée au joueur local, testable solo). Les vrais assets + la synchro
 * par IDs cosmétiques (via {@code reborn:character}) viennent ensuite.
 */
public final class RebornSkins {

    private RebornSkins() {}

    /** UUID joueur → identifiant de la texture composée enregistrée. */
    private static final Map<UUID, Identifier> overrides = new ConcurrentHashMap<>();

    /** Identifiant de la texture composée pour ce joueur, ou {@code null}. */
    public static Identifier overrideFor(UUID uuid) {
        return overrides.get(uuid);
    }

    public static boolean hasOverride(UUID uuid) {
        return overrides.containsKey(uuid);
    }

    /**
     * Applique une <b>composition de test</b> (base peau + marqueur torse) au
     * joueur — sert à valider le pipeline override en solo. À remplacer par
     * {@code compose(cosmeticIds)} une fois les assets/branchés.
     */
    public static void applyTest(UUID uuid) {
        NativeImage img = new NativeImage(64, 64, false);
        img.fillRect(0, 0, 64, 64, abgr(216, 165, 125, 255));  // teinte peau partout
        img.fillRect(20, 20, 8, 12, abgr(63, 224, 154, 255));  // marqueur torse (teal)
        img.fillRect(8, 8, 8, 8, abgr(120, 78, 52, 255));      // "casquette" tête (test)
        register(uuid, img);
    }

    /** Enregistre l'image composée comme texture dynamique et mémorise l'override. */
    public static void register(UUID uuid, NativeImage composed) {
        Minecraft mc = Minecraft.getInstance();
        Identifier id = Identifier.fromNamespaceAndPath(
            "reborn", "skins/" + uuid.toString().replace("-", ""));
        mc.getTextureManager().register(id, new DynamicTexture(() -> "reborn-skin", composed));
        overrides.put(uuid, id);
    }

    public static void clear(UUID uuid) {
        overrides.remove(uuid);
    }

    public static void clearAll() {
        overrides.clear();
    }

    /** Couleur au format natif de NativeImage (RGBA → int ABGR). */
    private static int abgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
