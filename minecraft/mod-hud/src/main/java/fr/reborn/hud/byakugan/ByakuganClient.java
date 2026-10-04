package fr.reborn.hud.byakugan;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * État client du Byakugan (alimenté par {@link ByakuganPayload}). Quand il est actif, les entités vivantes à portée
 * sont vues « aux rayons X » : corps fantôme bleuté (rendu translucide vanilla), réseau de chakra lumineux à
 * l'intérieur ({@link ChakraNetworkLayer}), contour bleu visible à travers les murs (aura), voile discret à l'écran
 * ({@link ByakuganVeil}). Tout est local : personne d'autre ne voit rien.
 */
public final class ByakuganClient {

    /** Couleur du contour (aura) des cibles. */
    public static final int AURA = 0x6EC8FF;
    /** Teinte et opacité du corps fantôme (ARGB) : ~35 %, bleuté. */
    public static final int CORPS_FANTOME = 0x5AA8D8FF;
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
    }

    public static void clear() { active = false; }

    public static boolean active() { return active; }

    /** Fondu d'ouverture (0 → 1 en 0,8 s) pour le voile. */
    public static float ouverture() {
        return active ? Math.min(1f, (System.currentTimeMillis() - depuis) / 800f) : 0f;
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
