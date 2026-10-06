package fr.reborn.hud.byakugan;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * État client du Byakugan (alimenté par {@link ByakuganPayload}) — vue subjective « comme dans l'anime » :
 * <ul>
 *   <li>le monde passe en <b>négatif</b> noir et blanc bleuté (filtre plein écran {@code reborn-hud:byakugan},
 *       posé par {@link fr.reborn.hud.effets.FiltresEcran}, prioritaire sur la météo) ;</li>
 *   <li>les entités vivantes à portée deviennent des <b>silhouettes</b> (corps uni, pleine lumière) où brille leur
 *       <b>réseau de chakra</b> ({@link ChakraNetworkLayer}) ; leur <b>aura</b> (contour) reste visible à travers les
 *       murs ;</li>
 *   <li>à l'activation : flash + lignes radiales vers le centre ({@link ByakuganActivation}).</li>
 * </ul>
 * Astuce de couleur : silhouettes, réseau et aura sont dessinés dans des couleurs « inverses » (blanc / orange pur) ;
 * le filtre négatif les transforme en silhouettes sombres et en chakra cyan lumineux. Tout est local au porteur.
 */
public final class ByakuganClient {

    /** Couleur « inverse » du chakra (orange pur) : le filtre la rend cyan lumineux. Sert aussi de couleur d'aura. */
    public static final int CHAKRA_INVERSE = 0xFF7300;
    public static final Identifier SILHOUETTE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/silhouette.png");
    /** Silhouette des joueurs / mannequins : seule la couche de base du skin est remplie ; la couche extérieure
     *  (chapeau, veste, manches, pantalon) reste transparente pour ne pas recouvrir le réseau de chakra. */
    public static final Identifier SILHOUETTE_JOUEUR = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/silhouette_joueur.png");
    public static final Identifier FILTRE = Identifier.fromNamespaceAndPath("reborn-hud", "byakugan");
    private static final int IMAGES = 4;
    private static final Identifier[] RESEAU = new Identifier[IMAGES];

    static {
        for (int i = 0; i < IMAGES; i++) {
            RESEAU[i] = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/byakugan/reseau_" + i + ".png");
        }
    }

    private static volatile boolean active;
    private static volatile float range;
    private static volatile long depuis;

    private ByakuganClient() {}

    public static void update(boolean on, float r) {
        if (on && !active) depuis = System.currentTimeMillis();
        active = on;
        range = Math.max(0f, r);
        fr.reborn.hud.effets.FiltresEcran.tick();          // le filtre négatif s'applique tout de suite
    }

    public static void clear() {
        active = false;
    }

    public static boolean active() { return active; }

    /** Secondes écoulées depuis l'activation (pour l'effet d'activation). */
    public static float depuisActivation() {
        return active ? (System.currentTimeMillis() - depuis) / 1000f : 99f;
    }

    /** Image d'animation du flux de chakra (impulsions qui partent du cœur), 5 images / s. */
    public static Identifier reseau() {
        return RESEAU[(int) ((System.currentTimeMillis() / 200L) % IMAGES)];
    }

    /** L'entité est-elle vue au Byakugan ? (vivante, pas le joueur local, à portée, pas un porte-armure) */
    public static boolean isTarget(LivingEntity e) {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || e instanceof ArmorStand || e.isSpectator() || !e.isAlive()) return false;
        float r = range;
        return e.distanceToSqr(mc.player) <= (double) r * r;
    }
}
