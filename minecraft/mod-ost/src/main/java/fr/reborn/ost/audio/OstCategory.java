package fr.reborn.ost.audio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catégories de pistes OST exposées dans l'UI. L'ordre déclaré ici
 * détermine l'ordre des onglets dans {@code OstScreen}.
 *
 * <p>"FAVORIS" est une catégorie virtuelle : elle n'a pas de dossier
 * dédié sur le filesystem, elle aggrège les tracks marquées favorites
 * dans la config utilisateur (cf {@link fr.reborn.ost.config.OstConfig}).
 *
 * <p>{@link #folderName} est le nom du sous-dossier sur disque
 * (lowercase, sans accent) — donc {@code "mystere"} pas {@code "Mystère"}.
 */
public enum OstCategory {
    APAISANT("apaisant", "Apaisant"),
    COMBAT("combat", "Combat"),
    MISSION("mission", "Mission"),
    MOTIVATION("motivation", "Motivation"),
    MYSTERE("mystere", "Mystère"),
    TRISTE("triste", "Triste"),
    FAVORIS("favoris", "Favoris");

    private final String folderName;
    private final String displayName;

    OstCategory(String folderName, String displayName) {
        this.folderName = folderName;
        this.displayName = displayName;
    }

    public String folderName() { return folderName; }
    public String displayName() { return displayName; }

    /** {@code true} si la catégorie a un dossier physique scanné depuis le FS. */
    public boolean isPhysical() {
        return this != FAVORIS;
    }

    public static Optional<OstCategory> fromFolderName(String name) {
        return Arrays.stream(values()).filter(c -> c.folderName.equalsIgnoreCase(name)).findFirst();
    }
}
