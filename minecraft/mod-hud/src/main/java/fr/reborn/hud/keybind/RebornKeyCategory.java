package fr.reborn.hud.keybind;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Catégorie de touches « Reborn » dans l'écran vanilla Options → Commandes.
 *
 * <p>Avant : toutes les touches Reborn tombaient dans {@code KeyMapping.Category.MISC}
 * (« Divers ») pêle-mêle avec les binds vanilla non catégorisés. Ici on enregistre
 * une catégorie dédiée {@code reborn:controls} → le libellé vient de la clé de langue
 * {@code key.category.reborn.controls} (dérivée par {@code Identifier.toLanguageKey}).
 *
 * <p><b>Partagée entre mods</b> : {@code mod-hud} et {@code mod-ost} veulent la même
 * section. {@code KeyMapping.Category} groupe par égalité de record (donc par
 * {@code id}), et {@link KeyMapping.Category#register} lève une
 * {@link IllegalArgumentException} si l'id est déjà enregistré. On enregistre donc
 * défensivement : le premier mod initialisé enregistre l'id, l'autre réutilise une
 * instance {@code equals} (même id → même section, même libellé). {@code mod-ost}
 * duplique cette logique de son côté (pas de dépendance commune entre les deux jars).
 */
public final class RebornKeyCategory {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("reborn", "controls");

    /** Catégorie partagée « Reborn » (enregistrée une fois, réutilisée sinon). */
    public static final KeyMapping.Category REBORN = resolve();

    private RebornKeyCategory() {}

    private static KeyMapping.Category resolve() {
        try {
            return KeyMapping.Category.register(ID);
        } catch (IllegalArgumentException alreadyRegistered) {
            // Déjà enregistrée (par l'autre mod Reborn) — une instance equal suffit
            // pour le groupement dans l'écran Commandes.
            return new KeyMapping.Category(ID);
        }
    }
}
