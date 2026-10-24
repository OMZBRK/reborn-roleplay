package fr.reborn.hud.boussole;

import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que porte la boussole du HUD en plus des points cardinaux (GAMEPLAN §4, 19/10) — <b>l'API attendue par la carte
 * (§36) et les objets d'orientation (§49)</b> :
 * <ul>
 *   <li>le <b>marqueur personnel</b> : un seul, posé sur la carte du monde (M) — {@link #poserMarqueur} /
 *       {@link #effacerMarqueur}. La carte n'en pose pas encore (§36 à venir) : seul le banc d'essai l'utilise ;</li>
 *   <li>des <b>repères</b> nommés (direction d'un objet d'orientation, cible de mission…) — {@link #poser} /
 *       {@link #retirer}, par clé stable.</li>
 * </ul>
 * Les lieux de la carte proches ({@code reborn:map}) sont lus directement par {@link BoussoleHud}. Tout est local au
 * client et oublié à la déconnexion ; un repère n'apparaît que dans la dimension où il a été posé.
 */
public final class Reperes {

    /** Un point suivi par la boussole. {@code dimension} : identifiant du niveau (« minecraft:overworld »), null = tous. */
    public record Repere(String id, String nom, double x, double z, int couleur, String dimension) {}

    /** Or du marqueur personnel. */
    public static final int OR_MARQUEUR = 0xFFE8C27A;

    private static volatile Repere marqueur;
    private static final Map<String, Repere> AUTRES = new ConcurrentHashMap<>();

    private Reperes() {}

    /** §36 : pose (ou déplace) le marqueur personnel dans la dimension courante. */
    public static void poserMarqueur(double x, double z, String nom) {
        marqueur = new Repere("marqueur", nom == null || nom.isBlank() ? "Marqueur" : nom, x, z, OR_MARQUEUR, dimension());
    }

    public static void effacerMarqueur() { marqueur = null; }

    /** Le marqueur personnel, ou null. */
    public static Repere marqueur() { return marqueur; }

    /** §49 : pose un repère (objet d'orientation…) sous une clé stable, dans la dimension courante. */
    public static void poser(String id, String nom, double x, double z, int couleur) {
        if (id == null) return;
        AUTRES.put(id, new Repere(id, nom, x, z, couleur, dimension()));
    }

    public static void retirer(String id) { if (id != null) AUTRES.remove(id); }

    public static Collection<Repere> autres() { return List.copyOf(AUTRES.values()); }

    /** Déconnexion : tout est oublié. */
    public static void clear() {
        marqueur = null;
        AUTRES.clear();
    }

    /** Identifiant de la dimension courante, ou null hors monde. */
    static String dimension() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.dimension().identifier().toString();
    }
}
